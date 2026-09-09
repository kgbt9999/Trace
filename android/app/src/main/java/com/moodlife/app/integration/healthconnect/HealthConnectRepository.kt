package com.moodlife.app.integration.healthconnect

import com.moodlife.app.data.health.HealthConnectManager
import javax.inject.Inject
import javax.inject.Singleton

/** Thin facade — real logic lives in [HealthConnectManager]. */
@Singleton
class HealthConnectRepository @Inject constructor(
    private val manager: HealthConnectManager,
) {
    suspend fun isAvailable(): Boolean = manager.isAvailable()

    suspend fun syncLatestDays(days: Int = 14): Result<Int> {
        val result = manager.syncRecentDays(days)
        return if (result.skipped) {
            Result.failure(IllegalStateException(result.reason ?: "skipped"))
        } else {
            Result.success(result.rowsWritten)
        }
    }
}
