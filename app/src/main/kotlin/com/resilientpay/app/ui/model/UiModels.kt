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
