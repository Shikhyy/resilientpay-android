package com.resilientpay.sdk

import uniffi.resilientpay_core.ResilientPayClient as RustClient

/**
 * Result of a transport transmission.
 */
sealed class TransportResult {
    data class Success(val receiptBytes: ByteArray) : TransportResult()
    data class Error(val reason: String) : TransportResult()
}

/**
 * TransportAdapter moves protocol objects and does NOT decide payment validity.
 * Transport logic is completely isolated from the core SDK and state machine.
 */
interface TransportAdapter {
    fun isAvailable(): Boolean = true
    fun send(txBytes: ByteArray): TransportResult
}

class InternetAdapter(
    val endpointUrl: String = "https://api.resilientpay.internal/v1/reconcile",
    private val httpDispatcher: ((url: String, payload: ByteArray) -> Pair<Int, ByteArray>)? = null
) : TransportAdapter {
    override fun isAvailable(): Boolean = true

    override fun send(txBytes: ByteArray): TransportResult {
        if (txBytes.isEmpty()) {
            return TransportResult.Error("Cannot transmit empty envelope")
        }
        return try {
            if (httpDispatcher != null) {
                val (statusCode, responseBody) = httpDispatcher.invoke(endpointUrl, txBytes)
                if (statusCode in 200..299) {
                    TransportResult.Success(responseBody)
                } else {
                    TransportResult.Error("HTTP error status $statusCode: ${responseBody.decodeToString()}")
                }
            } else {
                TransportResult.Success("HTTPS_ACK:${txBytes.size}".toByteArray())
            }
        } catch (e: Exception) {
            TransportResult.Error("Internet transport dispatch failed: ${e.message}")
        }
    }
}

class NfcAdapter(
    private val apduTransceiver: ((apduCommand: ByteArray) -> ByteArray)? = null
) : TransportAdapter {
    companion object {
        val AID_RESILIENTPAY = byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
    }

    override fun send(txBytes: ByteArray): TransportResult {
        if (txBytes.isEmpty()) {
            return TransportResult.Error("NFC payload is empty")
        }
        return try {
            if (apduTransceiver != null) {
                val selectApdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID_RESILIENTPAY.size.toByte()) + AID_RESILIENTPAY
                val selectResp = apduTransceiver.invoke(selectApdu)
                if (selectResp.size < 2 || selectResp[selectResp.size - 2] != 0x90.toByte() || selectResp[selectResp.size - 1] != 0x00.toByte()) {
                    return TransportResult.Error("NFC AID selection failed")
                }

                val writeApdu = byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, txBytes.size.toByte()) + txBytes
                val writeResp = apduTransceiver.invoke(writeApdu)
                TransportResult.Success(writeResp)
            } else {
                TransportResult.Success("NFC_PROXIMITY_DELIVERED:${txBytes.size}".toByteArray())
            }
        } catch (e: Exception) {
            TransportResult.Error("NFC APDU transmission failure: ${e.message}")
        }
    }
}

class BleAdapter(
    val serviceUuid: String = "0000FE25-0000-1000-8000-00805F9B34FB",
    val characteristicUuid: String = "0000FE26-0000-1000-8000-00805F9B34FB",
    private val gattTransceiver: ((serviceUuid: String, charUuid: String, payload: ByteArray) -> ByteArray?)? = null
) : TransportAdapter {
    override fun send(txBytes: ByteArray): TransportResult {
        if (txBytes.isEmpty()) {
            return TransportResult.Error("BLE payload is empty")
        }
        return try {
            if (gattTransceiver != null) {
                val receipt = gattTransceiver.invoke(serviceUuid, characteristicUuid, txBytes)
                if (receipt != null) {
                    TransportResult.Success(receipt)
                } else {
                    TransportResult.Error("BLE GATT characteristic write rejected by peer")
                }
            } else {
                TransportResult.Success("BLE_GATT_DELIVERED:${txBytes.size}".toByteArray())
            }
        } catch (e: Exception) {
            TransportResult.Error("BLE GATT error: ${e.message}")
        }
    }
}

class QrAdapter : TransportAdapter {
    override fun send(txBytes: ByteArray): TransportResult {
        if (txBytes.isEmpty()) {
            return TransportResult.Error("QR payload is empty")
        }
        val encoded = encodeToQrString(txBytes)
        return TransportResult.Success(encoded.toByteArray())
    }

    fun encodeToQrString(txBytes: ByteArray): String {
        val b64 = java.util.Base64.getEncoder().encodeToString(txBytes)
        return "RESPAY/QR/v1:$b64"
    }

    fun decodeFromQrString(qrText: String): ByteArray? {
        val prefix = "RESPAY/QR/v1:"
        if (!qrText.startsWith(prefix)) return null
        val b64 = qrText.removePrefix(prefix)
        return try {
            java.util.Base64.getDecoder().decode(b64)
        } catch (e: Exception) {
            null
        }
    }
}

class SmsAdapter(
    val gatewayNumber: String = "+910000000000",
    private val smsDispatcher: ((phone: String, text: String) -> Boolean)? = null
) : TransportAdapter {
    override fun send(txBytes: ByteArray): TransportResult {
        if (txBytes.size != 170) {
            return TransportResult.Error("Invalid envelope size for SMS transport: expected 170 bytes, got ${txBytes.size}")
        }

        val cborBytes = txBytes.copyOfRange(0, 106)
        val sigBytes = txBytes.copyOfRange(106, 170)
        val txId = "sms-tx-${System.currentTimeMillis()}"

        val segments = SmsPayloadCodec.encode(cborBytes, sigBytes, txId)
        if (segments.size != 2) {
            return TransportResult.Error("SMS fragmentation failed to produce exactly 2 segments")
        }

        return try {
            if (smsDispatcher != null) {
                for (segment in segments) {
                    val sent = smsDispatcher.invoke(gatewayNumber, segment)
                    if (!sent) {
                        return TransportResult.Error("Failed to dispatch SMS segment to $gatewayNumber")
                    }
                }
                TransportResult.Success("SMS_MULTIPART_QUEUED:${segments.size}".toByteArray())
            } else {
                TransportResult.Success("SMS_MULTIPART_QUEUED:${segments.size}".toByteArray())
            }
        } catch (e: Exception) {
            TransportResult.Error("SMS dispatch exception: ${e.message}")
        }
    }
}

/**
 * The core ResilientPay Android protocol entrypoint.
 * Manages the FFI boundary (Rust core) and handles the transport bridging.
 */
class ResilientPayClient(
    private val transport: TransportAdapter,
    keyManager: HardwareKeyManager = HardwareKeyManager()
) : AutoCloseable {
    
    // Persistent Native Client retaining the Kotlin callback over JNI
    private val nativeClient = RustClient(keyManager)

    /**
     * Produces a signed CBOR payment envelope via the Rust crypto core.
     */
    fun createTransactionBytes(
        txIdStr: String,
        credentialIdStr: String,
        payerKeyIdStr: String,
        merchantIdStr: String,
        amountMinor: Long,
        counter: Long,
        nonceBytes: ByteArray,
        createdAtUnix: Long,
        expiresAtUnix: Long
    ): ByteArray {
        return nativeClient.createTransaction(
            txIdStr = txIdStr,
            credentialIdStr = credentialIdStr,
            payerKeyIdStr = payerKeyIdStr,
            merchantIdStr = merchantIdStr,
            amountMinor = amountMinor.toULong(),
            counter = counter.toULong(),
            nonceBytes = nonceBytes,
            createdAtUnix = createdAtUnix,
            expiresAtUnix = expiresAtUnix
        )
    }

    /**
     * Orchestrates a full transaction:
     * 1. Calls Rust via FFI to canonicalize bytes
     * 2. Rust calls back into Android Keystore to sign
     * 3. Rust returns the signed CBOR payload
     * 4. Android transmits the bytes via the injected transport
     */
    fun createAndSubmitTransaction(
        txIdStr: String,
        credentialIdStr: String,
        payerKeyIdStr: String,
        merchantIdStr: String,
        amountMinor: Long,
        counter: Long,
        nonceBytes: ByteArray,
        createdAtUnix: Long,
        expiresAtUnix: Long
    ): TransportResult {
        return try {
            val signedEnvelopeBytes = createTransactionBytes(
                txIdStr = txIdStr,
                credentialIdStr = credentialIdStr,
                payerKeyIdStr = payerKeyIdStr,
                merchantIdStr = merchantIdStr,
                amountMinor = amountMinor,
                counter = counter,
                nonceBytes = nonceBytes,
                createdAtUnix = createdAtUnix,
                expiresAtUnix = expiresAtUnix
            )
            
            // Send exactly what Rust produced
            transport.send(signedEnvelopeBytes)
        } catch (e: Exception) {
            TransportResult.Error("FFI or Transaction error: ${e.message}")
        }
    }

    /**
     * Verifies a received payment envelope payload against the payer's public key.
     * Used by the merchant to assert cryptographic validity before accepting offline funds.
     */
    fun verifyTransaction(
        envelopeJsonBytes: ByteArray,
        payerPublicKey: ByteArray
    ): Boolean {
        return try {
            nativeClient.verifyTransaction(envelopeJsonBytes, payerPublicKey)
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        nativeClient.close()
    }
}
