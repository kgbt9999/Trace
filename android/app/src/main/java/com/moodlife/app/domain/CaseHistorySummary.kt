package com.moodlife.app.domain

import com.moodlife.app.data.local.entity.MoodEntryEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Diary-derived case-history summary rows (Pushkina/Kasyanov table layout).
 * Labels describe scale patterns only — not clinical episode diagnosis.
 */
object CaseHistorySummary {

    enum class Band {
        DIP, // спад
        RISE, // выраженный подъём
        MILD_RISE, // лёгкий подъём
        EVEN, // ровные
        MIXED, // смешанные
    }

    enum class Severity { NONE, MILD, MODERATE, SEVERE }

    data class Row(
        val periodLabel: String,
        val band: Band,
        val severity: Severity,
        val medications: String,
        val routine: String,
        val keyEvents: String,
    )

    data class Inputs(
        val entries: List<MoodEntryEntity>,
        /** date → "Name 100 мг" lines joined later */
        val medsByDate: Map<String, List<String>> = emptyMap(),
        val notesByDate: Map<String, String> = emptyMap(),
    )

    fun build(inputs: Inputs, minDays: Int = 5): List<Row> {
        val sorted = inputs.entries.sortedBy { it.date }
        if (sorted.isEmpty()) return emptyList()
        val segments = mutableListOf<List<MoodEntryEntity>>()
        var cur = mutableListOf(sorted.first())
        var curBand = bandOf(sorted.first())
        for (i in 1 until sorted.size) {
            val e = sorted[i]
            val b = bandOf(e)
            val gap = daysBetween(sorted[i - 1].date, e.date)
            if (b == curBand && gap <= 3) {
                cur.add(e)
            } else {
                segments.add(cur)
                cur = mutableListOf(e)
                curBand = b
            }
        }
        segments.add(cur)

        return segments.mapNotNull { seg ->
            if (seg.size < minDays && seg.size < sorted.size) {
                // Keep short segments only if they are the only data.
                if (segments.size > 1 && seg.size < 3) return@mapNotNull null
            }
            val band = majorityBand(seg)
            val sev = severityOf(seg, band)
            val from = seg.first().date
            val to = seg.last().date
            val meds = seg.asSequence()
                .flatMap { inputs.medsByDate[it.date].orEmpty().asSequence() }
                .map { it.substringBefore("—").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(4)
                .joinToString(" + ")
                .ifBlank { "—" }
            val routine = routineLabel(seg)
            val events = seg.asSequence()
                .mapNotNull { inputs.notesByDate[it.date]?.trim()?.takeIf { t -> t.isNotEmpty() } }
                .distinct()
                .take(2)
                .joinToString("; ")
                .ifBlank { "—" }
            Row(
                periodLabel = periodLabel(from, to),
                band = band,
                severity = sev,
                medications = meds.take(80),
                routine = routine,
                keyEvents = events.take(80),
            )
        }
    }

    fun bandLabel(band: Band): String = when (band) {
        Band.DIP -> "Спад"
        Band.RISE -> "Подъём"
        Band.MILD_RISE -> "Лёгкий подъём"
        Band.EVEN -> "Ровные"
        Band.MIXED -> "Смешанные"
    }

    fun severityLabel(sev: Severity): String = when (sev) {
        Severity.NONE -> "—"
        Severity.MILD -> "Лёгкая"
        Severity.MODERATE -> "Умеренная"
        Severity.SEVERE -> "Тяжёлая"
    }

    /** Android Color int for badge text / fill. */
    fun bandColorArgb(band: Band): Int = when (band) {
        Band.DIP -> 0xFF5B8DEF.toInt()
        Band.RISE -> 0xFFE57373.toInt()
        Band.MILD_RISE -> 0xFFE8A838.toInt()
        Band.EVEN -> 0xFF66BB6A.toInt()
        Band.MIXED -> 0xFF9B7EBD.toInt()
    }

    fun severityDotArgb(sev: Severity): Int = when (sev) {
        Severity.SEVERE -> 0xFFE57373.toInt()
        Severity.MODERATE -> 0xFFE8A838.toInt()
        Severity.MILD -> 0xFFFFD54F.toInt()
        Severity.NONE -> 0xFF66BB6A.toInt()
    }

    private fun bandOf(e: MoodEntryEntity): Band {
        val phase = e.episodePhase
        return when (phase) {
            "mixed" -> Band.MIXED
            "acute_depression", "prodromal_depression" -> Band.DIP
            "acute_mania" -> Band.RISE
            "prodromal_mania" -> Band.MILD_RISE
            "euthymic", "recovery" -> Band.EVEN
            else -> {
                val pol = e.elevated - e.depressed
                when {
                    e.depressed >= 2 && e.elevated >= 2 -> Band.MIXED
                    e.depressed >= 3 || pol <= -2 -> Band.DIP
                    e.elevated >= 3 || pol >= 3 -> Band.RISE
                    e.elevated >= 2 || pol >= 2 -> Band.MILD_RISE
                    else -> Band.EVEN
                }
            }
        }
    }

    private fun majorityBand(seg: List<MoodEntryEntity>): Band {
        val counts = seg.groupingBy { bandOf(it) }.eachCount()
        return counts.maxByOrNull { it.value }?.key ?: Band.EVEN
    }

    private fun severityOf(seg: List<MoodEntryEntity>, band: Band): Severity {
        if (band == Band.EVEN) return Severity.NONE
        val intensity = seg.map {
            maxOf(it.depressed, it.elevated, it.anxious, it.irritable)
        }.average()
        return when {
            intensity >= 4.0 -> Severity.SEVERE
            intensity >= 3.0 -> Severity.MODERATE
            intensity >= 1.5 -> Severity.MILD
            else -> Severity.NONE
        }
    }

    private fun routineLabel(seg: List<MoodEntryEntity>): String {
        val avg = seg.map { it.routineScore }.average()
        val sleep = seg.mapNotNull { it.sleepHours }.average().takeIf { !it.isNaN() }
        return when {
            avg >= 7 && (sleep == null || sleep in 6.5..9.5) -> "Стабильная"
            avg <= 3 || (sleep != null && (sleep < 5 || sleep > 11)) -> "Нарушена"
            else -> "Переменная"
        }
    }

    private fun periodLabel(fromIso: String, toIso: String): String {
        val from = LocalDate.parse(fromIso)
        val to = LocalDate.parse(toIso)
        val loc = Locale("ru")
        fun mon(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, loc)
            .replace(".", "")
            .replaceFirstChar { it.titlecase(loc) }
        return if (from.year == to.year) {
            if (from.month == to.month) {
                "${mon(from)} ${from.year}"
            } else {
                "${mon(from)}–${mon(to)} ${from.year}"
            }
        } else {
            "${mon(from)} ${from.year}–${mon(to)} ${to.year}"
        }
    }

    private fun daysBetween(a: String, b: String): Long =
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b))
}
