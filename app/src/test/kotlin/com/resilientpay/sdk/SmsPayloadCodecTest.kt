package com.resilientpay.sdk

import org.junit.Assert.*
import org.junit.Test

class SmsPayloadCodecTest {

    private val EXPECTED_CBOR_HEX = "8d015000000000000000000000000000000001500000000000000000000000000000000250000000000000000000000000000000035000000000000000000000000000000004189663494e520150010101010101010101010101010101011a6553f1001a6553ff10f6f6"
    private val TEST_TX_ID = "00000000-0000-0000-0000-000000000001"

    private fun decodeHex(hexString: String): ByteArray {
        val result = ByteArray(hexString.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = Integer.parseInt(hexString.substring(index, index + 2), 16)
            result[i] = j.toByte()
        }
        return result
    }

    @Test
    fun testEncode_FitsWithinGSMCharLimit() {
        val cborBytes = decodeHex(EXPECTED_CBOR_HEX)
        val sigBytes = ByteArray(64) { 0x42.toByte() }

        val parts = SmsPayloadCodec.encode(cborBytes, sigBytes, TEST_TX_ID)
        assertEquals(2, parts.size)

        for (part in parts) {
            assertTrue("Each SMS segment must be <= 160 chars, got ${part.length}", part.length <= 160)
            assertTrue("Segment must have standard prefix", part.startsWith("RESPAY/"))
        }
    }

    @Test
    fun testRoundTrip_InOrderAndOutOfOrder() {
        val cborBytes = decodeHex(EXPECTED_CBOR_HEX)
        val sigBytes = ByteArray(64) { it.toByte() }

        val parts = SmsPayloadCodec.encode(cborBytes, sigBytes, TEST_TX_ID)

        // 1. In-order reassembly
        val decoded1 = SmsPayloadCodec.decode(parts)
        assertNotNull(decoded1)
        assertArrayEquals(cborBytes, decoded1!!.first)
        assertArrayEquals(sigBytes, decoded1.second)

        // 2. Out-of-order reassembly
        val decoded2 = SmsPayloadCodec.decode(listOf(parts[1], parts[0]))
        assertNotNull(decoded2)
        assertArrayEquals(cborBytes, decoded2!!.first)
        assertArrayEquals(sigBytes, decoded2.second)
    }

    @Test
    fun testDecode_MismatchedPrefixOrMalformedFails() {
        val cborBytes = decodeHex(EXPECTED_CBOR_HEX)
        val sigBytes = ByteArray(64)

        val parts1 = SmsPayloadCodec.encode(cborBytes, sigBytes, "11111111-0000-0000-0000-000000000001")
        val parts2 = SmsPayloadCodec.encode(cborBytes, sigBytes, "22222222-0000-0000-0000-000000000002")

        // Mismatched parts from different transactions
        val mismatched = listOf(parts1[0], parts2[1])
        assertNull(SmsPayloadCodec.decode(mismatched))

        // Malformed string
        assertNull(SmsPayloadCodec.decode(listOf("GARBAGE", "MORE_GARBAGE")))
    }
}
