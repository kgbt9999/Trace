package com.moodlife.app.domain

import com.moodlife.app.util.DateUtils

/** Port of web-reference/src/lib/forecast.ts */
object ForecastEngine {

    const val MIN_SAMPLE = 8
    private const val HORIZON = 2
    private const val RECENT_WINDOW_DAYS = 14

    // Part weights: the recent fortnight predicts the coming week better than old history;
    // a warning sign present right now outweighs both.
    private const val W_BASE = 1.0
    private const val W_RECENT = 2.0
    private const val W_PHASE = 1.0
    private const val W_SIGN = 3.0

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
        today: String? = null,
    ): ForecastProb {
        val elevOut: (HistDay) -> Boolean = { it.elevated >= 2 }
        val depOut: (HistDay) -> Boolean = { it.depressed >= 2 }
        val mixedOut: (HistDay) -> Boolean = { it.depressed >= 2 && it.elevated >= 2 }

        val maniaSign: (HistDay) -> Boolean = { d -> d.warningMania || (d.sleepHours != null && d.sleepHours < 6f) }
        val depSign: (HistDay) -> Boolean = { d -> d.warningDep || d.depressed >= 2 }
        val lastWeeks = recentDays(hist, RECENT_WINDOW_DAYS, today)

        // Base rate (all history) + recent rate are the prior. Conditional "after a sign, within
        // HORIZON days" rates apply only when such a sign is actually in the last days — they
        // used to be mixed in every day, inflating calm days. Fixed weights, not sample size:
        // otherwise 180 history days drowned a present warning sign.
        val elev = blend(
            baseRate(hist, elevOut).weighted(W_BASE),
            baseRate(lastWeeks, elevOut).weighted(W_RECENT),
            phaseRate(hist, phase, elevOut).weighted(W_PHASE),
            if (recent.any(maniaSign)) lagProb(hist, maniaSign, elevOut).weighted(W_SIGN) else NONE,
        )
        val dep = blend(
            baseRate(hist, depOut).weighted(W_BASE),
            baseRate(lastWeeks, depOut).weighted(W_RECENT),
            phaseRate(hist, phase, depOut).weighted(W_PHASE),
            if (recent.any(depSign)) lagProb(hist, depSign, depOut).weighted(W_SIGN) else NONE,
        )
        val mixedSign: (HistDay) -> Boolean = { maniaSign(it) && depSign(it) }
        val mixed = blend(
            baseRate(hist, mixedOut).weighted(W_BASE),
            baseRate(lastWeeks, mixedOut).weighted(W_RECENT),
            phaseRate(hist, phase, mixedOut).weighted(W_PHASE),
            if (recent.any(mixedSign)) lagProb(hist, mixedSign, mixedOut).weighted(W_SIGN) else NONE,
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
        val probs = probabilitiesForDay(hist, markers?.phase, null, recent, today)
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

    /** Days within the last [windowDays] calendar days before [today] (or the latest entry). */
    private fun recentDays(hist: List<HistDay>, windowDays: Int, today: String?): List<HistDay> {
        val end = today ?: hist.maxOfOrNull { it.date } ?: return emptyList()
        val from = DateUtils.addDays(end, -(windowDays - 1).toLong())
        return hist.filter { it.date >= from && it.date <= end }
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

    private val NONE = ProbPart(null, 0)

    /** Weighted mean of the parts that have at least [MIN_SAMPLE] observations. */
    private fun blend(vararg parts: ProbPart): ProbPart {
        val usable = parts.filter { it.p != null && it.n >= MIN_SAMPLE }
        if (usable.isEmpty()) return ProbPart(null, parts.sumOf { it.n })
        val totalWeight = usable.sumOf { it.weight }
        val p = usable.sumOf { (it.p ?: 0.0) * it.weight } / totalWeight
        return ProbPart(p, usable.sumOf { it.n })
    }
}
