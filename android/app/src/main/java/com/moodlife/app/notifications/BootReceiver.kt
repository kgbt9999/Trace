package com.moodlife.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arm reminders after reboot (generic times only). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pending = goAsync()
            try {
                ReminderScheduler.scheduleAll(context)
            } catch (_: Exception) {
                // Boot must not crash the app process.
            } finally {
                pending.finish()
            }
        }
    }
}
