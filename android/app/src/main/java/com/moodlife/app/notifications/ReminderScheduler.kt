package com.moodlife.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules generic daily reminders via AlarmManager.
 * Prefs are read from SharedPreferences mirror written by ReminderPrefs (no sensitive payload).
 *
 * Med reminders follow the **current catalog scheme times** configured in Settings → notifications
 * (HH:mm list), not per-day journal overrides («только этот день»).
 */
object ReminderScheduler {

    private const val PREFS = "trace_reminders"
    private const val KEY_DIARY_ENABLED = "diary_enabled"
    private const val KEY_DIARY_HOUR = "diary_hour"
    private const val KEY_DIARY_MINUTE = "diary_minute"
    private const val KEY_MEDS_ENABLED = "meds_enabled"
    private const val KEY_MEDS_TIMES = "meds_times" // "HH:mm,HH:mm"

    fun saveDiary(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DIARY_ENABLED, enabled)
            .putInt(KEY_DIARY_HOUR, hour)
            .putInt(KEY_DIARY_MINUTE, minute)
            .apply()
        scheduleAll(context)
    }

    fun saveMeds(context: Context, enabled: Boolean, timesHhMm: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MEDS_ENABLED, enabled)
            .putString(KEY_MEDS_TIMES, timesHhMm.joinToString(","))
            .apply()
        scheduleAll(context)
    }

    fun readDiary(context: Context): Triple<Boolean, Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            p.getBoolean(KEY_DIARY_ENABLED, false),
            p.getInt(KEY_DIARY_HOUR, 21),
            p.getInt(KEY_DIARY_MINUTE, 0),
        )
    }

    fun readMeds(context: Context): Pair<Boolean, List<String>> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val times = p.getString(KEY_MEDS_TIMES, "09:00,21:00")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }
            .orEmpty()
            .ifEmpty { listOf("09:00", "21:00") }
        return p.getBoolean(KEY_MEDS_ENABLED, false) to times
    }

    fun scheduleAll(context: Context) {
        NotificationHelper.ensureChannels(context)
        cancelAll(context)
        val (diaryOn, hour, minute) = readDiary(context)
        if (diaryOn) {
            scheduleAt(context, NotificationHelper.TYPE_DIARY, 1001, hour, minute)
        }
        val (medsOn, times) = readMeds(context)
        if (medsOn) {
            times.forEachIndexed { index, hhmm ->
                val parts = hhmm.split(':')
                val h = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachIndexed
                val m = parts.getOrNull(1)?.toIntOrNull() ?: return@forEachIndexed
                scheduleAt(context, NotificationHelper.TYPE_MEDS, 2000 + index, h, m)
            }
        }
    }

    fun rescheduleOne(context: Context, type: String, code: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Next occurrence is already computed relative to now+1 day by scheduleAt if triggered;
        // re-read prefs and set next day.
        when (type) {
            NotificationHelper.TYPE_DIARY -> {
                val (on, h, m) = readDiary(context)
                if (on) scheduleAt(context, type, code, h, m, forceNextDay = true)
            }
            NotificationHelper.TYPE_MEDS -> {
                val (on, times) = readMeds(context)
                if (!on) return
                val idx = code - 2000
                val hhmm = times.getOrNull(idx) ?: return
                val parts = hhmm.split(':')
                val h = parts.getOrNull(0)?.toIntOrNull() ?: return
                val m = parts.getOrNull(1)?.toIntOrNull() ?: return
                scheduleAt(context, type, code, h, m, forceNextDay = true)
            }
        }
        // silence unused
        am
    }

    private fun cancelAll(context: Context) {
        for (code in 1001..1001) cancel(context, code)
        for (code in 2000..2010) cancel(context, code)
    }

    private fun cancel(context: Context, code: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, NotificationHelper.TYPE_DIARY, code))
        am.cancel(pending(context, NotificationHelper.TYPE_MEDS, code))
    }

    private fun scheduleAt(
        context: Context,
        type: String,
        code: Int,
        hour: Int,
        minute: Int,
        forceNextDay: Boolean = false,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceNextDay || timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val pi = pending(context, type, code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }.onFailure {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }
    }

    private fun pending(context: Context, type: String, code: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION
            putExtra(ReminderReceiver.EXTRA_TYPE, type)
            putExtra(ReminderReceiver.EXTRA_CODE, code)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
