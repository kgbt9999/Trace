package com.moodlife.app.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.moodlife.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for API keys and similar secrets (Android Keystore-backed MasterKey).
 * Migrates legacy plain values from Room settings on first read.
 */
@Singleton
class SecureSecretsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val prefs: SharedPreferences by lazy { createPrefs(context) }

    suspend fun getYandexWeatherApiKey(): String {
        val cached = prefs.getString(KEY_YANDEX, null)?.trim().orEmpty()
        if (cached.isNotEmpty()) return cached
        val legacy = settingsRepository.get(SettingsRepository.KEY_YANDEX_WEATHER_API_KEY)?.trim().orEmpty()
        if (legacy.isNotEmpty()) {
            setYandexWeatherApiKey(legacy)
            // Clear plaintext Room copy after successful migrate.
            settingsRepository.set(SettingsRepository.KEY_YANDEX_WEATHER_API_KEY, "")
            return legacy
        }
        return ""
    }

    fun setYandexWeatherApiKey(value: String) {
        prefs.edit().putString(KEY_YANDEX, value.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "trace_secure_secrets"
        private const val KEY_YANDEX = "yandex_weather_api_key"

        private fun createPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
