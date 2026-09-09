package com.moodlife.app.domain

import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.util.MedsUtils
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Month summaries from the diary — not a diagnosis.
 *
 * Day mix: Hu 2022 (depression is the predominant phase).
 * Mixed days: same threshold as [ClinicalAlerts.detectMixed].
 * Sleep spread: Lam 2019 (IPSRT / social rhythms).
 * Med slots: Chatterton 2017 (adherence with psychoeducation).
 */
object MonthBurden {

    enum class DayKind { MIXED, DEPRESSED, ELEVATED, OTHER }

    data class Counts(
        val mixed: Int = 0,
        val depressed: Int = 0,
        val elevated: Int = 0,
        val other: Int = 0,
    ) {
        val total: Int get() = mixed + depressed + elevated + other
    }

    data class Adherence(val taken: Int = 0, val scheduled: Int = 0) {
        val percent: Int? get() = if (scheduled <= 0) null else ((taken * 100.0) / scheduled).roundToInt()
    }

    fun classify(depressed: Int, elevated: Int): DayKind = when {
        ClinicalAlerts.detectMixed(depressed, elevated) != null -> DayKind.MIXED
        depressed >= 2 -> DayKind.DEPRESSED
        elevated >= 2 -> DayKind.ELEVATED
        else -> DayKind.OTHER
    }

    fun counts(days: List<Pair<Int, Int>>): Counts {
        var mixed = 0
        var depressed = 0
        var elevated = 0
        var other = 0
        days.forEach { (dep, elev) ->
            when (classify(dep, elev)) {
                DayKind.MIXED -> mixed++
                DayKind.DEPRESSED -> depressed++
                DayKind.ELEVATED -> elevated++
                DayKind.OTHER -> other++
            }
        }
        return Counts(mixed, depressed, elevated, other)
    }

    fun bedtimeSpreadMinutes(times: List<String>): Int? {
        val minutes = times.mapNotNull(::parseHm).takeIf { it.size >= 4 } ?: return null
        val mean = minutes.average()
        val variance = minutes.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).roundToInt()
    }

    fun adherence(logs: List<MedicationLogEntity>, meds: List<MedicationEntity>): Adherence {
        val byId = meds.associateBy { it.id }
        var taken = 0
        var scheduled = 0
        logs.forEach { log ->
            val med = byId[log.medicationId] ?: return@forEach
            val slots = MedsUtils.parseIntakeTimes(med.intakeTimes)
            val timed = slots.filter { it != "by-scheme" }
            if (timed.isEmpty()) {
                scheduled += 1
                if (log.taken) taken += 1
            } else {
                scheduled += timed.size
                taken += timed.count { slot ->
                    MedsUtils.isSlotTaken(log.taken, log.slotsTaken, slot, timed)
                }
            }
        }
        return Adherence(taken, scheduled)
    }

    private fun parseHm(raw: String): Int? {
        val parts = raw.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].take(2).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
