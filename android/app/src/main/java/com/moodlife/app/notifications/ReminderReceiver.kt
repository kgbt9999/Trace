package com.moodlife.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: NotificationHelper.TYPE_DIARY
        val code = intent.getIntExtra(EXTRA_CODE, type.hashCode())
        NotificationHelper.showReminder(context, type, code)
        // Reschedule next day for this same wall-clock time
        ReminderScheduler.rescheduleOne(context, type, code)
    }

    companion object {
        const val EXTRA_TYPE = "reminder_type"
        const val EXTRA_CODE = "reminder_code"
        const val ACTION = "com.moodlife.app.ACTION_REMINDER"
    }
}
