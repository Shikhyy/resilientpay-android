package com.resilientpay.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resilientpay.app.ui.merchant.*
import com.resilientpay.app.ui.model.*
import com.resilientpay.app.ui.payer.*
import com.resilientpay.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

enum class UserRole {
    PAYER,
    MERCHANT
}

enum class PayerSubScreen {
    HOME,
    CREATE,
    RECEIPT
}

enum class MerchantSubScreen {
    HOME,
    RECEIVE
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ResilientPayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ResilientPaper
                ) {
                    ResilientPayAppRoot()
                }
            }
        }
    }
}

@Composable
fun ResilientPayAppRoot() {
    val context = LocalContext.current

    // Global Interactive Prototype State
    var currentRole by remember { mutableStateOf(UserRole.PAYER) }
    var connectivity by remember { mutableStateOf(ConnectivityMode.C1_PROXIMITY) }
    var connectivityMenuExpanded by remember { mutableStateOf(false) }

    // Payer State
    var payerOfflineBalance by remember { mutableStateOf(450000L) } // ₹4,500.00
    var payerCounter by remember { mutableStateOf(14L) }
    var payerSubScreen by remember { mutableStateOf(PayerSubScreen.HOME) }
    var payerPendingTx by remember { mutableStateOf<LocalPaymentRecord?>(null) }
    var authorizingDialogData by remember { 
        mutableStateOf<Triple<Long, String, TransportSelection>?>(null) 
    }

    var payerTransactions by remember {
        mutableStateOf(
            listOf(
                LocalPaymentRecord(
                    txId = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                    counter = 12L,
                    amountMinor = 25000L, // ₹250.00
                    counterpartyId = "00000000-0000-0000-0000-000000000004",
                    transport = "NFC",
                    state = "SETTLED",
                    timestampUnix = System.currentTimeMillis() / 1000 - 3600,
                    receiptHash = "8d0150a1b2c3d4e5f60718293a4b5c6d"
                ),
                LocalPaymentRecord(
                    txId = "e28bc10a-32dd-4112-98ab-1f03a4b5c612",
                    counter = 13L,
                    amountMinor = 12000L, // ₹120.00
                    counterpartyId = "00000000-0000-0000-0000-000000000004",
                    transport = "QR",
                    state = "AUTHORIZED_LOCALLY",
                    timestampUnix = System.currentTimeMillis() / 1000 - 600,
                    receiptHash = "7a38b28c192d4f5e6a7b8c9d0e1f2a3b"
                )
            )
        )
    }

    // Merchant State
    val merchantId = "00000000-0000-0000-0000-000000000004"
    var merchantSubScreen by remember { mutableStateOf(MerchantSubScreen.HOME) }
    var merchantLatestReceivedTx by remember { mutableStateOf<LocalPaymentRecord?>(null) }
    var merchantCounter by remember { mutableStateOf(44L) }

    var merchantTransactions by remember {
        mutableStateOf(
            listOf(
                LocalPaymentRecord(
                    txId = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
                    counter = 41L,
                    amountMinor = 50000L, // ₹500.00
                    counterpartyId = "00000000-0000-0000-0000-000000000003",
                    transport = "BLE",
                    state = "CONFLICT",
                    timestampUnix = System.currentTimeMillis() / 1000 - 7200,
                    receiptHash = "9c1a7e2b3c4d5e6f7a8b9c0d1e2f3a4b",
                    conflictReason = "Counter already settled (Double-spend attempt)"
                ),
                LocalPaymentRecord(
                    txId = "b2c3d4e5-f6a1-4b5c-9d0e-1f2a3b4c5d6e",
                    counter = 42L,
                    amountMinor = 35000L, // ₹350.00
                    counterpartyId = "00000000-0000-0000-0000-000000000001",
                    transport = "NFC",
                    state = "SETTLED",
                    timestampUnix = System.currentTimeMillis() / 1000 - 3600,
                    receiptHash = "f4b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5"
                ),
                LocalPaymentRecord(
                    txId = "c3d4e5f6-a1b2-4c5d-0e1f-2a3b4c5d6e7f",
                    counter = 43L,
                    amountMinor = 8000L, // ₹80.00
                    counterpartyId = "00000000-0000-0000-0000-000000000002",
                    transport = "QR",
                    state = "PAYMENT RECEIVED LOCALLY",
                    timestampUnix = System.currentTimeMillis() / 1000 - 900,
                    receiptHash = "6b2c89d0e1f2a3b4c5d6e7f8a9b0c1d2"
                )
            )
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResilientInk)
            ) {
                // Main Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RESILIENTPAY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ResilientPaper,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "RESEARCH PROTOTYPE v1.0",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = ResilientSlate
                        )
                    }

                    // Interactive Connectivity Mode Selector
                    Box {
                        Row(
                            modifier = Modifier
                                .border(1.dp, ResilientPaper.copy(alpha = 0.3f))
                                .background(ResilientInk)
                                .clickable { connectivityMenuExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
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
                                text = "${connectivity.code} ▾",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ResilientPaper
                            )
                        }

                        DropdownMenu(
                            expanded = connectivityMenuExpanded,
                            onDismissRequest = { connectivityMenuExpanded = false }
                        ) {
                            ConnectivityMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "${mode.code} — ${mode.title}",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = mode.description,
                                                fontSize = 10.sp,
                                                color = ResilientSlate
                                            )
                                        }
                                    },
                                    onClick = {
                                        connectivity = mode
                                        connectivityMenuExpanded = false
                                        Toast.makeText(
                                            context,
                                            "Simulated mode switched to ${mode.code} (${mode.title})",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }

                // Dual-App Role Switcher Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isPayer = currentRole == UserRole.PAYER
                    Button(
                        onClick = { currentRole = UserRole.PAYER },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPayer) ResilientPaper else ResilientInk,
                            contentColor = if (isPayer) ResilientInk else ResilientSlate
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .border(1.dp, if (isPayer) ResilientPaper else ResilientPaper.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "PAYER CLIENT",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    val isMerchant = currentRole == UserRole.MERCHANT
                    Button(
                        onClick = { currentRole = UserRole.MERCHANT },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMerchant) ResilientPaper else ResilientInk,
                            contentColor = if (isMerchant) ResilientInk else ResilientSlate
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .border(1.dp, if (isMerchant) ResilientPaper else ResilientPaper.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "MERCHANT POS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRole) {
                UserRole.PAYER -> {
                    when (payerSubScreen) {
                        PayerSubScreen.HOME -> {
                            PayerHomeScreen(
                                offlineBalanceMinor = payerOfflineBalance,
                                connectivity = connectivity,
                                transactions = payerTransactions,
                                onInitiatePayment = {
                                    payerSubScreen = PayerSubScreen.CREATE
                                }
                            )
                        }
                        PayerSubScreen.CREATE -> {
                            PaymentCreationScreen(
                                offlineBalanceMinor = payerOfflineBalance,
                                onCancel = {
                                    payerSubScreen = PayerSubScreen.HOME
                                },
                                onProceedToAuthorize = { amountMinor, targetMerchantId, transport ->
                                    authorizingDialogData = Triple(amountMinor, targetMerchantId, transport)
                                }
                            )
                        }
                        PayerSubScreen.RECEIPT -> {
                            payerPendingTx?.let { record ->
                                PaymentReceiptScreen(
                                    record = record,
                                    onDone = {
                                        payerSubScreen = PayerSubScreen.HOME
                                    }
                                )
                            } ?: run {
                                payerSubScreen = PayerSubScreen.HOME
                            }
                        }
                    }

                    // Payer Authorize Confirmation Dialog
                    authorizingDialogData?.let { (amountMinor, targetMerchantId, transport) ->
                        PaymentAuthorizeDialog(
                            amountMinor = amountMinor,
                            merchantId = targetMerchantId,
                            transport = transport,
                            onConfirm = {
                                // Authorize & sign transaction with device key
                                payerOfflineBalance -= amountMinor
                                val newTx = LocalPaymentRecord(
                                    txId = UUID.randomUUID().toString(),
                                    counter = payerCounter++,
                                    amountMinor = amountMinor,
                                    counterpartyId = targetMerchantId,
                                    transport = transport.protocolName,
                                    state = "AUTHORIZED_LOCALLY",
                                    timestampUnix = System.currentTimeMillis() / 1000,
                                    receiptHash = UUID.randomUUID().toString().replace("-", "")
                                )
                                payerTransactions = listOf(newTx) + payerTransactions
                                payerPendingTx = newTx
                                authorizingDialogData = null
                                payerSubScreen = PayerSubScreen.RECEIPT
                            },
                            onDismiss = {
                                authorizingDialogData = null
                            }
                        )
                    }
                }

                UserRole.MERCHANT -> {
                    when (merchantSubScreen) {
                        MerchantSubScreen.HOME -> {
                            MerchantHomeScreen(
                                merchantId = merchantId,
                                connectivity = connectivity,
                                transactions = merchantTransactions,
                                onReceivePayment = {
                                    merchantSubScreen = MerchantSubScreen.RECEIVE
                                },
                                onSyncWithBackend = {
                                    if (connectivity == ConnectivityMode.C0_OFFLINE ||
                                        connectivity == ConnectivityMode.C1_PROXIMITY) {
                                        Toast.makeText(
                                            context,
                                            "Offline mode (${connectivity.code}): Cannot reach cloud backend. Reconnect to Internet (C2/C3) to reconcile.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        // Reconcile and settle all pending transactions
                                        val updated = merchantTransactions.map { tx ->
                                            if (tx.state == "PAYMENT RECEIVED LOCALLY" || tx.state == "SYNC_PENDING") {
                                                tx.copy(state = "SETTLED")
                                            } else {
                                                tx
                                            }
                                        }
                                        merchantTransactions = updated
                                        Toast.makeText(
                                            context,
                                            "Reconciliation complete: All pending payments settled with backend ledger.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                        MerchantSubScreen.RECEIVE -> {
                            MerchantReceiveScreen(
                                merchantId = merchantId,
                                onCancel = {
                                    merchantSubScreen = MerchantSubScreen.HOME
                                },
                                onPaymentReceivedLocally = { amountMinor, payerId, transportName ->
                                    val newTx = LocalPaymentRecord(
                                        txId = UUID.randomUUID().toString(),
                                        counter = merchantCounter++,
                                        amountMinor = amountMinor,
                                        counterpartyId = payerId,
                                        transport = transportName,
                                        state = "PAYMENT RECEIVED LOCALLY",
                                        timestampUnix = System.currentTimeMillis() / 1000,
                                        receiptHash = UUID.randomUUID().toString().replace("-", "")
                                    )
                                    merchantTransactions = listOf(newTx) + merchantTransactions
                                    merchantLatestReceivedTx = newTx
                                    merchantSubScreen = MerchantSubScreen.HOME
                                }
                            )
                        }
                    }

                    // Merchant Payment Received Dialog
                    merchantLatestReceivedTx?.let { receivedTx ->
                        MerchantPaymentReceivedDialog(
                            record = receivedTx,
                            onDone = {
                                merchantLatestReceivedTx = null
                            }
                        )
                    }
                }
            }
        }
    }
}
