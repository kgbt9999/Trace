package com.moodlife.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moodlife.app.MainActivity
import com.moodlife.app.R

/**
 * Generic diary / medication reminders only — never include mood, med names, or notes.
 */
object NotificationHelper {

    const val CHANNEL_DIARY = "diary_reminders"
    const val CHANNEL_MEDS = "med_reminders"

    const val TYPE_DIARY = "diary"
    const val TYPE_MEDS = "meds"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIARY,
                context.getString(R.string.notif_channel_diary),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_diary_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDS,
                context.getString(R.string.notif_channel_meds),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_meds_desc)
            },
        )
    }

    fun showReminder(context: Context, type: String, requestCode: Int) {
        ensureChannels(context)
        val channel = if (type == TYPE_MEDS) CHANNEL_MEDS else CHANNEL_DIARY
        val title = if (type == TYPE_MEDS) {
            context.getString(R.string.notif_meds_title)
        } else {
            context.getString(R.string.notif_diary_title)
        }
        val text = if (type == TYPE_MEDS) {
            context.getString(R.string.notif_meds_text)
        } else {
            context.getString(R.string.notif_diary_text)
        }
        val launch = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }
}
