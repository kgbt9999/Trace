package com.moodlife.app.domain

import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.util.DateUtils
import org.json.JSONObject
import java.util.Locale

enum class PhysicalPeriod { DAY, WEEK, MONTH, QUARTER }

data class NutritionGoals(
    val kcal: Int? = null,
    val proteinG: Float? = null,
    val fatG: Float? = null,
    val carbsG: Float? = null,
) {
    companion object {
        fun parse(raw: String?): NutritionGoals {
            if (raw.isNullOrBlank()) return NutritionGoals()
            return try {
                val o = JSONObject(raw)
                NutritionGoals(
                    kcal = o.optInt("kcal").takeIf { it > 0 },
                    proteinG = o.optDouble("protein").toFloat().takeIf { it > 0f },
                    fatG = o.optDouble("fat").toFloat().takeIf { it > 0f },
                    carbsG = o.optDouble("carbs").toFloat().takeIf { it > 0f },
                )
            } catch (_: Exception) {
                NutritionGoals()
            }
        }

        fun serialize(g: NutritionGoals): String {
            val o = JSONObject()
            g.kcal?.let { o.put("kcal", it) }
            g.proteinG?.let { o.put("protein", it) }
            g.fatG?.let { o.put("fat", it) }
            g.carbsG?.let { o.put("carbs", it) }
            return o.toString()
        }
    }
}

data class PhysicalAggregate(
    val period: PhysicalPeriod,
    val anchorDate: String,
    val from: String,
    val to: String,
    val rangeLabel: String,
    val proteinG: Float?,
    val fatG: Float?,
    val carbsG: Float?,
    val caloriesEaten: Int?,
    val caloriesBurned: Int?,
    val goals: NutritionGoals,
    val heightCm: Float?,
    val weightKg: Float?,
    val cycleDay: Int?,
    val cyclePhaseLabel: String?,
    val steps: Int?,
    val cardioMinutes: Int?,
    val sleepHours: Float?,
    val bedtime: String?,
    val wakeTime: String?,
    val sleepQuality: Int?,
    val dayCount: Int,
) {
    val hasNutrition: Boolean =
        proteinG != null || fatG != null || carbsG != null || caloriesEaten != null
}

object PhysicalSummary {
    fun rangeFor(period: PhysicalPeriod, anchorIso: String): Pair<String, String> = when (period) {
        PhysicalPeriod.DAY -> anchorIso to anchorIso
        PhysicalPeriod.WEEK -> DateUtils.weekRangeContaining(anchorIso)
        PhysicalPeriod.MONTH -> DateUtils.monthRangeContaining(anchorIso)
        PhysicalPeriod.QUARTER -> DateUtils.quarterRangeContaining(anchorIso)
    }

    fun rangeLabel(period: PhysicalPeriod, from: String, to: String, anchor: String): String {
        return when (period) {
            PhysicalPeriod.DAY -> DateUtils.formatRu(DateUtils.parseIso(anchor))
            PhysicalPeriod.WEEK -> "${fmtShort(from)} — ${fmtShort(to)}"
            PhysicalPeriod.MONTH -> {
                val d = DateUtils.parseIso(anchor)
                "${DateUtils.MONTH_NAMES_RU[d.monthValue - 1]} ${d.year}"
            }
            PhysicalPeriod.QUARTER -> {
                val d = DateUtils.parseIso(anchor)
                val q = (d.monthValue - 1) / 3 + 1
                "Q$q ${d.year} · ${fmtShort(from)} — ${fmtShort(to)}"
            }
        }
    }

    private fun fmtShort(iso: String): String =
        DateUtils.parseIso(iso).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale("ru")))

    fun aggregate(
        period: PhysicalPeriod,
        anchorIso: String,
        days: List<ExternalHealthDayEntity>,
        moodEntries: List<MoodEntryEntity>,
        periodSetting: PeriodSettingEntity?,
        heightCm: Float?,
        goals: NutritionGoals,
    ): PhysicalAggregate {
        val (from, to) = rangeFor(period, anchorIso)
        val inRange = days.filter { it.date in from..to }
        val dayCount = DateUtils.daysBetween(from, to).toInt() + 1

        val nutrition = inRange.filter { it.kind == "nutrition" }
        val activity = inRange.filter { it.kind == "activity" }
        val sleepRows = inRange.filter { it.kind == "sleep" }
        val weightRows = inRange.filter { it.kind == "weight" && it.weightKg != null }

        fun avgFloat(values: List<Float>): Float? =
            values.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        fun sumInt(values: List<Int>): Int? =
            values.takeIf { it.isNotEmpty() }?.sum()

        val protein = when (period) {
            PhysicalPeriod.DAY -> nutrition.mapNotNull { it.proteinG }.firstOrNull()
            else -> avgFloat(nutrition.mapNotNull { it.proteinG })
        }
        val fat = when (period) {
            PhysicalPeriod.DAY -> nutrition.mapNotNull { it.fatG }.firstOrNull()
            else -> avgFloat(nutrition.mapNotNull { it.fatG })
        }
        val carbs = when (period) {
            PhysicalPeriod.DAY -> nutrition.mapNotNull { it.carbsG }.firstOrNull()
            else -> avgFloat(nutrition.mapNotNull { it.carbsG })
        }
        val eaten = when (period) {
            PhysicalPeriod.DAY -> nutrition.mapNotNull { it.calories }.firstOrNull()
            else -> avgFloat(nutrition.mapNotNull { it.calories?.toFloat() })?.toInt()
        }
        val burned = when (period) {
            PhysicalPeriod.DAY -> activity.mapNotNull { it.calories }.firstOrNull()
            else -> sumInt(activity.mapNotNull { it.calories })
        }
        val steps = when (period) {
            PhysicalPeriod.DAY -> activity.mapNotNull { it.steps }.firstOrNull()
            else -> sumInt(activity.mapNotNull { it.steps })
        }
        val cardio = when (period) {
            PhysicalPeriod.DAY -> activity.mapNotNull { it.activeMinutes }.firstOrNull()
            else -> sumInt(activity.mapNotNull { it.activeMinutes })
        }
        val sleepH = when (period) {
            PhysicalPeriod.DAY -> sleepRows.mapNotNull { it.sleepHours }.firstOrNull()
                ?: moodEntries.find { it.date == anchorIso }?.sleepHours
            else -> {
                val hc = sleepRows.mapNotNull { it.sleepHours }
                val mood = moodEntries.filter { it.date in from..to }.mapNotNull { it.sleepHours }
                avgFloat(hc.ifEmpty { mood })
            }
        }
        val moodForSleep = moodEntries.filter { it.date in from..to }
            .sortedByDescending { it.date }
        val bedtime = moodForSleep.firstOrNull { !it.sleepTime.isNullOrBlank() }?.sleepTime
        val wake = moodForSleep.firstOrNull { !it.wakeTime.isNullOrBlank() }?.wakeTime
        val sleepQ = sleepRows.mapNotNull { it.sleepQuality }.lastOrNull()
            ?: moodForSleep.firstOrNull { it.sleepQuality > 0 }?.sleepQuality

        val weight = weightRows.maxByOrNull { it.date }?.weightKg

        val cycleAnchor = if (period == PhysicalPeriod.DAY) anchorIso else to
        val cycle = periodSetting?.lastPeriodStart?.let { start ->
            CycleUtils.calcCyclePhase(
                start,
                periodSetting.cycleLength,
                periodSetting.periodLength,
                cycleAnchor,
                periodSetting.irregular,
            )
        }
        val phaseLabel = cycle?.let { c ->
            CycleUtils.PHASE_INFO[c.phase]?.label
        }

        return PhysicalAggregate(
            period = period,
            anchorDate = anchorIso,
            from = from,
            to = to,
            rangeLabel = rangeLabel(period, from, to, anchorIso),
            proteinG = protein,
            fatG = fat,
            carbsG = carbs,
            caloriesEaten = eaten,
            caloriesBurned = burned,
            goals = goals,
            heightCm = heightCm,
            weightKg = weight,
            cycleDay = cycle?.dayOfCycle,
            cyclePhaseLabel = phaseLabel,
            steps = steps,
            cardioMinutes = cardio,
            sleepHours = sleepH,
            bedtime = bedtime,
            wakeTime = wake,
            sleepQuality = sleepQ,
            dayCount = dayCount,
        )
    }
}
