package com.moodlife.app.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed passphrase for SQLCipher (Room). Never log the passphrase.
 */
@Singleton
class DatabasePassphraseStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { createPrefs(context) }

    /** Returns existing passphrase chars, or creates and persists a new random key. */
    fun getOrCreatePassphrase(): CharArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (!existing.isNullOrBlank()) {
            return existing.toCharArray()
        }
        val generated = generatePassphrase()
        prefs.edit().putString(KEY_PASSPHRASE, generated).commit()
        return generated.toCharArray()
    }

    companion object {
        private const val PREFS_NAME = "trace_db_passphrase"
        private const val KEY_PASSPHRASE = "sqlcipher_passphrase"
        private const val PASSPHRASE_HEX_LEN = 64

        private fun generatePassphrase(): String {
            val bytes = ByteArray(PASSPHRASE_HEX_LEN / 2)
            SecureRandom().nextBytes(bytes)
            return buildString(PASSPHRASE_HEX_LEN) {
                for (b in bytes) append("%02x".format(b))
            }
        }

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                // Keystore failure must not wipe user data; degraded private prefs.
                context.getSharedPreferences("${PREFS_NAME}_degraded", Context.MODE_PRIVATE)
            }
        }
    }
}
