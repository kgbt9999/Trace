package com.moodlife.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moodlife.app.R
import com.moodlife.app.data.backup.JsonBackupExporter
import com.moodlife.app.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Weekly full JSON backup into a user-chosen SAF folder (tree URI).
 * Off by default — scheduled only when enabled + folder selected.
 */
@HiltWorker
class WeeklyFolderBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val jsonBackupExporter: JsonBackupExporter,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = settingsRepository.get(SettingsRepository.KEY_WEEKLY_BACKUP_ENABLED) == "true"
        if (!enabled) return Result.success()
        val treeUri = settingsRepository.get(SettingsRepository.KEY_WEEKLY_BACKUP_TREE_URI)
        if (treeUri.isNullOrBlank()) {
            settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_LAST, "no_folder")
            return Result.failure()
        }
        return try {
            val exported = jsonBackupExporter.exportToCache()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "Trace-backup-weekly-$stamp.json"
            val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(treeUri))
                ?: run {
                    settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_LAST, "bad_uri")
                    return Result.failure()
                }
            val dest = tree.createFile("application/json", fileName)
                ?: run {
                    settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_LAST, "create_fail")
                    return Result.retry()
                }
            applicationContext.contentResolver.openOutputStream(dest.uri)?.use { out ->
                exported.file.inputStream().use { input -> input.copyTo(out) }
            } ?: run {
                settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_LAST, "write_fail")
                return Result.retry()
            }
            val label = "$fileName · ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())}"
            settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_LAST, label)
            settingsRepository.set(SettingsRepository.KEY_BACKUP_LAST, dest.uri.toString())
            notifySaved(applicationContext, label)
            Result.success()
        } catch (_: Exception) {
            settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_LAST, "error")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "weekly_folder_trace_backup"
        private const val CHANNEL_ID = "trace_weekly_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyFolderBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        suspend fun syncSchedule(context: Context, settingsRepository: SettingsRepository) {
            val enabled = settingsRepository.get(SettingsRepository.KEY_WEEKLY_BACKUP_ENABLED) == "true"
            val uri = settingsRepository.get(SettingsRepository.KEY_WEEKLY_BACKUP_TREE_URI)
            if (enabled && !uri.isNullOrBlank()) schedule(context) else cancel(context)
        }

        fun takePersistablePermission(context: Context, treeUri: Uri) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: SecurityException) {
                // Some providers may not support persistable write; best-effort.
            }
        }

        private fun notifySaved(context: Context, pathLabel: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.backup_weekly_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.backup_weekly_title))
                .setContentText(context.getString(R.string.backup_weekly_text) + " " + pathLabel)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.backup_weekly_text) + "\n" + pathLabel),
                )
                .setAutoCancel(true)
                .build()
            nm.notify(4402, notification)
        }
    }
}
