package com.resilientpay.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.resilientpay.sdk.BleAdapter
import com.resilientpay.sdk.HardwareKeyManager
import com.resilientpay.sdk.InternetAdapter
import com.resilientpay.sdk.NfcAdapter
import com.resilientpay.sdk.QrAdapter
import com.resilientpay.sdk.ResilientPayClient
import com.resilientpay.sdk.SmsAdapter
import com.resilientpay.sdk.TransportResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
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
    val coroutineScope = rememberCoroutineScope()

    // Global Dynamic Configuration & Settings
    var settings by remember { mutableStateOf(AppSettings()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    // Prevents double-submit while FFI is in-flight
    var transactionInProgress by remember { mutableStateOf(false) }

    // Navigation & Connectivity State
    var currentRole by remember { mutableStateOf(UserRole.PAYER) }
    var connectivity by remember { mutableStateOf(ConnectivityMode.C1_PROXIMITY) }
    var connectivityMenuExpanded by remember { mutableStateOf(false) }

    // Dynamic Payer State
    var payerOfflineBalance by remember { mutableStateOf(450000L) } // 450,000 paise = ₹4,500.00
    var payerCounter by remember { mutableStateOf(14L) }
    var payerSubScreen by remember { mutableStateOf(PayerSubScreen.HOME) }
    var payerPendingTx by remember { mutableStateOf<LocalPaymentRecord?>(null) }
    var authorizingDialogData by remember { 
        mutableStateOf<Triple<Long, String, TransportSelection>?>(null) 
    }

    // Payer Ledger (starts clean; can be loaded with demo test vectors via Settings)
    var payerTransactions by remember {
        mutableStateOf(createInitialPayerLedger(settings.merchantId))
    }

    // Dynamic Merchant State
    var merchantSubScreen by remember { mutableStateOf(MerchantSubScreen.HOME) }
    var merchantLatestReceivedTx by remember { mutableStateOf<LocalPaymentRecord?>(null) }
    var merchantCounter by remember { mutableStateOf(44L) }

    // Merchant Ledger (starts clean; can be loaded with demo test vectors via Settings)
    var merchantTransactions by remember {
        mutableStateOf(createInitialMerchantLedger(settings.payerCredentialId))
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic Settings Config Button
                        OutlinedButton(
                            onClick = { showSettingsDialog = true },
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .border(1.dp, ResilientPaper.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CONFIG",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResilientPaper
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
                        shape = RoundedCornerShape(0.dp),
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
                        shape = RoundedCornerShape(0.dp),
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
                                perTxLimitMinor = settings.maxPerTxLimitMinor,
                                counterCeiling = settings.maxCounterCeiling,
                                onInitiatePayment = {
                                    payerSubScreen = PayerSubScreen.CREATE
                                }
                            )
                        }
                        PayerSubScreen.CREATE -> {
                            PaymentCreationScreen(
                                offlineBalanceMinor = payerOfflineBalance,
                                initialMerchantId = settings.merchantId,
                                perTxLimitMinor = settings.maxPerTxLimitMinor,
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
                                if (transactionInProgress) return@PaymentAuthorizeDialog
                                transactionInProgress = true

                                // Snapshot counter before launch; post-increment atomically.
                                val newTxId = UUID.randomUUID().toString()
                                val newCounter = payerCounter++
                                val createdAtUnix = System.currentTimeMillis() / 1000
                                val expiresAtUnix = createdAtUnix + 3600L

                                // Generate 16-byte cryptographically secure nonce.
                                val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }

                                // Debit balance optimistically; restored on error.
                                payerOfflineBalance -= amountMinor
                                // Dismiss the dialog immediately so UI is responsive.
                                authorizingDialogData = null

                                coroutineScope.launch {
                                    // Build the transport adapter on the UI thread (stateless objects).
                                    val chosenTransport = when (transport.protocolName) {
                                        "INTERNET" -> InternetAdapter(settings.backendUrl)
                                        "NFC"      -> NfcAdapter()
                                        "BLE"      -> BleAdapter()
                                        "QR"       -> QrAdapter()
                                        "SMS"      -> SmsAdapter(settings.smsGatewayNumber)
                                        else       -> InternetAdapter(settings.backendUrl)
                                    }

                                    // FFI + transport on IO — never blocks the main thread.
                                    val result = withContext(Dispatchers.IO) {
                                        ResilientPayClient(
                                            transport = chosenTransport,
                                            keyManager = HardwareKeyManager()
                                        ).use { client ->
                                            client.createAndSubmitTransaction(
                                                txIdStr          = newTxId,
                                                credentialIdStr  = settings.payerCredentialId,
                                                payerKeyIdStr    = ResilientPayApplication.PAYER_KEY_ALIAS,
                                                merchantIdStr    = targetMerchantId,
                                                amountMinor      = amountMinor,
                                                counter          = newCounter,
                                                nonceBytes       = nonceBytes,
                                                createdAtUnix    = createdAtUnix,
                                                expiresAtUnix    = expiresAtUnix
                                            )
                                        }
                                    }

                                    // Back on main thread: update state based on result.
                                    transactionInProgress = false
                                    when (result) {
                                        is TransportResult.Error -> {
                                            // Restore balance — the payment was not delivered.
                                            payerOfflineBalance += amountMinor
                                            // Roll back counter increment.
                                            payerCounter = newCounter
                                            Toast.makeText(
                                                context,
                                                "Payment failed: ${result.reason}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        is TransportResult.Success -> {
                                            // Receipt hash = SHA-256 of the actual signed envelope bytes.
                                            val receiptDigest = computeSha256Hex(result.receiptBytes)
                                            val txState = if (transport.protocolName == "INTERNET") {
                                                "PENDING_RECONCILIATION"
                                            } else {
                                                "AUTHORIZED_LOCALLY"
                                            }
                                            val newTx = LocalPaymentRecord(
                                                txId          = newTxId,
                                                counter       = newCounter,
                                                amountMinor   = amountMinor,
                                                counterpartyId = targetMerchantId,
                                                transport     = transport.protocolName,
                                                state         = txState,
                                                timestampUnix = createdAtUnix,
                                                receiptHash   = receiptDigest
                                            )
                                            payerTransactions = listOf(newTx) + payerTransactions
                                            payerPendingTx = newTx
                                            payerSubScreen = PayerSubScreen.RECEIPT
                                        }
                                    }
                                }
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
                                merchantId = settings.merchantId,
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
                                merchantId = settings.merchantId,
                                defaultPayerId = settings.payerCredentialId,
                                onCancel = {
                                    merchantSubScreen = MerchantSubScreen.HOME
                                },
                                onPaymentReceivedLocally = { amountMinor, payerId, transportName ->
                                    val newTxId = UUID.randomUUID().toString()
                                    val newCounter = merchantCounter++
                                    val timestampUnix = System.currentTimeMillis() / 1000
                                    // Real cryptographic SHA-256 digest of envelope payload
                                    val receiptDigest = computeSha256Hex(
                                        "$newTxId:$newCounter:$amountMinor:$payerId:$timestampUnix".toByteArray()
                                    )
                                    val newTx = LocalPaymentRecord(
                                        txId = newTxId,
                                        counter = newCounter,
                                        amountMinor = amountMinor,
                                        counterpartyId = payerId,
                                        transport = transportName,
                                        state = "PAYMENT RECEIVED LOCALLY",
                                        timestampUnix = timestampUnix,
                                        receiptHash = receiptDigest
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

    // Dynamic Settings & Configuration Dialog
    if (showSettingsDialog) {
        SettingsConfigDialog(
            currentSettings = settings,
            onSave = { updated ->
                settings = updated
                showSettingsDialog = false
                Toast.makeText(context, "System configuration saved", Toast.LENGTH_SHORT).show()
            },
            onSeedDemoData = {
                payerTransactions = createInitialPayerLedger(settings.merchantId)
                merchantTransactions = createInitialMerchantLedger(settings.payerCredentialId)
                Toast.makeText(context, "Research test vectors loaded into ledger", Toast.LENGTH_SHORT).show()
            },
            onClearData = {
                payerTransactions = emptyList()
                merchantTransactions = emptyList()
                Toast.makeText(context, "Local ledgers cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun SettingsConfigDialog(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onSeedDemoData: () -> Unit,
    onClearData: () -> Unit,
    onDismiss: () -> Unit
) {
    var merchantIdInput by remember { mutableStateOf(currentSettings.merchantId) }
    var payerCredInput by remember { mutableStateOf(currentSettings.payerCredentialId) }
    var backendUrlInput by remember { mutableStateOf(currentSettings.backendUrl) }
    var smsGatewayInput by remember { mutableStateOf(currentSettings.smsGatewayNumber) }
    var perTxLimitRupees by remember { mutableStateOf((currentSettings.maxPerTxLimitMinor / 100).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "SYSTEM CONFIGURATION",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ResilientInk
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Configure dynamic endpoints and cryptographic identity bounds:",
                    fontSize = 11.sp,
                    color = ResilientSlate
                )

                OutlinedTextField(
                    value = merchantIdInput,
                    onValueChange = { merchantIdInput = it },
                    label = { Text("Merchant Recipient ID", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = payerCredInput,
                    onValueChange = { payerCredInput = it },
                    label = { Text("Payer Credential ID", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    label = { Text("Reconciliation Backend URL", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = smsGatewayInput,
                    onValueChange = { smsGatewayInput = it },
                    label = { Text("Telecom SMS Gateway Number", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = perTxLimitRupees,
                    onValueChange = { perTxLimitRupees = it.filter { c -> c.isDigit() } },
                    label = { Text("Max Per-Tx Limit (Rupees)", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(color = ResilientRule)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSeedDemoData,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SEED FIXTURES", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onClearData,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLEAR LEDGER", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ResilientRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limitRupees = perTxLimitRupees.toLongOrNull() ?: 500L
                    val updated = currentSettings.copy(
                        merchantId = merchantIdInput.trim(),
                        payerCredentialId = payerCredInput.trim(),
                        backendUrl = backendUrlInput.trim(),
                        smsGatewayNumber = smsGatewayInput.trim(),
                        maxPerTxLimitMinor = limitRupees * 100
                    )
                    onSave(updated)
                },
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ResilientTeal, contentColor = ResilientPaper)
            ) {
                Text("SAVE CONFIG", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ResilientSlate)
            }
        },
        shape = RoundedCornerShape(0.dp),
        containerColor = ResilientSurface
    )
}

private fun createInitialPayerLedger(merchantId: String): List<LocalPaymentRecord> {
    return listOf(
        LocalPaymentRecord(
            txId = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            counter = 12L,
            amountMinor = 25000L,
            counterpartyId = merchantId,
            transport = "NFC",
            state = "SETTLED",
            timestampUnix = System.currentTimeMillis() / 1000 - 3600,
            receiptHash = computeSha256Hex("f47ac10b-58cc-4372-a567-0e02b2c3d479:12:25000".toByteArray())
        ),
        LocalPaymentRecord(
            txId = "e28bc10a-32dd-4112-98ab-1f03a4b5c612",
            counter = 13L,
            amountMinor = 12000L,
            counterpartyId = merchantId,
            transport = "QR",
            state = "AUTHORIZED_LOCALLY",
            timestampUnix = System.currentTimeMillis() / 1000 - 600,
            receiptHash = computeSha256Hex("e28bc10a-32dd-4112-98ab-1f03a4b5c612:13:12000".toByteArray())
        )
    )
}

private fun createInitialMerchantLedger(payerId: String): List<LocalPaymentRecord> {
    return listOf(
        LocalPaymentRecord(
            txId = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
            counter = 41L,
            amountMinor = 50000L,
            counterpartyId = payerId,
            transport = "BLE",
            state = "CONFLICT",
            timestampUnix = System.currentTimeMillis() / 1000 - 7200,
            receiptHash = computeSha256Hex("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d:41:50000".toByteArray()),
            conflictReason = "Counter already settled (Double-spend attempt)"
        ),
        LocalPaymentRecord(
            txId = "b2c3d4e5-f6a1-4b5c-9d0e-1f2a3b4c5d6e",
            counter = 42L,
            amountMinor = 35000L,
            counterpartyId = payerId,
            transport = "NFC",
            state = "SETTLED",
            timestampUnix = System.currentTimeMillis() / 1000 - 3600,
            receiptHash = computeSha256Hex("b2c3d4e5-f6a1-4b5c-9d0e-1f2a3b4c5d6e:42:35000".toByteArray())
        ),
        LocalPaymentRecord(
            txId = "c3d4e5f6-a1b2-4c5d-0e1f-2a3b4c5d6e7f",
            counter = 43L,
            amountMinor = 8000L,
            counterpartyId = payerId,
            transport = "QR",
            state = "PAYMENT RECEIVED LOCALLY",
            timestampUnix = System.currentTimeMillis() / 1000 - 900,
            receiptHash = computeSha256Hex("c3d4e5f6-a1b2-4c5d-0e1f-2a3b4c5d6e7f:43:8000".toByteArray())
        )
    )
}
