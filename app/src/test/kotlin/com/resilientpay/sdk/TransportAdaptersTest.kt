package com.resilientpay.sdk

import org.junit.Assert.*
import org.junit.Test

class TransportAdaptersTest {

    private val valid170Bytes = ByteArray(170) { (it % 256).toByte() }

    @Test
    fun testInternetAdapter_SuccessAndFailure() {
        // Success case with dispatcher
        val successAdapter = InternetAdapter(
            endpointUrl = "https://api.resilientpay.internal/v1/reconcile",
            httpDispatcher = { _, _ -> Pair(200, "RECONCILED_OK".toByteArray()) }
        )
        assertTrue(successAdapter.isAvailable())
        val res1 = successAdapter.send(valid170Bytes)
        assertTrue(res1 is TransportResult.Success)
        assertEquals("RECONCILED_OK", (res1 as TransportResult.Success).receiptBytes.decodeToString())

        // Failure case (HTTP 409 conflict)
        val conflictAdapter = InternetAdapter(
            endpointUrl = "https://api.resilientpay.internal/v1/reconcile",
            httpDispatcher = { _, _ -> Pair(409, "DOUBLE_SPEND_CONFLICT".toByteArray()) }
        )
        val res2 = conflictAdapter.send(valid170Bytes)
        assertTrue(res2 is TransportResult.Error)
        assertTrue((res2 as TransportResult.Error).reason.contains("HTTP error status 409"))

        // Empty payload failure
        val emptyRes = successAdapter.send(ByteArray(0))
        assertTrue(emptyRes is TransportResult.Error)
    }

    @Test
    fun testQrAdapter_EncodeDecodeRoundTrip() {
        val adapter = QrAdapter()
        assertTrue(adapter.isAvailable())

        val res = adapter.send(valid170Bytes)
        assertTrue(res is TransportResult.Success)

        val qrString = (res as TransportResult.Success).receiptBytes.decodeToString()
        assertTrue(qrString.startsWith("RESPAY/QR/v1:"))

        val decoded = adapter.decodeFromQrString(qrString)
        assertNotNull(decoded)
        assertArrayEquals(valid170Bytes, decoded)

        // Corrupted or invalid QR payload
        assertNull(adapter.decodeFromQrString("INVALID_QR_PAYLOAD"))
        assertNull(adapter.decodeFromQrString("RESPAY/QR/v1:???malformed-base64???"))
    }

    @Test
    fun testNfcAdapter_ApduExchange() {
        val nfcAdapter = NfcAdapter(
            apduTransceiver = { cmd ->
                if (cmd.size >= 5 && cmd[1] == 0xA4.toByte()) {
                    // SELECT AID response: 0x9000
                    NfcAdapter.SW_OK
                } else {
                    // ACK response: 0x9000
                    byteArrayOf(0x01) + NfcAdapter.SW_OK
                }
            }
        )
        val res = nfcAdapter.send(valid170Bytes)
        assertTrue(res is TransportResult.Success)

        // NFC failure when AID select is rejected
        val rejectingAdapter = NfcAdapter(
            apduTransceiver = { byteArrayOf(0x6A.toByte(), 0x82.toByte()) } // File not found
        )
        val failRes = rejectingAdapter.send(valid170Bytes)
        assertTrue(failRes is TransportResult.Error)
    }

    @Test
    fun testBleAdapter_GattExchange() {
        val bleAdapter = BleAdapter(
            gattTransceiver = { service, char, payload ->
                assertEquals("0000FE25-0000-1000-8000-00805F9B34FB", service)
                assertEquals("0000FE26-0000-1000-8000-00805F9B34FB", char)
                assertEquals(170, payload.size)
                "BLE_ACK_OK".toByteArray()
            }
        )
        val res = bleAdapter.send(valid170Bytes)
        assertTrue(res is TransportResult.Success)
        assertEquals("BLE_ACK_OK", (res as TransportResult.Success).receiptBytes.decodeToString())

        // BLE failure when peer rejects
        val rejectingAdapter = BleAdapter(gattTransceiver = { _, _, _ -> null })
        val failRes = rejectingAdapter.send(valid170Bytes)
        assertTrue(failRes is TransportResult.Error)
    }

    @Test
    fun testSmsAdapter_EnforcesSizeAndDispatchesSegments() {
        val dispatchedSegments = mutableListOf<String>()
        val smsAdapter = SmsAdapter(
            gatewayNumber = "+919876543210",
            smsDispatcher = { phone, text ->
                assertEquals("+919876543210", phone)
                dispatchedSegments.add(text)
                true
            }
        )

        // Valid 170 bytes (106 CBOR + 64 Ed25519)
        val res = smsAdapter.send(valid170Bytes)
        assertTrue(res is TransportResult.Success)
        assertEquals(2, dispatchedSegments.size)
        assertTrue(dispatchedSegments[0].startsWith("RESPAY/1/2:"))
        assertTrue(dispatchedSegments[1].startsWith("RESPAY/2/2:"))

        // Invalid byte size rejection
        val invalidRes = smsAdapter.send(ByteArray(100))
        assertTrue(invalidRes is TransportResult.Error)
        assertTrue((invalidRes as TransportResult.Error).reason.contains("expected 170 bytes"))
    }
}
