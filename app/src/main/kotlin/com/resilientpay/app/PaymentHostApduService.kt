package com.resilientpay.app

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.resilientpay.sdk.NfcAdapter

/**
 * Host-based Card Emulation (HCE) service for merchant-side proximity payments.
 *
 * This service implements the ISO/IEC 7816-4 APDU contract defined in
 * docs/05-protocol/TRANSPORT_IMPLEMENTATION.md §2:
 *  - SELECT AID command: responds with SW_OK (0x90 0x00)
 *  - Envelope payload command: extracts CBOR envelope bytes, broadcasts to local UI,
 *    and responds with SW_OK.
 *
 * SECURITY INVARIANT (AGENTS.md & security-rules.md):
 *  - Transport adapters and HCE services strictly move protocol bytes.
 *  - This service MUST NOT authorize transactions or assume delivered bytes are valid.
 *  - The receiving application MUST verify the RFC 8032 Ed25519 signature, counter
 *    monotonicity, and credential active status before accepting any payment.
 */
class PaymentHostApduService : HostApduService() {

    companion object {
        private const val TAG = "ResilientPayHCE"

        /** Broadcast action triggered when a signed envelope arrives via NFC. */
        const val ACTION_NFC_PAYMENT_RECEIVED = "com.resilientpay.NFC_PAYMENT_RECEIVED"
        const val EXTRA_ENVELOPE_BYTES = "extra_envelope_bytes"

        // ISO/IEC 7816-4 Command Headers
        private const val CLA_ISO7816: Byte = 0x00.toByte()
        private const val INS_SELECT: Byte = 0xA4.toByte()
        private const val P1_SELECT_BY_NAME: Byte = 0x04.toByte()

        // ResilientPay proprietary APDU instruction for transmitting envelope bytes
        private const val CLA_RESPAY: Byte = 0x80.toByte()
        private const val INS_TRANSMIT_ENVELOPE: Byte = 0x20.toByte()

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val SW_WRONG_DATA = byteArrayOf(0x6A.toByte(), 0x80.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            Log.w(TAG, "Malformed or null APDU received")
            return SW_WRONG_DATA
        }

        val cla = commandApdu[0]
        val ins = commandApdu[1]

        // 1. Handle SELECT AID APDU
        if (cla == CLA_ISO7816 && ins == INS_SELECT) {
            Log.d(TAG, "SELECT AID command received on ResilientPay HCE channel")
            return SW_OK
        }

        // 2. Handle Envelope Payload Transmission APDU
        if (cla == CLA_RESPAY || cla == CLA_ISO7816) {
            // Lc byte indicates length of incoming payload
            val lc = if (commandApdu.size > 4) commandApdu[4].toInt() and 0xFF else 0
            val payloadBytes = if (commandApdu.size >= 5 + lc && lc > 0) {
                commandApdu.copyOfRange(5, 5 + lc)
            } else {
                // If direct raw transmission without ISO header framing
                commandApdu
            }

            Log.i(TAG, "Received ${payloadBytes.size} payment envelope bytes over NFC")

            // Broadcast envelope to application components for verification
            val broadcastIntent = Intent(ACTION_NFC_PAYMENT_RECEIVED).apply {
                putExtra(EXTRA_ENVELOPE_BYTES, payloadBytes)
                setPackage(packageName)
            }
            sendBroadcast(broadcastIntent)

            return SW_OK
        }

        Log.w(TAG, "Unsupported APDU CLA=0x%02X INS=0x%02X".format(cla, ins))
        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "LINK_LOSS"
            DEACTIVATION_DESELECTED -> "DESELECTED"
            else -> "UNKNOWN($reason)"
        }
        Log.d(TAG, "HCE NFC Link deactivated: $reasonStr")
    }
}
