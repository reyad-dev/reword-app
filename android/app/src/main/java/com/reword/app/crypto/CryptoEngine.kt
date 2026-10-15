package com.reword.app.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ReWord Cryptographic Engine
 *
 * Implements AES-256-GCM + PBKDF2 (HMAC-SHA256, 310,000 iterations).
 * Strictly mirrors the web and Chrome extension implementation in core.js:
 *
 * Salt: 32 bytes (indices 0..31)
 * IV: 12 bytes (indices 32..43)
 * Ciphertext with appended 16-byte GCM tag: indices 44..end
 * All Base64-encoded.
 */
object CryptoEngine {
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 32
    private const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    /**
     * Derives a 256-bit AES key from master password and salt using PBKDF2WithHmacSHA256.
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Base64 of: [32-byte salt] + [12-byte IV] + [AES-GCM ciphertext + 16-byte tag]
     */
    fun encrypt(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(SALT_LENGTH_BYTES + IV_LENGTH_BYTES + ciphertextWithTag.size)
        System.arraycopy(salt, 0, combined, 0, SALT_LENGTH_BYTES)
        System.arraycopy(iv, 0, combined, SALT_LENGTH_BYTES, IV_LENGTH_BYTES)
        System.arraycopy(ciphertextWithTag, 0, combined, SALT_LENGTH_BYTES + IV_LENGTH_BYTES, ciphertextWithTag.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64 payload containing [salt][iv][ciphertext+tag] with master password.
     */
    fun decrypt(base64Payload: String, password: String): String {
        val raw = Base64.decode(base64Payload, Base64.NO_WRAP)
        require(raw.size > SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16) { "Invalid payload length" }

        val salt = raw.copyOfRange(0, SALT_LENGTH_BYTES)
        val iv = raw.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
        val ciphertextWithTag = raw.copyOfRange(SALT_LENGTH_BYTES + IV_LENGTH_BYTES, raw.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertextWithTag)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Hashes the user's Sync ID into a 64-char hex SHA-256 string for Cloudflare Worker KV lookup:
     * SHA-256("reword:v1:" + syncId)
     */
    fun hashId(syncId: String): String {
        val input = "reword:v1:${syncId.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
