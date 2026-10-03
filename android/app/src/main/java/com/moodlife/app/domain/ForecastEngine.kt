package com.moodlife.app.domain

import com.moodlife.app.util.DateUtils

/** Port of web-reference/src/lib/forecast.ts */
object ForecastEngine {

    const val MIN_SAMPLE = 8
    private const val HORIZON = 2
    private const val RECENT_WINDOW_DAYS = 14

    /** Recent days count double: current state predicts the next week better than old history. */
    private const val RECENT_WEIGHT = 2.0

    data class ForecastProb(
        val elevation: Double?,
        val depression: Double?,
        val mixed: Double?,
        val sample: Int,
        val note: String,
    )

    data class HistDay(
        val date: String,
        val depressed: Int,
        val elevated: Int,
        val anxious: Int,
        val irritable: Int,
        val sleepQuality: Int,
        val sleepHours: Float?,
        val warningMania: Boolean,
        val warningDep: Boolean,
        val phase: CycleUtils.CyclePhase?,
        val precipType: String?,
        val tempMax: Float?,
    )

    data class HistDayInput(
        val date: String,
        val depressed: Int,
        val elevated: Int,
        val anxious: Int,
        val irritable: Int,
        val sleepQuality: Int,
        val sleepHours: Float?,
        val warningMania: Boolean = false,
        val warningDep: Boolean = false,
        val precipType: String? = null,
        val tempMax: Float? = null,
    )

    data class WeatherRisk(val factor: String, val severity: String, val description: String)

    data class ForecastDay(
        val date: String,
        val phase: CycleUtils.CycleMarkers?,
        val phaseInfo: CycleUtils.PhaseInfo?,
        val probabilities: ForecastProb,
        val recommendations: List<String>,
        val risks: List<WeatherRisk>,
        val score: Double?,
    )

    fun buildHistory(
        entries: List<HistDayInput>,
        lastPeriodStart: String?,
        cycleLength: Int,
        periodLength: Int,
        irregular: Boolean = false,
    ): List<HistDay> = entries.map { e ->
        val phase = lastPeriodStart?.let {
            CycleUtils.calcCyclePhase(it, cycleLength, periodLength, e.date, irregular).phase
        }
        HistDay(
            date = e.date,
            depressed = e.depressed,
            elevated = e.elevated,
            anxious = e.anxious,
            irritable = e.irritable,
            sleepQuality = e.sleepQuality,
            sleepHours = e.sleepHours,
            warningMania = e.warningMania,
            warningDep = e.warningDep,
            phase = phase,
            precipType = e.precipType,
            tempMax = e.tempMax,
        )
    }

    fun probabilitiesForDay(
        hist: List<HistDay>,
        phase: CycleUtils.CyclePhase?,
        precipType: String?,
        recent: List<HistDay>,
    ): ForecastProb {
        val elevOut: (HistDay) -> Boolean = { it.elevated >= 2 }
        val depOut: (HistDay) -> Boolean = { it.depressed >= 2 }
        val mixedOut: (HistDay) -> Boolean = { it.depressed >= 2 && it.elevated >= 2 }

        val recentMania = recent.any { it.warningMania || it.elevated >= 2 || (it.sleepHours != null && it.sleepHours < 6f) }
        val recentDep = recent.any { it.warningDep || it.depressed >= 2 }
        val lastWeeks = recentDays(hist, RECENT_WINDOW_DAYS)

        // Personal base rate (whole history) + recent rate are always available once the user
        // has MIN_SAMPLE days. Before, only prodrome-lag and cycle-phase parts existed; without
        // logged warning signs or cycle data every part was empty and the forecast stayed blank.
        val elev = blend(
            baseRate(hist, elevOut),
            baseRate(lastWeeks, elevOut).weighted(RECENT_WEIGHT),
            lagProb(hist, { it.warningMania }, elevOut),
            phaseRate(hist, phase, elevOut),
            if (recentMania) lagProb(hist, { d -> d.warningMania || (d.sleepHours != null && d.sleepHours < 6f) }, elevOut) else ProbPart(null, 0),
        )
        val dep = blend(
            baseRate(hist, depOut),
            baseRate(lastWeeks, depOut).weighted(RECENT_WEIGHT),
            lagProb(hist, { it.warningDep }, depOut),
            phaseRate(hist, phase, depOut),
            if (recentDep) lagProb(hist, { it.warningDep || it.depressed >= 2 }, depOut) else ProbPart(null, 0),
        )
        val mixed = blend(
            baseRate(hist, mixedOut),
            baseRate(lastWeeks, mixedOut).weighted(RECENT_WEIGHT),
            lagProb(hist, { it.warningMania && it.warningDep }, mixedOut),
            phaseRate(hist, phase, mixedOut),
        )

        val n = hist.size
        return ForecastProb(
            elevation = if (n < MIN_SAMPLE) null else elev.p,
            depression = if (n < MIN_SAMPLE) null else dep.p,
            mixed = if (n < MIN_SAMPLE) null else mixed.p,
            sample = n,
            note = if (n < MIN_SAMPLE) {
                "Мало данных: нужно минимум $MIN_SAMPLE дней с записями (сейчас $n). Это не прогноз эпизода."
            } else {
                "По вашим $n дням: частота состояний, последние $RECENT_WINDOW_DAYS дней, ранние признаки и фаза цикла. Не общее правило."
            },
        )
    }

    fun recommendationsFor(
        probs: ForecastProb,
        recent: List<HistDay>,
        phase: CycleUtils.CyclePhase?,
    ): List<String> {
        val rec = mutableListOf<String>()
        val elev = probs.elevation ?: 0.0
        val dep = probs.depression ?: 0.0
        val mixed = probs.mixed ?: 0.0
        val maniaNow = recent.any { it.warningMania || it.elevated >= 2 }

        if (probs.sample < MIN_SAMPLE) {
            rec.add("Пока мало записей — опирайтесь на сон и план действий, а не на цифры.")
        }
        if (mixed >= 0.35) rec.add("Если одновременно тянет вниз и вверх — свяжитесь с врачом.")
        if (elev >= 0.35 || maniaNow) {
            rec.add("Похоже на приближение подъёма: меньше стимуляции, не добавляйте дел.")
            rec.add("Сон — приоритет: лечь в привычное время.")
        } else if (dep >= 0.35) {
            rec.add("Не меняйте препараты самостоятельно.")
        }
        if (phase == CycleUtils.CyclePhase.LUTEAL || phase == CycleUtils.CyclePhase.MENSTRUAL) {
            rec.add("Фаза цикла может совпадать с более тяжёлыми днями — держите режим.")
        }
        if (rec.isEmpty()) rec.add("Держите привычный сон и не разгоняйте день «на всякий случай».")
        return rec
    }

    fun recentWindow(hist: List<HistDay>, today: String, beforeDate: String): List<HistDay> {
        val from = DateUtils.addDays(beforeDate, -2)
        return hist.filter { it.date >= from && it.date < beforeDate && it.date <= today }
    }

    fun buildSevenDayForecast(
        today: String,
        hist: List<HistDay>,
        lastPeriodStart: String?,
        cycleLength: Int,
        periodLength: Int,
        irregular: Boolean,
    ): List<ForecastDay> = buildForecast(
        today, hist, lastPeriodStart, cycleLength, periodLength, irregular, dayCount = 7,
    )

    /** Same scoring as seven-day; only the horizon length changes. */
    fun buildForecast(
        today: String,
        hist: List<HistDay>,
        lastPeriodStart: String?,
        cycleLength: Int,
        periodLength: Int,
        irregular: Boolean,
        dayCount: Int,
    ): List<ForecastDay> = (0 until dayCount.coerceIn(1, 14)).map { offset ->
        val date = DateUtils.addDays(today, offset.toLong())
        val markers = lastPeriodStart?.let {
            CycleUtils.calcCyclePhase(it, cycleLength, periodLength, date, irregular)
        }
        val recent = recentWindow(hist, today, date)
        val probs = probabilitiesForDay(hist, markers?.phase, null, recent)
        val worst = maxOf(probs.elevation ?: 0.0, probs.depression ?: 0.0, probs.mixed ?: 0.0)
        val score = if (probs.elevation == null && probs.depression == null) null
        else ((10 - worst * 10).coerceIn(0.0, 10.0) * 10).toInt() / 10.0
        ForecastDay(
            date = date,
            phase = markers,
            phaseInfo = markers?.phase?.let { CycleUtils.PHASE_INFO[it] },
            probabilities = probs,
            recommendations = recommendationsFor(probs, recent, markers?.phase),
            risks = emptyList(),
            score = score,
        )
    }

    private data class ProbPart(val p: Double?, val n: Int, val weight: Double = 1.0) {
        fun weighted(w: Double) = copy(weight = w)
    }

    private fun rate(nHit: Int, n: Int): Double? = if (n < MIN_SAMPLE) null else nHit.toDouble() / n

    private fun baseRate(days: List<HistDay>, outcome: (HistDay) -> Boolean): ProbPart =
        ProbPart(rate(days.count(outcome), days.size), days.size)

    /** Days within [windowDays] calendar days of the latest entry. */
    private fun recentDays(hist: List<HistDay>, windowDays: Int): List<HistDay> {
        val last = hist.maxOfOrNull { it.date } ?: return emptyList()
        val from = DateUtils.addDays(last, -(windowDays - 1).toLong())
        return hist.filter { it.date >= from }
    }

    /**
     * P(outcome within the next [HORIZON] calendar days | predicate today).
     * Uses dates, not list neighbours: with gaps in the diary the "next two entries" could be
     * weeks later and would inflate the hit rate.
     */
    private fun lagProb(hist: List<HistDay>, pred: (HistDay) -> Boolean, outcome: (HistDay) -> Boolean): ProbPart {
        val byDate = hist.associateBy { it.date }
        var n = 0
        var hits = 0
        for (day in hist) {
            if (!pred(day)) continue
            n++
            val hit = (1..HORIZON).any { offset ->
                byDate[DateUtils.addDays(day.date, offset.toLong())]?.let(outcome) == true
            }
            if (hit) hits++
        }
        return ProbPart(rate(hits, n), n)
    }

    private fun phaseRate(hist: List<HistDay>, phase: CycleUtils.CyclePhase?, outcome: (HistDay) -> Boolean): ProbPart {
        if (phase == null) return ProbPart(null, 0)
        val subset = hist.filter { it.phase == phase }
        return ProbPart(rate(subset.count(outcome), subset.size), subset.size)
    }

    private fun blend(vararg parts: ProbPart): ProbPart {
        val usable = parts.filter { it.p != null && it.n >= MIN_SAMPLE }
        if (usable.isEmpty()) return ProbPart(null, parts.sumOf { it.n })
        val totalWeight = usable.sumOf { it.n * it.weight }
        val p = usable.sumOf { (it.p ?: 0.0) * it.n * it.weight } / totalWeight
        return ProbPart(p, usable.sumOf { it.n })
    }
}
