
package com.resilientpay.app

import android.app.Application
import android.util.Log
import com.resilientpay.sdk.HardwareKeyManager
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * On first process start the hardware-wrapped Ed25519 signing key is provisioned
 * inside AndroidKeyStore. The in-memory wrapped-seed map is populated here so
 * [com.resilientpay.sdk.ResilientPayClient] can sign transactions immediately
 * without a separate provisioning step in the UI.
 *
 * Prototype residual risk: the wrapped seed is in-memory only (see HardwareKeyManager KDoc).
 * On process death the user must reinitiate a payment to re-provision (generateKey is
 * idempotent – a new AES wrap key and seed are created each time, which is acceptable for
 * the research prototype).
 */
@HiltAndroidApp
class ResilientPayApplication : Application() {

    companion object {
        private const val TAG = "ResilientPayApp"

        /** Stable alias used by all payment operations in this build. */
        const val PAYER_KEY_ALIAS = "resilientpay_payer_key_v1"
    }

    override fun onCreate() {
        super.onCreate()
        provisionKeyIfNeeded()
    }

    /**
     * Generate the hardware-wrapped signing key if it is not already provisioned for this
     * process lifetime. Logs a warning on failure but does not crash — the UI will show
     * an error when the user actually attempts to sign a transaction.
     */
    private fun provisionKeyIfNeeded() {
        val keyManager = HardwareKeyManager()
        try {
            // Check whether the key is already in the in-memory store.
            keyManager.getPublicKey(PAYER_KEY_ALIAS)
            Log.d(TAG, "Signing key already provisioned: $PAYER_KEY_ALIAS")
        } catch (_: Exception) {
            // Key not present — generate now.
            try {
                val ok = keyManager.generateKey(PAYER_KEY_ALIAS)
                if (ok) {
                    Log.i(TAG, "Hardware-wrapped signing key provisioned: $PAYER_KEY_ALIAS")
                } else {
                    Log.w(TAG, "generateKey returned false for alias: $PAYER_KEY_ALIAS")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to provision signing key '$PAYER_KEY_ALIAS': ${e.message}")
                // Non-fatal: app remains operational; signing will fail gracefully per
                // TransportResult.Error when the user attempts a transaction.
            }
        }
    }
}

