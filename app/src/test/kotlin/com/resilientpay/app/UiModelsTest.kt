package com.resilientpay.app

import com.resilientpay.app.ui.model.ConnectivityMode
import com.resilientpay.app.ui.model.LocalPaymentRecord
import com.resilientpay.app.ui.model.formatPaise
import com.resilientpay.app.ui.model.formatPaiseWithMinor
import org.junit.Assert.assertEquals
import org.junit.Test

class UiModelsTest {

    @Test
    fun testFormatPaise_ExactRupeesAndFractional() {
        assertEquals("₹0.00", formatPaise(0L))
        assertEquals("₹0.05", formatPaise(5L))
        assertEquals("₹0.50", formatPaise(50L))
        assertEquals("₹1.00", formatPaise(100L))
        assertEquals("₹150.00", formatPaise(15000L))
        assertEquals("₹4500.00", formatPaise(450000L))
    }

    @Test
    fun testFormatPaiseWithMinor() {
        assertEquals("₹150.00 (15000 paise)", formatPaiseWithMinor(15000L))
    }

    @Test
    fun testConnectivityModeCodes() {
        assertEquals("C3", ConnectivityMode.C3_ONLINE.code)
        assertEquals("C2", ConnectivityMode.C2_DEGRADED.code)
        assertEquals("C1", ConnectivityMode.C1_PROXIMITY.code)
        assertEquals("C0", ConnectivityMode.C0_OFFLINE.code)
    }

    @Test
    fun testLocalPaymentRecordImmutability() {
        val record = LocalPaymentRecord(
            txId = "test-uuid",
            counter = 10L,
            amountMinor = 50000L,
            counterpartyId = "merchant-1",
            transport = "NFC",
            state = "AUTHORIZED_LOCALLY",
            timestampUnix = 1700000000L,
            receiptHash = "hash123",
            conflictReason = null
        )

        assertEquals("test-uuid", record.txId)
        assertEquals(10L, record.counter)
        assertEquals(50000L, record.amountMinor)
        assertEquals("AUTHORIZED_LOCALLY", record.state)
    }
}
