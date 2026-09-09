package com.moodlife.app.integration.drive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for Phase 5 — Google Drive appDataFolder backup.
 * Mirror web `/api/gdrive/sync` intent: encrypted JSON export of Room DB.
 */
@Singleton
class GoogleDriveBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun isConfigured(): Boolean = false

    suspend fun uploadBackup(): Result<String> {
        // TODO Phase 5: Google Sign-In + Drive API appDataFolder
        return Result.failure(UnsupportedOperationException("Drive backup not implemented yet"))
    }

    suspend fun restoreBackup(): Result<Int> {
        return Result.failure(UnsupportedOperationException("Drive restore not implemented yet"))
    }
}
