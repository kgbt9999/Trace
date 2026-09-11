package com.moodlife.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moodlife.app.notifications.NotificationHelper
import com.moodlife.app.notifications.ReminderScheduler
import com.moodlife.app.workers.MonthlyBackupWorker
import com.moodlife.app.workers.WeeklyFolderBackupWorker
import com.moodlife.app.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MoodLifeApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        ReminderScheduler.scheduleAll(this)
        MonthlyBackupWorker.schedule(this)
        applicationScope.launch {
            WeeklyFolderBackupWorker.syncSchedule(this@MoodLifeApplication, settingsRepository)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
