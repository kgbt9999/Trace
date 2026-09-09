package com.moodlife.app.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup to JSON file — share via system picker (Google Drive, Files, etc.).
 * Full OAuth Drive API can be added later; SAF covers Drive folder saves.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonBackupExporter: JsonBackupExporter,
) {
    suspend fun isConnected(): Boolean = true

    suspend fun connect(activity: android.app.Activity): Boolean = true

    suspend fun uploadBackup(): BackupResult {
        return try {
            val exported = jsonBackupExporter.exportToCache()
            BackupResult(
                success = true,
                message = exported.file.name,
                bytesWritten = exported.bytes,
                filePath = exported.file.absolutePath,
            )
        } catch (e: Exception) {
            BackupResult(success = false, message = e.message ?: "Ошибка экспорта")
        }
    }

    suspend fun downloadBackup(): BackupResult =
        BackupResult(success = false, message = "Импорт JSON — в следующей версии")

    data class BackupResult(
        val success: Boolean,
        val message: String? = null,
        val bytesWritten: Long = 0,
        val filePath: String? = null,
    )
}
