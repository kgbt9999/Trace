package com.moodlife.app.domain

import com.moodlife.app.util.DateUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Time window for the Reports screen. Ranges are inclusive ISO dates. */
data class ReportPeriod(
    val kind: Kind,
    /** For [Kind.MONTH]: calendar year and 0-based month. */
    val year: Int,
    val month: Int,
    /** For [Kind.CUSTOM]. */
    val customFrom: String? = null,
    val customTo: String? = null,
) {
    enum class Kind(val days: Int?) { DAYS_7(7), DAYS_30(30), DAYS_90(90), MONTH(null), CUSTOM(null) }

    fun range(today: LocalDate): Pair<String, String> = when (kind) {
        Kind.MONTH -> DateUtils.monthRange(year, month)
        Kind.CUSTOM -> {
            val from = customFrom ?: today.minusDays(29).toString()
            val to = customTo ?: today.toString()
            if (from <= to) from to to else to to from
        }
        else -> today.minusDays((kind.days!! - 1).toLong()).toString() to today.toString()
    }

    fun label(today: LocalDate): String = when (kind) {
        Kind.MONTH -> "${DateUtils.MONTH_NAMES_RU[month]} $year"
        else -> {
            val (from, to) = range(today)
            "${shortDate(from)} — ${shortDate(to)}"
        }
    }

    /** Month used by the month-based exports: the month containing the end of the range. */
    fun exportMonth(today: LocalDate): Pair<Int, Int> {
        val end = LocalDate.parse(range(today).second)
        return end.year to end.monthValue - 1
    }

    fun dayCount(today: LocalDate): Long {
        val (from, to) = range(today)
        return ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) + 1
    }

    fun prevMonth(): ReportPeriod =
        if (month == 0) copy(year = year - 1, month = 11) else copy(month = month - 1)

    fun nextMonth(): ReportPeriod =
        if (month == 11) copy(year = year + 1, month = 0) else copy(month = month + 1)

    companion object {
        /** Default keeps the previous behaviour: the current calendar month. */
        fun currentMonth(today: LocalDate): ReportPeriod =
            ReportPeriod(Kind.MONTH, today.year, today.monthValue - 1)

        private fun shortDate(iso: String): String {
            val d = LocalDate.parse(iso)
            return "${d.dayOfMonth} ${DateUtils.MONTH_NAMES_RU[d.monthValue - 1].take(3).lowercase()} ${d.year}"
        }
    }
}
