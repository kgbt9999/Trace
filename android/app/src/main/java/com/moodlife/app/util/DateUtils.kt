package com.moodlife.app.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateUtils {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val MONTH_NAMES_RU = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    private val isoDateRe = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun todayIso(): String = LocalDate.now().format(isoFormatter)

    fun isValidIsoDate(value: String?): Boolean {
        if (value == null || !isoDateRe.matches(value)) return false
        return try {
            parseIso(value).format(isoFormatter) == value
        } catch (_: Exception) {
            false
        }
    }

    fun parseIso(date: String): LocalDate = LocalDate.parse(date, isoFormatter)

    fun formatRu(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))

    fun formatIso(date: LocalDate): String = date.format(isoFormatter)

    fun addDays(iso: String, days: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        parseIso(iso).plusDays(days).format(isoFormatter)

    fun daysBetween(a: String, b: String): Long =
        ChronoUnit.DAYS.between(parseIso(a), parseIso(b))

    fun monthRange(year: Int, month: Int): Pair<String, String> {
        val first = LocalDate.of(year, month + 1, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        return first.format(isoFormatter) to last.format(isoFormatter)
    }

    /** Inclusive ISO range for a calendar week (Mon–Sun) containing [iso]. */
    fun weekRangeContaining(iso: String): Pair<String, String> {
        val d = parseIso(iso)
        val monday = d.minusDays(((d.dayOfWeek.value + 6) % 7).toLong())
        val sunday = monday.plusDays(6)
        return monday.format(isoFormatter) to sunday.format(isoFormatter)
    }

    /** Inclusive ISO range for a calendar quarter (Q1–Q4) containing [iso]. */
    fun quarterRangeContaining(iso: String): Pair<String, String> {
        val d = parseIso(iso)
        val qStartMonth = ((d.monthValue - 1) / 3) * 3 + 1
        val first = LocalDate.of(d.year, qStartMonth, 1)
        val last = first.plusMonths(2).withDayOfMonth(first.plusMonths(2).lengthOfMonth())
        return first.format(isoFormatter) to last.format(isoFormatter)
    }

    fun monthRangeContaining(iso: String): Pair<String, String> {
        val d = parseIso(iso)
        return monthRange(d.year, d.monthValue - 1)
    }

    /** Monday-first 6×7 grid; null = padding cell. */
    fun monthMatrix(year: Int, month: Int): List<List<String?>> {
        val first = LocalDate.of(year, month + 1, 1)
        val daysInMonth = first.lengthOfMonth()
        val startOffset = (first.dayOfWeek.value + 6) % 7
        val cells = mutableListOf<String?>()
        repeat(startOffset) { cells.add(null) }
        for (d in 1..daysInMonth) {
            cells.add(LocalDate.of(year, month + 1, d).format(isoFormatter))
        }
        while (cells.size % 7 != 0) cells.add(null)
        while (cells.size < 42) cells.add(null)
        return cells.chunked(7)
    }
}
