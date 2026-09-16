package com.resilientpay.sdk

import java.util.Base64

/**
 * Handles multipart SMS segmentation and reassembly for store-and-forward resilience.
 *
 * Per SMS_PROTOCOL.md and ADR-006:
 *  - Transports carry 106-byte canonical CBOR envelope + 64-byte Ed25519 signature (170 bytes).
 *  - Single GSM SMS has a 140-octet payload ceiling.
 *  - Segments are prefixed: "RESPAY/<part>/<total>:<tx_prefix>:<base64_payload>"
 *  - Part 1: first 85 bytes of (cbor || sig) -> Base64 is 116 chars -> total segment string 136 chars.
 *  - Part 2: remaining 85 bytes of (cbor || sig) -> Base64 is 116 chars -> total segment string 136 chars.
 *  - Fits comfortably within standard 160 GSM 7-bit character limits without carrier clipping.
 */
object SmsPayloadCodec {

    fun encode(envelopeBytes: ByteArray, signatureBytes: ByteArray, txId: String): List<String> {
        require(signatureBytes.size == 64) { "Signature must be 64 bytes" }
        val combined = envelopeBytes + signatureBytes
        val half = combined.size / 2
        val part1Bytes = combined.copyOfRange(0, half)
        val part2Bytes = combined.copyOfRange(half, combined.size)

        val prefix = txId.replace("-", "").take(8).lowercase()
        val encoder = Base64.getEncoder()

        val part1Str = "RESPAY/1/2:$prefix:${encoder.encodeToString(part1Bytes)}"
        val part2Str = "RESPAY/2/2:$prefix:${encoder.encodeToString(part2Bytes)}"

        return listOf(part1Str, part2Str)
    }

    fun decode(parts: List<String>): Pair<ByteArray, ByteArray>? {
        if (parts.size < 2) return null

        val parsed = mutableMapOf<Int, Pair<String, String>>()
        for (part in parts) {
            val trimmed = part.trim()
            if (!trimmed.startsWith("RESPAY/")) return null
            val tokens = trimmed.split(":", limit = 3)
            if (tokens.size != 3) return null

            val header = tokens[0].removePrefix("RESPAY/").split("/")
            if (header.size != 2) return null
            val partNum = header[0].toIntOrNull() ?: return null
            val totalNum = header[1].toIntOrNull() ?: return null
            if (totalNum != 2 || partNum < 1 || partNum > 2) return null

            val prefix = tokens[1]
            val payload = tokens[2]
            parsed[partNum] = Pair(prefix, payload)
        }

        if (parsed.size != 2) return null
        val p1 = parsed[1] ?: return null
        val p2 = parsed[2] ?: return null
        if (p1.first != p2.first) return null // prefix mismatch

        val decoder = Base64.getDecoder()
        val b1 = try { decoder.decode(p1.second) } catch (e: Exception) { return null }
        val b2 = try { decoder.decode(p2.second) } catch (e: Exception) { return null }

        val combined = b1 + b2
        if (combined.size < 106 + 64) return null

        val cborBytes = combined.copyOfRange(0, combined.size - 64)
        val sigBytes = combined.copyOfRange(combined.size - 64, combined.size)

        return Pair(cborBytes, sigBytes)
    }
}
