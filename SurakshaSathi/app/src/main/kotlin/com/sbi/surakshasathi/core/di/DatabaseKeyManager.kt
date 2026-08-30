package com.sbi.surakshasathi.core.di

import android.content.Context
import android.util.Base64

/**
 * Manages the SQLCipher passphrase using the Android Keystore.
 *
 * The passphrase is generated once, encrypted, and stored in EncryptedSharedPreferences.
 * The encryption key never leaves the Keystore's secure hardware enclave.
 *
 * Security properties:
 * - AES-256-GCM key generated in Keystore hardware (TEE/StrongBox).
 * - Key is non-exportable and user-authentication-NOT-required (transparent to user).
 * - Passphrase is 32 bytes of SecureRandom, base64-encoded for SQLCipher.
 */
internal object DatabaseKeyManager {
    private const val KEYSTORE_ALIAS = "surakshasathi_db_key"
    private const val PREFS_NAME = "db_key_prefs"
    private const val PREFS_KEY = "db_passphrase"

    fun getOrCreatePassphrase(context: Context): CharArray {
        val encryptedPrefs =
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                getMasterKey(context),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        val existing = encryptedPrefs.getString(PREFS_KEY, null)
        if (existing != null) return existing.toCharArray()

        // First launch: generate a fresh passphrase
        val passphrase = generatePassphrase()
        encryptedPrefs.edit().putString(PREFS_KEY, String(passphrase)).apply()
        return passphrase
    }

    private fun generatePassphrase(): CharArray {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP).toCharArray()
    }

    private fun getMasterKey(context: Context): androidx.security.crypto.MasterKey =
        androidx.security.crypto.MasterKey.Builder(context, KEYSTORE_ALIAS)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
}
