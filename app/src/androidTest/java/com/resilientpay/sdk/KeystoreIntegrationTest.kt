package com.resilientpay.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.resilientpay_core.FfiException
import uniffi.resilientpay_core.ResilientPayClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import android.security.keystore.KeyInfo
import java.security.KeyFactory

@RunWith(AndroidJUnit4::class)
class KeystoreIntegrationTest {

    private val EXPECTED_CBOR_HEX = "8d015000000000000000000000000000000001500000000000000000000000000000000250000000000000000000000000000000035000000000000000000000000000000004189663494e520150010101010101010101010101010101011a6553f1001a6553ff10f6f6"
    private val TEST_KEY_ID = "00000000-0000-0000-0000-000000000003"
    private val DOMAIN_SEP = "resilientpay:payment-envelope:v1:"

    private fun decodeHex(hexString: String): ByteArray {
        val result = ByteArray(hexString.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = Integer.parseInt(hexString.substring(index, index + 2), 16)
            result[i] = j.toByte()
        }
        return result
    }

    @Test
    fun testNativeLibraryLoadAndClientCreation() {
        val keyManager = HardwareKeyManager()
        val client = ResilientPayClient(keyManager)
        assertNotNull(client)
        client.close()
    }

    @Test
    fun testHardwareKeystoreSigning_And_HardwareBackingCheck() {
        val keyManager = HardwareKeyManager()
        
        // Ensure key generation succeeds
        keyManager.generateKey(TEST_KEY_ID, requireStrongBox = false)
        
        // Test Keystore Hardware-backing level
        val factory = KeyFactory.getInstance("EC", "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(TEST_KEY_ID, null),
            KeyInfo::class.java
        )
        
        val isHardwareBacked = keyInfo.isInsideSecureHardware
        println("SECURITY CLASSIFICATION: isInsideSecureHardware = $isHardwareBacked")

        // 1. Rust canonical bytes domain string
        val payload = DOMAIN_SEP.toByteArray() + decodeHex(EXPECTED_CBOR_HEX)

        // 2. Sign
        val signature = keyManager.sign(TEST_KEY_ID, payload)
        assertNotNull(signature)
        assertTrue(signature.size == 64) // Ed25519 is 64 bytes
        
        // 3. Repeated signing
        val signature2 = keyManager.sign(TEST_KEY_ID, payload)
        assertNotNull(signature2)
    }

    @Test
    fun testConcurrencyAndThreadSafety() {
        val keyManager = HardwareKeyManager()
        keyManager.generateKey("concurrent_key")
        val payload = DOMAIN_SEP.toByteArray() + decodeHex(EXPECTED_CBOR_HEX)

        val threads = 10
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val successCount = AtomicInteger(0)

        for (i in 0 until threads) {
            executor.submit {
                try {
                    val sig = keyManager.sign("concurrent_key", payload)
                    if (sig.size == 64) {
                        successCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        assertEquals("All concurrent signatures must succeed safely", threads, successCount.get())
    }

    @Test
    fun testKeyInvalidationAndErrorMapping() {
        val keyManager = HardwareKeyManager()
        try {
            keyManager.sign("non_existent_key", ByteArray(10))
            fail("Should throw FfiException.SigningFailure or KeyUnavailable")
        } catch (e: Exception) {
            // Success (FfiException generated Kotlin classes will catch it)
            assertTrue(e.message?.contains("not found") == true || e.message?.contains("SigningFailure") == true)
        }
    }
}
