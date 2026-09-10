package com.moodlife.app.data.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.repository.ExternalHealthRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.domain.HcSourceCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.reflect.KClass

/**
 * Health Connect hub — Guava-style: one connection, many source apps write into HC.
 * Syncs whatever permissions the user granted (partial OK).
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val externalHealthRepository: ExternalHealthRepository,
    private val settingsRepository: SettingsRepository,
    private val periodRepository: PeriodRepository,
) {
    private val permissionByType: Map<KClass<*>, String> = mapOf(
        StepsRecord::class to HealthPermission.getReadPermission(StepsRecord::class),
        DistanceRecord::class to HealthPermission.getReadPermission(DistanceRecord::class),
        ExerciseSessionRecord::class to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        ActiveCaloriesBurnedRecord::class to HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        TotalCaloriesBurnedRecord::class to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        SleepSessionRecord::class to HealthPermission.getReadPermission(SleepSessionRecord::class),
        WeightRecord::class to HealthPermission.getReadPermission(WeightRecord::class),
        BodyFatRecord::class to HealthPermission.getReadPermission(BodyFatRecord::class),
        NutritionRecord::class to HealthPermission.getReadPermission(NutritionRecord::class),
        MenstruationFlowRecord::class to HealthPermission.getReadPermission(MenstruationFlowRecord::class),
        MenstruationPeriodRecord::class to HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
        HeartRateRecord::class to HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    val requiredPermissions: Set<String> get() = permissionByType.values.toSet()

    suspend fun isAvailable(): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    } catch (_: Exception) {
        false
    }

    fun openHealthConnectInstallOrManage(): Boolean {
        val intents = listOf(
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
            ),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // try next
            } catch (_: Exception) {
                // try next
            }
        }
        return false
    }

    suspend fun grantedPermissions(): Set<String> {
        if (!isAvailable()) return emptySet()
        return try {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** At least one of our read permissions — Guava pattern (partial grants OK). */
    suspend fun hasPermissions(): Boolean {
        val granted = grantedPermissions()
        return requiredPermissions.any { it in granted }
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = grantedPermissions()
        return requiredPermissions.all { it in granted }
    }

    /**
     * Launches the Health Connect permission UI via [ComponentActivity]'s
     * [androidx.activity.result.ActivityResultRegistry]. Prefer the Compose
     * [PermissionController.createRequestPermissionResultContract] launcher in UI;
     * this path covers Activity/ViewModel callers.
     */
    suspend fun requestPermissions(activity: ComponentActivity): Boolean {
        if (!isAvailable()) return false
        if (hasPermissions()) return true
        return suspendCancellableCoroutine { cont ->
            val key = "hc_perm_${System.currentTimeMillis()}"
            lateinit var launcher: androidx.activity.result.ActivityResultLauncher<Set<String>>
            launcher = activity.activityResultRegistry.register(
                key,
                androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
            ) { granted ->
                try {
                    launcher.unregister()
                } catch (_: Exception) {
                }
                if (cont.isActive) {
                    cont.resume(granted.any { it in requiredPermissions })
                }
            }
            cont.invokeOnCancellation {
                try {
                    launcher.unregister()
                } catch (_: Exception) {
                }
            }
            try {
                launcher.launch(requiredPermissions)
            } catch (_: Exception) {
                try {
                    launcher.unregister()
                } catch (_: Exception) {
                }
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    suspend fun loadStatus(): HcStatus {
        val available = isAvailable()
        val granted = if (available) grantedPermissions() else emptySet()
        val grantedOurs = requiredPermissions.count { it in granted }
        val lastAt = settingsRepository.get(SettingsRepository.KEY_HC_LAST_SYNC)?.toLongOrNull()
        val lastRows = settingsRepository.get(SettingsRepository.KEY_HC_LAST_ROWS)?.toIntOrNull() ?: 0
        val originsRaw = settingsRepository.get(SettingsRepository.KEY_HC_LAST_ORIGINS).orEmpty()
        val origins = parseOrigins(originsRaw)
        return HcStatus(
            available = available,
            grantedCount = grantedOurs,
            requiredCount = requiredPermissions.size,
            hasAnyPermission = grantedOurs > 0,
            hasAllPermissions = grantedOurs == requiredPermissions.size,
            lastSyncAtMs = lastAt,
            lastRows = lastRows,
            lastOriginLabels = origins,
        )
    }

    suspend fun syncRecentDays(daysBack: Int = 30): SyncResult {
        if (!isAvailable()) {
            return SyncResult(skipped = true, reason = "Health Connect не установлен")
        }
        val granted = grantedPermissions()
        if (requiredPermissions.none { it in granted }) {
            return SyncResult(skipped = true, reason = "Нет разрешений Health Connect")
        }
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val start = end.minus(daysBack.toLong(), ChronoUnit.DAYS)
            val filter = TimeRangeFilter.between(start, end)
            val origins = linkedSetOf<String>()
            var rows = 0

            fun noteOrigin(packageName: String?) {
                val pkg = packageName?.takeIf { it.isNotBlank() } ?: return
                origins += HcSourceCatalog.labelForPackage(pkg)
            }

            suspend fun <T : androidx.health.connect.client.records.Record> canRead(type: KClass<T>): Boolean {
                val perm = permissionByType[type] ?: return false
                return perm in granted
            }

            if (canRead(StepsRecord::class)) {
                val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRangeFilter = filter))
                val stepsByDate = mutableMapOf<String, Int>()
                for (record in steps.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    val date = isoDate(record.startTime)
                    stepsByDate[date] = (stepsByDate[date] ?: 0) + record.count.toInt()
                }
                for ((date, count) in stepsByDate) {
                    upsertActivity(date, steps = count)
                    rows++
                }
            }

            if (canRead(DistanceRecord::class)) {
                val distance = client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRangeFilter = filter))
                val byDate = mutableMapOf<String, Double>()
                for (record in distance.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    val date = isoDate(record.startTime)
                    byDate[date] = (byDate[date] ?: 0.0) + record.distance.inMeters
                }
                for ((date, meters) in byDate) {
                    upsertActivity(date, distanceM = meters.toFloat())
                    rows++
                }
            }

            if (canRead(ExerciseSessionRecord::class)) {
                val sessions = client.readRecords(
                    ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = filter),
                )
                val minutesByDate = mutableMapOf<String, Int>()
                for (record in sessions.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    val date = isoDate(record.startTime)
                    val mins = ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt().coerceAtLeast(0)
                    minutesByDate[date] = (minutesByDate[date] ?: 0) + mins
                }
                for ((date, mins) in minutesByDate) {
                    upsertActivity(date, activeMinutes = mins)
                    rows++
                }
            }

            if (canRead(ActiveCaloriesBurnedRecord::class) || canRead(TotalCaloriesBurnedRecord::class)) {
                val calByDate = mutableMapOf<String, Double>()
                if (canRead(ActiveCaloriesBurnedRecord::class)) {
                    val active = client.readRecords(
                        ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = filter),
                    )
                    for (record in active.records) {
                        noteOrigin(record.metadata.dataOrigin.packageName)
                        val date = isoDate(record.startTime)
                        calByDate[date] = (calByDate[date] ?: 0.0) + record.energy.inKilocalories
                    }
                }
                if (canRead(TotalCaloriesBurnedRecord::class) && calByDate.isEmpty()) {
                    val total = client.readRecords(
                        ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRangeFilter = filter),
                    )
                    for (record in total.records) {
                        noteOrigin(record.metadata.dataOrigin.packageName)
                        val date = isoDate(record.startTime)
                        calByDate[date] = (calByDate[date] ?: 0.0) + record.energy.inKilocalories
                    }
                }
                for ((date, kcal) in calByDate) {
                    upsertActivity(date, calories = kcal.toInt())
                    rows++
                }
            }

            if (canRead(SleepSessionRecord::class)) {
                val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = filter))
                val hoursByDate = mutableMapOf<String, Float>()
                for (record in sleep.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    val date = isoDate(record.endTime)
                    val hours = ChronoUnit.MINUTES.between(record.startTime, record.endTime) / 60f
                    hoursByDate[date] = (hoursByDate[date] ?: 0f) + hours
                }
                for ((date, hours) in hoursByDate) {
                    externalHealthRepository.upsertMerged(
                        ExternalHealthDayEntity(
                            id = "",
                            date = date,
                            source = SOURCE,
                            kind = "sleep",
                            sleepHours = hours,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                    )
                    rows++
                }
            }

            if (canRead(WeightRecord::class) || canRead(BodyFatRecord::class)) {
                val weightByDate = mutableMapOf<String, Float>()
                val fatByDate = mutableMapOf<String, Float>()
                if (canRead(WeightRecord::class)) {
                    val weights = client.readRecords(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = filter))
                    for (record in weights.records) {
                        noteOrigin(record.metadata.dataOrigin.packageName)
                        weightByDate[isoDate(record.time)] = record.weight.inKilograms.toFloat()
                    }
                }
                if (canRead(BodyFatRecord::class)) {
                    val fats = client.readRecords(ReadRecordsRequest(BodyFatRecord::class, timeRangeFilter = filter))
                    for (record in fats.records) {
                        noteOrigin(record.metadata.dataOrigin.packageName)
                        fatByDate[isoDate(record.time)] = record.percentage.value.toFloat()
                    }
                }
                val dates = weightByDate.keys + fatByDate.keys
                for (date in dates) {
                    externalHealthRepository.upsertMerged(
                        ExternalHealthDayEntity(
                            id = "",
                            date = date,
                            source = SOURCE,
                            kind = "weight",
                            weightKg = weightByDate[date],
                            bodyFatPct = fatByDate[date],
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                    )
                    rows++
                }
            }

            if (canRead(NutritionRecord::class)) {
                val nutrition = client.readRecords(ReadRecordsRequest(NutritionRecord::class, timeRangeFilter = filter))
                data class Macros(var kcal: Double = 0.0, var p: Double = 0.0, var c: Double = 0.0, var f: Double = 0.0)
                val byDate = mutableMapOf<String, Macros>()
                for (record in nutrition.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    val date = isoDate(record.startTime)
                    val m = byDate.getOrPut(date) { Macros() }
                    m.kcal += record.energy?.inKilocalories ?: 0.0
                    m.p += record.protein?.inGrams ?: 0.0
                    m.c += record.totalCarbohydrate?.inGrams ?: 0.0
                    m.f += record.totalFat?.inGrams ?: 0.0
                }
                for ((date, m) in byDate) {
                    externalHealthRepository.upsertMerged(
                        ExternalHealthDayEntity(
                            id = "",
                            date = date,
                            source = SOURCE,
                            kind = "nutrition",
                            calories = m.kcal.toInt().takeIf { it > 0 },
                            proteinG = m.p.toFloat().takeIf { it > 0f },
                            carbsG = m.c.toFloat().takeIf { it > 0f },
                            fatG = m.f.toFloat().takeIf { it > 0f },
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                    )
                    rows++
                }
            }

            if (canRead(MenstruationFlowRecord::class)) {
                val flow = client.readRecords(
                    ReadRecordsRequest(MenstruationFlowRecord::class, timeRangeFilter = filter),
                )
                for (record in flow.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    val date = isoDate(record.time)
                    val level = when (record.flow) {
                        MenstruationFlowRecord.FLOW_LIGHT -> 1
                        MenstruationFlowRecord.FLOW_MEDIUM -> 2
                        MenstruationFlowRecord.FLOW_HEAVY -> 3
                        else -> 1
                    }
                    externalHealthRepository.upsertMerged(
                        ExternalHealthDayEntity(
                            id = "",
                            date = date,
                            source = SOURCE,
                            kind = "period",
                            flowLevel = level,
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                    )
                    rows++
                }
            }

            if (canRead(MenstruationPeriodRecord::class)) {
                val periods = client.readRecords(
                    ReadRecordsRequest(MenstruationPeriodRecord::class, timeRangeFilter = filter),
                )
                val latestStart = periods.records.maxByOrNull { it.startTime }?.startTime
                if (latestStart != null) {
                    noteOrigin(periods.records.maxByOrNull { it.startTime }?.metadata?.dataOrigin?.packageName)
                    val iso = isoDate(latestStart)
                    try {
                        periodRepository.update(lastPeriodStart = iso)
                    } catch (_: Exception) {
                        // cycle settings optional
                    }
                }
            }

            if (canRead(HeartRateRecord::class)) {
                val hr = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = filter))
                val avgByDate = mutableMapOf<String, Pair<Long, Int>>()
                for (record in hr.records) {
                    noteOrigin(record.metadata.dataOrigin.packageName)
                    for (sample in record.samples) {
                        val date = isoDate(sample.time)
                        val (sum, n) = avgByDate[date] ?: (0L to 0)
                        avgByDate[date] = (sum + sample.beatsPerMinute) to (n + 1)
                    }
                }
                for ((date, sn) in avgByDate) {
                    val (sum, n) = sn
                    if (n == 0) continue
                    val avg = (sum / n).toInt()
                    externalHealthRepository.upsertMerged(
                        ExternalHealthDayEntity(
                            id = "",
                            date = date,
                            source = SOURCE,
                            kind = "activity",
                            note = "ЧСС ср. $avg",
                            createdAt = 0,
                            updatedAt = 0,
                        ),
                    )
                    rows++
                }
            }

            val originList = origins.toList().sorted()
            settingsRepository.set(SettingsRepository.KEY_HC_LAST_SYNC, System.currentTimeMillis().toString())
            settingsRepository.set(SettingsRepository.KEY_HC_LAST_ROWS, rows.toString())
            settingsRepository.set(SettingsRepository.KEY_HC_LAST_ORIGINS, JSONArray(originList).toString())

            SyncResult(rowsWritten = rows, originLabels = originList)
        } catch (e: Exception) {
            SyncResult(skipped = true, reason = e.message ?: "Ошибка синхронизации")
        }
    }

    private suspend fun upsertActivity(
        date: String,
        steps: Int? = null,
        activeMinutes: Int? = null,
        calories: Int? = null,
        distanceM: Float? = null,
    ) {
        externalHealthRepository.upsertMerged(
            ExternalHealthDayEntity(
                id = "",
                date = date,
                source = SOURCE,
                kind = "activity",
                steps = steps,
                activeMinutes = activeMinutes,
                calories = calories,
                distanceM = distanceM,
                createdAt = 0,
                updatedAt = 0,
            ),
        )
    }

    private fun isoDate(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun parseOrigins(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class HcStatus(
        val available: Boolean,
        val grantedCount: Int,
        val requiredCount: Int,
        val hasAnyPermission: Boolean,
        val hasAllPermissions: Boolean,
        val lastSyncAtMs: Long?,
        val lastRows: Int,
        val lastOriginLabels: List<String>,
    )

    data class SyncResult(
        val skipped: Boolean = false,
        val rowsWritten: Int = 0,
        val reason: String? = null,
        val originLabels: List<String> = emptyList(),
    )

    companion object {
        const val SOURCE = "health-connect"
    }
}
