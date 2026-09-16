package com.resilientpay.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resilientpay.app.ui.model.*
import com.resilientpay.app.ui.theme.*

private val ResilientPayZeroShapes = Shapes(
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp)
)

@Composable
fun MerchantHomeScreen(
    merchantId: String,
    connectivity: ConnectivityMode,
    transactions: List<LocalPaymentRecord>,
    onReceivePayment: () -> Unit,
    onSyncWithBackend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedConflictTx by remember { mutableStateOf<LocalPaymentRecord?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientPaper)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Connectivity Status Bar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (connectivity) {
                                        ConnectivityMode.C3_ONLINE -> ResilientTeal
                                        ConnectivityMode.C2_DEGRADED -> ResilientBlue
                                        ConnectivityMode.C1_PROXIMITY -> ResilientOrange
                                        ConnectivityMode.C0_OFFLINE -> ResilientRed
                                    }
                                )
                        )
                        Text(
                            text = "${connectivity.code} · ${connectivity.title}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ResilientInk
                        )
                    }
                    Text(
                        text = connectivity.description,
                        fontSize = 11.sp,
                        color = ResilientSlate
                    )
                }
            }
        }

        // 2. Merchant Overview Card
        item {
            val totalReceivedMinor = transactions
                .filter { it.state != "CONFLICT" }
                .sumOf { it.amountMinor }
            val pendingSyncCount = transactions.count { 
                it.state == "PAYMENT RECEIVED LOCALLY" || it.state == "SYNC_PENDING" 
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "MERCHANT TERMINAL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientSlate,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${merchantId.take(18)}...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientInk
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = ResilientRule.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TOTAL COLLECTED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ResilientSlate
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPaise(totalReceivedMinor),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientInk
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SYNC STATUS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ResilientSlate
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (pendingSyncCount > 0) {
                                Text(
                                    text = "$pendingSyncCount PENDING",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResilientOrange
                                )
                            } else {
                                Text(
                                    text = "ALL SYNCED",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResilientTeal
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Action Buttons: Receive & Sync
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onReceivePayment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResilientTeal,
                        contentColor = ResilientPaper
                    ),
                    shape = ResilientPayZeroShapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "RECEIVE PAYMENT (NFC / QR / BLE)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }

                val pendingCount = transactions.count { 
                    it.state == "PAYMENT RECEIVED LOCALLY" || it.state == "SYNC_PENDING" 
                }
                OutlinedButton(
                    onClick = onSyncWithBackend,
                    shape = ResilientPayZeroShapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, ResilientInk)
                ) {
                    Text(
                        text = if (pendingCount > 0) {
                            "SYNC LEDGER TO BACKEND ($pendingCount QUEUED)"
                        } else {
                            "RECONCILE LEDGER WITH BACKEND"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ResilientInk
                    )
                }
            }
        }

        // 4. Pending Synchronization Warning Note
        val pendingCount = transactions.count { 
            it.state == "PAYMENT RECEIVED LOCALLY" || it.state == "SYNC_PENDING" 
        }
        if (pendingCount > 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ResilientOrange)
                        .background(ResilientOrange.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "OFFLINE ACCEPTANCE NOTICE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ResilientOrange
                        )
                        Text(
                            text = "Local offline acceptance and final reconciliation are distinct protocol states. $pendingCount transactions await cloud settlement.",
                            fontSize = 11.sp,
                            color = ResilientInk
                        )
                    }
                }
            }
        }

        // 5. Transaction History Header
        item {
            Text(
                text = "MERCHANT TRANSACTION LEDGER",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientSlate,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 6. Transactions List
        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ResilientRule)
                        .background(ResilientSurface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No received payments in ledger",
                        fontSize = 13.sp,
                        color = ResilientSlate,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(transactions) { tx ->
                val isConflict = tx.state == "CONFLICT"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp, 
                            if (isConflict) ResilientRed else ResilientRule
                        )
                        .background(
                            if (isConflict) ResilientRed.copy(alpha = 0.04f) else ResilientSurface
                        )
                        .clickable {
                            if (isConflict) {
                                selectedConflictTx = tx
                            }
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "From: ${tx.counterpartyId.take(16)}...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientInk
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ctr: #${tx.counter} · Via: ${tx.transport}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ResilientSlate
                            )
                            if (isConflict) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap to view conflict detail & action",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = ResilientRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+${formatPaise(tx.amountMinor)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConflict) ResilientRed else ResilientTeal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tx.state,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (tx.state) {
                                    "SETTLED", "RECONCILED" -> ResilientTeal
                                    "CONFLICT" -> ResilientRed
                                    else -> ResilientOrange
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Conflict Dialog
    selectedConflictTx?.let { conflictTx ->
        ConflictDetailDialog(
            record = conflictTx,
            onDismiss = { selectedConflictTx = null }
        )
    }
}

@Composable
fun MerchantReceiveScreen(
    merchantId: String,
    onCancel: () -> Unit,
    onPaymentReceivedLocally: (amountMinor: Long, payerId: String, transport: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var amountInput by remember { mutableStateOf("150.00") }
    var selectedTransport by remember { mutableStateOf(TransportSelection.NFC) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientPaper)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(
                    text = "GENERATE PAYMENT REQUEST",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientSlate,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "RECIPIENT: ${merchantId.take(18)}...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = ResilientSlate
                )
            }

            // Amount Input Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "REQUEST AMOUNT (RUPEES)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientSlate
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it.filter { char -> char.isDigit() || char == '.' }
                            errorMessage = null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = ResilientInk
                        ),
                        placeholder = { Text("0.00", fontSize = 28.sp, color = ResilientSlate) },
                        prefix = { Text("₹", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ResilientInk) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ResilientInk,
                            unfocusedBorderColor = ResilientRule
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Transport Selection
            Text(
                text = "ACCEPTANCE CHANNEL",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientSlate,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(TransportSelection.NFC, TransportSelection.QR, TransportSelection.BLE).forEach { tr ->
                    val isSelected = selectedTransport == tr
                    Button(
                        onClick = { selectedTransport = tr },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ResilientInk else ResilientSurface,
                            contentColor = if (isSelected) ResilientPaper else ResilientInk
                        ),
                        shape = ResilientPayZeroShapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, if (isSelected) ResilientInk else ResilientRule)
                    ) {
                        Text(
                            text = tr.protocolName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Representation Card (NFC HCE, Dynamic QR, or BLE GATT)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTransport) {
                    TransportSelection.NFC -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .border(2.dp, ResilientTeal)
                                    .background(ResilientPaper),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("NFC", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ResilientTeal)
                            }
                            Text(
                                text = "NFC HCE READER READY",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ResilientInk
                            )
                            Text(
                                text = "Hold customer device against phone to transfer CBOR envelope.",
                                fontSize = 11.sp,
                                color = ResilientSlate,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    TransportSelection.QR -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // High-contrast QR representation block
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .background(ResilientInk)
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ResilientPaper)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "QR CODE\n[106-byte CBOR]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = ResilientInk
                                    )
                                }
                            }
                            Text(
                                text = "SCAN DYNAMIC QR CODE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ResilientInk
                            )
                        }
                    }
                    TransportSelection.BLE -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .border(2.dp, ResilientBlue)
                                    .background(ResilientPaper),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("BLE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ResilientBlue)
                            }
                            Text(
                                text = "BLE GATT SERVICE BROADCASTING",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ResilientInk
                            )
                            Text(
                                text = "UUID: 0000FE25-0000-1000-8000-00805F9B34FB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ResilientSlate
                            )
                        }
                    }
                    else -> {}
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = ResilientRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Interactive Simulation Button for Prototype Verification
            Button(
                onClick = {
                    val enteredDouble = amountInput.toDoubleOrNull() ?: 0.0
                    val amountMinor = (enteredDouble * 100).toLong()

                    if (amountMinor <= 0) {
                        errorMessage = "Enter a valid payment amount"
                    } else {
                        // Simulate receiving transaction from Payer
                        onPaymentReceivedLocally(
                            amountMinor,
                            "00000000-0000-0000-0000-000000000001",
                            selectedTransport.protocolName
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientInk,
                    contentColor = ResilientPaper
                ),
                shape = ResilientPayZeroShapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "SIMULATE INCOMING PROXIMITY PAYMENT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onCancel,
                shape = ResilientPayZeroShapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, ResilientRule)
            ) {
                Text(
                    text = "CANCEL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ResilientInk
                )
            }
        }
    }
}

@Composable
fun MerchantPaymentReceivedDialog(
    record: LocalPaymentRecord,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PAYMENT RECEIVED LOCALLY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientTeal
                )
                Text(
                    text = "RECONCILIATION PENDING",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientOrange
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Cryptographic envelope verified via Ed25519 canonical signature. Stored securely in merchant offline ledger.",
                    fontSize = 12.sp,
                    color = ResilientSlate
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ResilientOrange.copy(alpha = 0.08f))
                        .border(1.dp, ResilientOrange)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "CRITICAL: Local acceptance is NOT final cloud settlement. Submit ledger to bank clearing when internet resumes.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ResilientOrange
                    )
                }

                Divider(color = ResilientRule)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AMOUNT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(formatPaise(record.amountMinor), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ResilientInk)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("PAYER ID", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(record.counterpartyId.take(12) + "...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientInk)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("COUNTER", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text("#${record.counter}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientInk)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TRANSPORT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(record.transport, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientTeal)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RECEIPT DIGEST", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(record.receiptHash.take(14) + "...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientInk,
                    contentColor = ResilientPaper
                ),
                shape = ResilientPayZeroShapes.small
            ) {
                Text("RETURN TO MERCHANT DASHBOARD", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ResilientSurface,
        shape = ResilientPayZeroShapes.small
    )
}

@Composable
fun ConflictDetailDialog(
    record: LocalPaymentRecord,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "RECONCILIATION CONFLICT DETECTED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientRed
                )
                Text(
                    text = "STATE: CONFLICT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResilientRed
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "The backend reconciliation engine rejected this offline submission. A cryptographic or counter collision was detected.",
                    fontSize = 12.sp,
                    color = ResilientInk
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ResilientRed.copy(alpha = 0.08f))
                        .border(1.dp, ResilientRed)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "RECOMMENDED MERCHANT ACTION:",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = ResilientRed
                        )
                        Text(
                            text = "HOLD GOODS. Do not release merchandise. Retrying submission is prohibited. Preserve cryptographic receipt for manual bank dispute.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ResilientInk
                        )
                    }
                }

                Divider(color = ResilientRule)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TRANSACTION ID", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(record.txId.take(12) + "...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientInk)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("REUSED COUNTER", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text("#${record.counter}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ResilientRed)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AMOUNT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(formatPaise(record.amountMinor), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ResilientInk)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("REASON", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(record.conflictReason ?: "Counter already settled (Double-spend attempt)", fontSize = 11.sp, color = ResilientRed)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RETRY ALLOWED", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text("FALSE (Prohibited)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ResilientRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientInk,
                    contentColor = ResilientPaper
                ),
                shape = ResilientPayZeroShapes.small
            ) {
                Text("CLOSE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ResilientSurface,
        shape = ResilientPayZeroShapes.small
    )
}
