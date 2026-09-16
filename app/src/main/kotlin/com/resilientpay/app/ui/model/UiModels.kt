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
