package com.resilientpay.sdk

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import uniffi.resilientpay_core.AndroidKeyManager
import uniffi.resilientpay_core.FfiException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-wrapped Ed25519 implementation of the Rust FFI callback.
 *
 * Android Keystore hardware (Keymaster / KeyMint prior to API 35) does not natively
 * support Ed25519 key generation or signing.
 * Per ADR-012, this class implements the Keystore-wrapped pattern:
 *  - A hardware-backed AES-256-GCM key is generated and stored inside AndroidKeyStore (StrongBox/TEE).
 *  - The RFC 8032 Ed25519 32-byte seed is encrypted under this hardware key at rest.
 *  - On sign(), the seed is decrypted into transient memory, the 64-byte Ed25519 signature
 *    is produced via RFC 8032, and the transient seed is immediately zeroized.
 *  - An EC keypair is also registered in AndroidKeyStore to allow hardware-backing inspection
 *    via KeyInfo.isInsideSecureHardware.
 */
class HardwareKeyManager : AndroidKeyManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private data class WrappedKeyEntry(
        val iv: ByteArray,
        val encryptedSeed: ByteArray,
        val publicKeyBytes: ByteArray,
    )

    companion object {
        /**
         * In-process encrypted-seed store.
         *
         * Security properties:
         *  - Seeds are NEVER stored plaintext; each entry holds an AES-256-GCM ciphertext
         *    encrypted under a hardware-backed key that lives exclusively inside AndroidKeyStore.
         *  - The map is in-memory only (process lifetime). On process death all wrapped entries
         *    are lost; the hardware AES key persists in AndroidKeyStore.
         *  - This is intentional for the research prototype: production would persist
         *    the ciphertext to encrypted SharedPreferences or the Android Keystore directly
         *    using a Key Wrapping scheme upon app restart.
         *  - This map MUST NOT be serialized, logged, or transmitted across process boundaries.
         *  - ConcurrentHashMap is used because [sign] and [generateKey] may be called from
         *    different threads (Rust FFI threadpool vs. Compose UI thread).
         *
         * Prototype residual risk: App restart requires re-provisioning the key (calling
         * [generateKey] or [importSeed] again). This is acceptable for research; a production
         * implementation would call [importSeed] with the persisted ciphertext on each cold start.
         */
        private val keyStorage = ConcurrentHashMap<String, WrappedKeyEntry>()
        @Volatile
        private var appContext: android.content.Context? = null

        /**
         * Initialize application context for persisting hardware-wrapped key blobs across restarts.
         */
        fun initialize(context: android.content.Context) {
            appContext = context.applicationContext
        }

        private fun persistEntry(keyId: String, entry: WrappedKeyEntry) {
            appContext?.let { ctx ->
                val prefs = ctx.getSharedPreferences("resilientpay_wrapped_keys", android.content.Context.MODE_PRIVATE)
                val b64 = java.util.Base64.getEncoder()
                prefs.edit()
                    .putString("${keyId}_iv", b64.encodeToString(entry.iv))
                    .putString("${keyId}_seed", b64.encodeToString(entry.encryptedSeed))
                    .putString("${keyId}_pub", b64.encodeToString(entry.publicKeyBytes))
                    .apply()
            }
        }

        private fun loadPersistedEntry(keyId: String): WrappedKeyEntry? {
            val ctx = appContext ?: return null
            val prefs = ctx.getSharedPreferences("resilientpay_wrapped_keys", android.content.Context.MODE_PRIVATE)
            val ivB64 = prefs.getString("${keyId}_iv", null) ?: return null
            val seedB64 = prefs.getString("${keyId}_seed", null) ?: return null
            val pubB64 = prefs.getString("${keyId}_pub", null) ?: return null
            return try {
                val b64 = java.util.Base64.getDecoder()
                WrappedKeyEntry(
                    iv = b64.decode(ivB64),
                    encryptedSeed = b64.decode(seedB64),
                    publicKeyBytes = b64.decode(pubB64)
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun getPublicKey(keyId: String): ByteArray {
        val entry = keyStorage[keyId]
            ?: loadPersistedEntry(keyId)?.also { keyStorage[keyId] = it }
            ?: throw FfiException.KeyUnavailable("Key not found in Keystore: $keyId")
        return entry.publicKeyBytes.copyOf()
    }

    override fun sign(keyId: String, payload: ByteArray): ByteArray {
        val entry = keyStorage[keyId]
            ?: loadPersistedEntry(keyId)?.also { keyStorage[keyId] = it }
            ?: throw FfiException.KeyUnavailable("Private key not found: $keyId")

        val aesKey = try {
            keyStore.getKey("${keyId}_aes", null) as? SecretKey
                ?: throw FfiException.KeyUnavailable("Keystore AES key not found: ${keyId}_aes")
        } catch (e: FfiException) {
            throw e
        } catch (e: Exception) {
            throw FfiException.SigningFailure("Failed to retrieve Keystore key: ${e.message}")
        }

        var seed: ByteArray? = null
        try {
            // Decrypt the Ed25519 seed inside transient memory using the Keystore-backed AES key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, entry.iv)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
            seed = cipher.doFinal(entry.encryptedSeed)

            // Produce Ed25519 RFC 8032 signature
            val privParams = Ed25519PrivateKeyParameters(seed, 0)
            val signer = Ed25519Signer()
            signer.init(true, privParams)
            signer.update(payload, 0, payload.size)
            return signer.generateSignature()
        } catch (e: Exception) {
            throw FfiException.SigningFailure("Signing failed: ${e.message}")
        } finally {
            // Immediately zeroize the transient private seed in memory
            if (seed != null) {
                Arrays.fill(seed, 0.toByte())
            }
        }
    }

    /**
     * Generate a new hardware-wrapped Ed25519 keypair.
     *
     * @param keyId Unique identifier for this key
     * @param requireStrongBox If true, requests StrongBox hardware security module
     * @return true if hardware key generation succeeded
     */
    fun generateKey(keyId: String, requireStrongBox: Boolean = false): Boolean {
        // 1. Generate EC key in AndroidKeyStore for hardware security inspection
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val ecBuilder = KeyGenParameterSpec.Builder(
            keyId,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setDigests(KeyProperties.DIGEST_NONE)
        if (requireStrongBox) {
            ecBuilder.setIsStrongBoxBacked(true)
        }
        kpg.initialize(ecBuilder.build())
        kpg.generateKeyPair()

        // 2. Generate hardware-backed AES-256-GCM key in AndroidKeyStore for seed wrapping
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val aesBuilder = KeyGenParameterSpec.Builder(
            "${keyId}_aes",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (requireStrongBox) {
            aesBuilder.setIsStrongBoxBacked(true)
        }
        kg.init(aesBuilder.build())
        val aesKey = kg.generateKey()

        // 3. Generate fresh 32-byte Ed25519 seed
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)

        try {
            // 4. Derive raw 32-byte Ed25519 public key
            val privParams = Ed25519PrivateKeyParameters(seed, 0)
            val pubParams = privParams.generatePublicKey()
            val publicKeyBytes = pubParams.encoded

            // 5. Encrypt the seed using hardware AES key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey)
            val iv = cipher.iv
            val encryptedSeed = cipher.doFinal(seed)

            // 6. Store wrapped entry
            val entry = WrappedKeyEntry(
                iv = iv,
                encryptedSeed = encryptedSeed,
                publicKeyBytes = publicKeyBytes,
            )
            keyStorage[keyId] = entry
            persistEntry(keyId, entry)
            return true
        } finally {
            Arrays.fill(seed, 0.toByte())
        }
    }

    /**
     * Import a known 32-byte Ed25519 seed under hardware protection (for test vector verification).
     */
    fun importSeed(keyId: String, seed: ByteArray, requireStrongBox: Boolean = false): Boolean {
        require(seed.size == 32) { "Ed25519 seed must be exactly 32 bytes" }

        // Generate hardware-backed AES key
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val aesBuilder = KeyGenParameterSpec.Builder(
            "${keyId}_aes",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (requireStrongBox) {
            aesBuilder.setIsStrongBoxBacked(true)
        }
        kg.init(aesBuilder.build())
        val aesKey = kg.generateKey()

        // Derive public key
        val privParams = Ed25519PrivateKeyParameters(seed, 0)
        val pubParams = privParams.generatePublicKey()
        val publicKeyBytes = pubParams.encoded

        // Encrypt seed
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val encryptedSeed = cipher.doFinal(seed)

        val entry = WrappedKeyEntry(
            iv = iv,
            encryptedSeed = encryptedSeed,
            publicKeyBytes = publicKeyBytes,
        )
        keyStorage[keyId] = entry
        persistEntry(keyId, entry)
        return true
    }
}
