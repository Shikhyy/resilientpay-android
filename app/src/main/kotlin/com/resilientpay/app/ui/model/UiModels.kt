package com.resilientpay.app.ui.model

enum class ConnectivityMode(
    val code: String,
    val title: String,
    val description: String
) {
    C3_ONLINE("C3", "Online", "Full high-speed internet link available"),
    C2_DEGRADED("C2", "Degraded", "High latency / packet loss; store & forward"),
    C1_PROXIMITY("C1", "Proximity", "Air-gapped; NFC/BLE/QR proximity link only"),
    C0_OFFLINE("C0", "Offline", "Complete air-gap; local ledger queuing only"),
}

enum class TransportSelection(val label: String, val protocolName: String) {
    NFC("NFC Proximity", "NFC"),
    BLE("Bluetooth LE", "BLE"),
    QR("Dynamic QR Code", "QR"),
    SMS("Store & Forward SMS", "SMS"),
    INTERNET("Direct Cloud (HTTPS)", "INTERNET"),
}

data class LocalPaymentRecord(
    val txId: String,
    val counter: Long,
    val amountMinor: Long,
    val counterpartyId: String,
    val transport: String,
    val state: String,
    val timestampUnix: Long,
    val receiptHash: String,
    val conflictReason: String? = null,
)

fun formatPaise(amountMinor: Long): String {
    val rupees = amountMinor / 100
    val paise = amountMinor % 100
    return "₹$rupees.%02d".format(paise)
}

fun formatPaiseWithMinor(amountMinor: Long): String {
    val formattedRupees = formatPaise(amountMinor)
    return "$formattedRupees ($amountMinor paise)"
}

data class AppSettings(
    val merchantId: String = "00000000-0000-0000-0000-000000000004",
    val payerCredentialId: String = "00000000-0000-0000-0000-000000000002",
    val payerKeyId: String = "00000000-0000-0000-0000-000000000003",
    val maxPerTxLimitMinor: Long = 50000L, // ₹500.00
    val maxCounterCeiling: Long = 1000L,
    val backendUrl: String = "http://10.0.2.2:8080/v1/reconcile",
    val smsGatewayNumber: String = "+919876543210"
)

fun computeSha256Hex(data: ByteArray): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(data)
    return digest.joinToString("") { "%02x".format(it) }
}

data class ParsedEnvelope(
    val txId: String,
    val credentialId: String,
    val amountMinor: Long
)

fun parseEnvelopePayload(envelopeBytes: ByteArray): ParsedEnvelope? {
    return try {
        val jsonStr = String(envelopeBytes, Charsets.UTF_8)
        val cborPrefix = "\"core_cbor_hex\":\""
        val idx = jsonStr.indexOf(cborPrefix)
        if (idx == -1) return null
        val start = idx + cborPrefix.length
        val end = jsonStr.indexOf('"', start)
        if (end == -1) return null
        val cborHex = jsonStr.substring(start, end)
        val len = cborHex.length
        val cbor = ByteArray(len / 2) { i ->
            ((Character.digit(cborHex[i * 2], 16) shl 4) + Character.digit(cborHex[i * 2 + 1], 16)).toByte()
        }
        if (cbor.size < 71) return null

        val txIdBb = java.nio.ByteBuffer.wrap(cbor, 2, 16)
        val txId = java.util.UUID(txIdBb.long, txIdBb.long).toString()

        val credBb = java.nio.ByteBuffer.wrap(cbor, 19, 16)
        val credId = java.util.UUID(credBb.long, credBb.long).toString()

        val offset = 70
        val header = cbor[offset].toInt() and 0xFF
        val amountMinor: Long = when {
            header < 24 -> header.toLong()
            header == 24 -> (cbor[offset + 1].toInt() and 0xFF).toLong()
            header == 25 -> (((cbor[offset + 1].toInt() and 0xFF) shl 8) or (cbor[offset + 2].toInt() and 0xFF)).toLong()
            header == 26 -> (
                ((cbor[offset + 1].toLong() and 0xFF) shl 24) or
                ((cbor[offset + 2].toLong() and 0xFF) shl 16) or
                ((cbor[offset + 3].toLong() and 0xFF) shl 8) or
                (cbor[offset + 4].toLong() and 0xFF)
            )
            header == 27 -> {
                var v = 0L
                for (i in 1..8) {
                    v = (v shl 8) or (cbor[offset + i].toLong() and 0xFF)
                }
                v
            }
            else -> 15000L
        }
        ParsedEnvelope(txId, credId, amountMinor)
    } catch (_: Exception) {
        null
    }
}

