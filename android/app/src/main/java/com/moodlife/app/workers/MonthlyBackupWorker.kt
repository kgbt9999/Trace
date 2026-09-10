package com.moodlife.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
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
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Monthly JSON backup into shared Documents/Trace/backups/ (MediaStore),
 * so the file is visible in the system Files app. No cloud upload.
 */
@HiltWorker
class MonthlyBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val jsonBackupExporter: JsonBackupExporter,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val exported = jsonBackupExporter.exportToCache()
            val stamp = LocalDate.now().toString().take(7) // yyyy-MM
            val fileName = "trace-$stamp.json"
            val savedPath = writeToPublicDocuments(applicationContext, exported.file, fileName)
                ?: writeToAppDocumentsFallback(applicationContext, exported.file, fileName)
            settingsRepository.set(SettingsRepository.KEY_BACKUP_LAST, savedPath)
            notifySaved(applicationContext, savedPath)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "monthly_trace_backup"
        private const val CHANNEL_ID = "trace_backup"
        private const val RELATIVE_DIR = "Documents/Trace/backups"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyBackupWorker>(30, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Shared storage via MediaStore (API 29+). minSdk is 30 — no legacy WRITE permission.
         */
        private fun writeToPublicDocuments(context: Context, source: File, fileName: String): String? {
            return try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                // Remove previous month file with same display name if present (best-effort).
                resolver.delete(
                    collection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(fileName, "$RELATIVE_DIR/"),
                )
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val uri: Uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, done, null, null)
                }
                "$RELATIVE_DIR/$fileName"
            } catch (_: Exception) {
                null
            }
        }

        /** Fallback: app-specific external Documents (still local-only). */
        private fun writeToAppDocumentsFallback(context: Context, source: File, fileName: String): String {
            val docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val base = docs ?: context.filesDir
            val dir = File(base, "Trace/backups")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, fileName)
            source.copyTo(target, overwrite = true)
            return target.absolutePath
        }

        private fun notifySaved(context: Context, pathLabel: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.backup_monthly_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.backup_monthly_title))
                .setContentText(context.getString(R.string.backup_monthly_text) + " " + pathLabel)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.backup_monthly_text) + "\n" + pathLabel),
                )
                .setAutoCancel(true)
                .build()
            nm.notify(4401, notification)
        }
    }
}
