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
    fun send(txBytes: ByteArray): TransportResult
}

// Stubs for adapters...
class InternetAdapter : TransportAdapter {
    override fun send(txBytes: ByteArray): TransportResult = TransportResult.Error("not implemented")
}

class NfcAdapter : TransportAdapter {
    override fun send(txBytes: ByteArray): TransportResult = TransportResult.Error("not implemented")
}

class BleAdapter : TransportAdapter {
    override fun send(txBytes: ByteArray): TransportResult = TransportResult.Error("not implemented")
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
            val signedEnvelopeBytes = nativeClient.createTransaction(
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
            
            // Send exactly what Rust produced
            transport.send(signedEnvelopeBytes)
        } catch (e: Exception) {
            TransportResult.Error("FFI or Transaction error: ${e.message}")
        }
    }

    override fun close() {
        nativeClient.close()
    }
}
