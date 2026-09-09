package com.moodlife.app

import android.app.Application
import com.moodlife.app.notifications.NotificationHelper
import com.moodlife.app.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MoodLifeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        ReminderScheduler.scheduleAll(this)
    }
}
