package com.moodlife.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
 * Monthly JSON backup into **app-private** filesDir/backups only.
 * Never writes medical JSON to shared MediaStore / public Documents.
 * User-visible cloud/folder copies remain explicit (share / weekly SAF).
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
            val savedPath = writeToAppPrivate(applicationContext, exported.file, fileName)
            settingsRepository.set(SettingsRepository.KEY_BACKUP_LAST, "private:$fileName")
            notifySaved(applicationContext, savedPath)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "monthly_trace_backup"
        private const val CHANNEL_ID = "trace_backup"

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
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** App-private only — not visible to other apps / Files "Documents". */
        private fun writeToAppPrivate(context: Context, source: File, fileName: String): String {
            val dir = File(context.filesDir, "backups")
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
            // Do not put absolute paths with medical filenames into the shade text.
            val safeLabel = pathLabel.substringAfterLast(File.separatorChar)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.backup_monthly_title))
                .setContentText(context.getString(R.string.backup_monthly_text) + " " + safeLabel)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.backup_monthly_text) + "\n" + safeLabel),
                )
                .setAutoCancel(true)
                .build()
            nm.notify(4401, notification)
        }
    }
}
