package com.moodlife.app.integration.drive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for Phase 5 — Google Drive appDataFolder backup.
 * Mirror web `/api/gdrive/sync` intent: encrypted JSON export of Room DB.
 *
 * Not wired to UI; use [com.moodlife.app.data.backup.DriveBackupManager] (SAF share) instead.
 * Failures return a safe Result — never throws.
 */
@Singleton
class GoogleDriveBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun isConfigured(): Boolean = false

    suspend fun uploadBackup(): Result<String> =
        Result.failure(IllegalStateException(NOT_READY))

    suspend fun restoreBackup(): Result<Int> =
        Result.failure(IllegalStateException(NOT_READY))

    companion object {
        const val NOT_READY =
            "Резервная копия через Google Drive API пока недоступна. Используйте экспорт JSON в Настройках."
    }
}
