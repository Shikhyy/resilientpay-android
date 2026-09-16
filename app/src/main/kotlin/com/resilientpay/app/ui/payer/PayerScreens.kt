package com.resilientpay.app.ui.payer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resilientpay.app.ui.model.*
import com.resilientpay.app.ui.theme.*

@Composable
fun PayerHomeScreen(
    offlineBalanceMinor: Long,
    connectivity: ConnectivityMode,
    transactions: List<LocalPaymentRecord>,
    onInitiatePayment: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        // 2. Offline Spending Balance Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "AVAILABLE OFFLINE BALANCE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientSlate,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatPaise(offlineBalanceMinor),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientInk
                    )
                    Text(
                        text = "$offlineBalanceMinor minor units (paise) · Device-Bound Keystore Key",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ResilientSlate
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = ResilientRule.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CREDENTIAL", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ResilientSlate)
                            Text("ACTIVE (Ed25519)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ResilientTeal)
                        }
                        Column {
                            Text("PER-TX LIMIT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ResilientSlate)
                            Text(formatPaise(50000), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ResilientInk)
                        }
                        Column {
                            Text("COUNTER CEILING", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ResilientSlate)
                            Text("#1,000 max", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ResilientInk)
                        }
                    }
                }
            }
        }

        // 3. Make Payment Action
        item {
            Button(
                onClick = onInitiatePayment,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientInk,
                    contentColor = ResilientPaper
                ),
                shape = ResilientPayThemeShapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "SEND OFFLINE PAYMENT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // 4. Pending Sync Banner (if any)
        val pendingCount = transactions.count { it.state == "SYNC_PENDING" || it.state == "AUTHORIZED_LOCALLY" }
        if (pendingCount > 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ResilientOrange)
                        .background(ResilientOrange.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SYNCHRONIZATION PENDING",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ResilientOrange
                            )
                            Text(
                                text = "$pendingCount local transactions queued for cloud reconciliation",
                                fontSize = 12.sp,
                                color = ResilientInk
                            )
                        }
                    }
                }
            }
        }

        // 5. Recent Transactions List
        item {
            Text(
                text = "RECENT TRANSACTION LEDGER",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientSlate,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

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
                        text = "No transactions recorded in local ledger",
                        fontSize = 13.sp,
                        color = ResilientSlate,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(transactions) { tx ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ResilientRule)
                        .background(ResilientSurface)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "To: ${tx.counterpartyId.take(16)}...",
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
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "-${formatPaise(tx.amountMinor)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientInk
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
}

private val ResilientPayThemeShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
)

@Composable
fun PaymentCreationScreen(
    offlineBalanceMinor: Long,
    onCancel: () -> Unit,
    onProceedToAuthorize: (amountMinor: Long, merchantId: String, transport: TransportSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var amountInput by remember { mutableStateOf("") }
    var merchantId by remember { mutableStateOf("00000000-0000-0000-0000-000000000004") }
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
            Text(
                text = "NEW OFFLINE PAYMENT",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientSlate,
                letterSpacing = 1.sp
            )

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
                        text = "PAYMENT AMOUNT (RUPEES)",
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Max offline limit: ${formatPaise(offlineBalanceMinor)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ResilientSlate
                    )
                }
            }

            // Recipient Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "MERCHANT RECIPIENT ID",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientSlate
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = merchantId,
                        onValueChange = { merchantId = it },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = ResilientInk
                        ),
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
                text = "SELECT PROXIMITY TRANSPORT",
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
                listOf(TransportSelection.NFC, TransportSelection.BLE, TransportSelection.QR).forEach { tr ->
                    val isSelected = selectedTransport == tr
                    Button(
                        onClick = { selectedTransport = tr },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ResilientInk else ResilientSurface,
                            contentColor = if (isSelected) ResilientPaper else ResilientInk
                        ),
                        shape = ResilientPayThemeShapes.small,
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

        // Bottom Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = ResilientPayThemeShapes.small,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
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

            Button(
                onClick = {
                    val enteredDouble = amountInput.toDoubleOrNull() ?: 0.0
                    val amountMinor = (enteredDouble * 100).toLong()

                    if (amountMinor <= 0) {
                        errorMessage = "Amount must be greater than zero"
                    } else if (amountMinor > offlineBalanceMinor) {
                        errorMessage = "Amount exceeds available offline balance"
                    } else if (merchantId.isBlank()) {
                        errorMessage = "Merchant ID cannot be empty"
                    } else {
                        onProceedToAuthorize(amountMinor, merchantId, selectedTransport)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientTeal,
                    contentColor = ResilientPaper
                ),
                shape = ResilientPayThemeShapes.small,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "AUTHORIZE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun PaymentAuthorizeDialog(
    amountMinor: Long,
    merchantId: String,
    transport: TransportSelection,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "AUTHORIZE OFFLINE PAYMENT",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Device will sign a 106-byte canonical CBOR envelope with hardware-wrapped Ed25519 key.",
                    fontSize = 12.sp,
                    color = ResilientSlate
                )
                Divider(color = ResilientRule)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AMOUNT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(formatPaise(amountMinor), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ResilientInk)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TRANSPORT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(transport.protocolName, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ResilientTeal)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RECIPIENT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
                    Text(merchantId.take(12) + "...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientInk)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResilientTeal,
                    contentColor = ResilientPaper
                ),
                shape = ResilientPayThemeShapes.small
            ) {
                Text("CONFIRM PIN / BIOMETRIC", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
            }
        },
        containerColor = ResilientSurface,
        shape = ResilientPayThemeShapes.small
    )
}

@Composable
fun PaymentReceiptScreen(
    record: LocalPaymentRecord,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ResilientPaper)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Verified Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(ResilientTeal),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 28.sp, color = ResilientPaper, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "AUTHORIZED LOCALLY",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientInk,
                letterSpacing = 1.sp
            )

            Text(
                text = formatPaise(record.amountMinor),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientInk
            )

            Text(
                text = "Stored securely in local ledger. Cloud reconciliation is pending.",
                fontSize = 12.sp,
                color = ResilientSlate,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Cryptographic Receipt Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ResilientRule)
                    .background(ResilientSurface)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CRYPTOGRAPHIC RECEIPT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResilientSlate
                    )
                    Divider(color = ResilientRule.copy(alpha = 0.5f))

                    ReceiptRow("TRANSACTION ID", record.txId.take(18) + "...")
                    ReceiptRow("COUNTER NUMBER", "#${record.counter}")
                    ReceiptRow("TRANSPORT", record.transport)
                    ReceiptRow("TIMESTAMP", "${record.timestampUnix} UNIX")
                    ReceiptRow("RECEIPT HASH", record.receiptHash.take(16) + "...")
                    ReceiptRow("STATUS", record.state)
                }
            }
        }

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(
                containerColor = ResilientInk,
                contentColor = ResilientPaper
            ),
            shape = ResilientPayThemeShapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("RETURN TO DASHBOARD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
        Text(text = value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ResilientInk)
    }
}
