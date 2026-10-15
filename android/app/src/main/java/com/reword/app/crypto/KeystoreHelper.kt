package com.reword.app.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages Hardware-backed Android Keystore keys for Biometric authentication.
 *
 * Used to securely seal/unseal the user's Master Password so that
 * BiometricPrompt can unlock the vault during Autofill requests.
 */
object KeystoreHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "reword_biometric_auth_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Initializes or gets the Hardware-backed AES key protected by Biometric auth.
     */
    fun getOrCreateBiometricKey(requireUserAuth: Boolean = true): SecretKey {
        if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

            if (requireUserAuth) {
                builder.setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        0, // 0 means authenticates every time
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                }
            }

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }
        return keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
    }

    /**
     * Creates a CryptoObject for Encrypting the master password during biometric setup.
     */
    fun createEncryptCryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricKey(requireUserAuth = true))
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Creates a CryptoObject for Decrypting the master password during autofill unlock.
     */
    fun createDecryptCryptoObject(iv: ByteArray): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateBiometricKey(requireUserAuth = true), spec)
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Removes biometric key on logout/reset.
     */
    fun deleteKey() {
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }
}
