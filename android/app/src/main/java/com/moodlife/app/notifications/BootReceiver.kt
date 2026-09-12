package com.moodlife.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager

/** Re-arm reminders after reboot / unlock (generic times only). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager != null && !userManager.isUserUnlocked) {
            // Credential-encrypted storage / alarms unavailable until unlock.
            return
        }
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
