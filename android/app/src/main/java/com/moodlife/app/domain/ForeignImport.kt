package com.moodlife.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns exports of other diary apps into per-day records Trace can merge.
 *
 * Scales are only filled where the source's meaning is known (eMoods: format read from the
 * app itself — "DATE (YYYY-MM-DD)", DEPRESSED/ELEVATED/IRRITABILITY/ANXIETY on 0–3, SLEEP hours;
 * generic files: columns whose names clearly match). Everything else becomes the day note, so
 * an import never silently drops data and never invents scale values.
 */
object ForeignImport {

    data class Day(
        val date: String,
        /** Trace axis key → value on Trace's scale (0–5 for mood axes). */
        val values: Map<String, Int> = emptyMap(),
        val sleepHours: Float? = null,
        val notes: List<String> = emptyList(),
        val bedTime: String? = null,
        val wakeTime: String? = null,
    )

    data class Result(val format: String, val days: List<Day>)

    // ---------- detection ----------

    fun parse(fileName: String, text: String): Result? {
        val t = text.trim().removePrefix("\uFEFF")
        if (t.isEmpty()) return null
        return if (t.startsWith("{") || t.startsWith("[")) parseJson(t) else parseCsv(fileName, t)
    }

    // ---------- CSV ----------

    private fun parseCsv(fileName: String, text: String): Result? {
        val rows = Csv.parse(text)
        if (rows.size < 2) return null
        if (rows.first().firstOrNull()?.trim() == "Id" && rows.first().any { it.trim() == "Hours" }) {
            return Result("Sleep as Android", sleepAsAndroid(rows))
        }
        val header = rows.first().map { it.trim() }
        val norm = header.map { it.lowercase() }
        val body = rows.drop(1).filter { r -> r.any { it.isNotBlank() } }
        return when {
            "date (yyyy-mm-dd)" in norm || (norm.contains("depressed") && norm.contains("elevated") && norm.contains("irritability")) ->
                Result("eMoods", eMoods(header, body))
            norm.containsAll(listOf("full_date", "mood")) ->
                Result("Daylio", daylio(header, body))
            norm.contains("rating/amount") && norm.contains("category") ->
                Result("Bearable", bearable(header, body))
            else -> generic(header, body)?.let { Result("CSV ($fileName)", it) }
        }
    }

    /**
     * eMoods levels: 0 none · 1 mild · 2 moderate · 3 severe. Trace 0–5: 0 нет · 1 слегка ·
     * 3 мешало делам · 4 сильно (moderate in eMoods already means daily life is affected).
     */
    private val EMOODS_LEVEL = mapOf(0 to 0, 1 to 1, 2 to 3, 3 to 4)

    private fun eMoods(header: List<String>, rows: List<List<String>>): List<Day> {
        val idx = header.withIndex().associate { (i, h) -> h.trim().uppercase() to i }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { idx[it] }
        val dateCol = col("DATE (YYYY-MM-DD)", "DATE") ?: return emptyList()
        val axes = listOf(
            "depressed" to col("DEPRESSED"),
            "elevated" to col("ELEVATED"),
            "irritable" to col("IRRITABILITY"),
            "anxious" to col("ANXIETY"),
        )
        val sleepCol = col("SLEEP")
        val known = setOfNotNull(dateCol, sleepCol) + axes.mapNotNull { it.second }
        return rows.mapNotNull { r ->
            val date = date(r.getOrNull(dateCol)) ?: return@mapNotNull null
            val values = axes.mapNotNull { (key, c) ->
                val raw = c?.let { r.getOrNull(it) }?.trim()?.toIntOrNull() ?: return@mapNotNull null
                EMOODS_LEVEL[raw.coerceIn(0, 3)]?.let { key to it }
            }.toMap()
            Day(
                date = date,
                values = values,
                sleepHours = sleepCol?.let { r.getOrNull(it) }?.replace(',', '.')?.toFloatOrNull()?.takeIf { it in 0f..24f },
                notes = leftovers("eMoods", header, r, known),
            )
        }
    }

    private val SAA_TIME = DateTimeFormatter.ofPattern("d. M. yyyy H:mm")

    /**
     * Sleep as Android `sleep-export.csv`: every night is a header row starting with "Id"
     * followed by its value row (then optional event/noise rows). Times look like
     * "10. 04. 2026 6:30". A night belongs to the day you wake up on; naps add up, bed/wake
     * times come from the longest sleep.
     */
    private fun sleepAsAndroid(rows: List<List<String>>): List<Day> {
        data class Night(val date: String, val from: java.time.LocalDateTime, val to: java.time.LocalDateTime, val hours: Float, val note: String?)
        val nights = mutableListOf<Night>()
        rows.forEachIndexed { i, header ->
            if (header.firstOrNull()?.trim() != "Id") return@forEachIndexed
            val values = rows.getOrNull(i + 1) ?: return@forEachIndexed
            fun v(name: String) = header.indexOfFirst { it.trim() == name }.takeIf { it >= 0 }?.let { values.getOrNull(it) }?.trim().orEmpty()
            val from = runCatching { java.time.LocalDateTime.parse(v("From"), SAA_TIME) }.getOrNull() ?: return@forEachIndexed
            val to = runCatching { java.time.LocalDateTime.parse(v("To"), SAA_TIME) }.getOrNull() ?: return@forEachIndexed
            val hours = v("Hours").replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..24f } ?: return@forEachIndexed
            val note = listOfNotNull(
                v("Rating").toFloatOrNull()?.takeIf { it > 0f }?.let { "оценка ${"%.1f".format(it)}" },
                v("DeepSleep").toFloatOrNull()?.takeIf { it in 0f..1f }?.let { "глубокий сон ${(it * 100).toInt()}%" },
                v("Comment").takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { null }
            nights += Night(to.toLocalDate().toString(), from, to, hours, note)
        }
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        return nights.groupBy { it.date }.map { (date, list) ->
            val main = list.maxBy { it.hours }
            Day(
                date = date,
                sleepHours = list.sumOf { it.hours.toDouble() }.toFloat().coerceAtMost(24f),
                bedTime = main.from.format(hm),
                wakeTime = main.to.format(hm),
                notes = list.mapNotNull { it.note }.map { "Sleep as Android · $it" },
            )
        }
    }

    /** Daylio mood labels are user-renamable, so they are kept as text, not mapped to scales. */
    private fun daylio(header: List<String>, rows: List<List<String>>): List<Day> {
        val i = header.map { it.trim().lowercase() }
        val dateCol = i.indexOf("full_date")
        return rows.mapNotNull { r ->
            val date = date(r.getOrNull(dateCol)) ?: return@mapNotNull null
            fun v(name: String) = i.indexOf(name).takeIf { it >= 0 }?.let { r.getOrNull(it) }?.trim().orEmpty()
            val parts = listOfNotNull(
                v("time").takeIf { it.isNotEmpty() },
                v("mood").takeIf { it.isNotEmpty() }?.let { "настроение: $it" },
                v("activities").takeIf { it.isNotEmpty() }?.let { "занятия: ${it.replace(" | ", ", ")}" },
                v("note_title").takeIf { it.isNotEmpty() },
                v("note").takeIf { it.isNotEmpty() },
            )
            if (parts.isEmpty()) null else Day(date, notes = listOf("Daylio · " + parts.joinToString(" · ")))
        }
    }

    /** Bearable: one row per logged item (category, rating/amount, detail, notes). */
    private fun bearable(header: List<String>, rows: List<List<String>>): List<Day> {
        val i = header.map { it.trim().lowercase() }
        fun v(r: List<String>, name: String) = i.indexOf(name).takeIf { it >= 0 }?.let { r.getOrNull(it) }?.trim().orEmpty()
        return rows.mapNotNull { r ->
            val date = date(v(r, "date")) ?: return@mapNotNull null
            val text = listOf(v(r, "time of day"), v(r, "category"), v(r, "rating/amount"), v(r, "detail"), v(r, "notes"))
                .filter { it.isNotEmpty() }.joinToString(" · ")
            if (text.isEmpty()) null else Day(date, notes = listOf("Bearable · $text"))
        }.groupBy { it.date }.map { (date, list) -> Day(date, notes = list.flatMap { it.notes }) }
    }

    /** Any CSV with a date column: clearly named columns → scales, the rest → note. */
    private fun generic(header: List<String>, rows: List<List<String>>): List<Day>? {
        val dateCol = header.indexOfFirst { it.trim().lowercase() in DATE_NAMES }.takeIf { it >= 0 } ?: return null
        val mapped = header.withIndex().mapNotNull { (i, h) -> GENERIC_AXES[h.trim().lowercase()]?.let { i to it } }.toMap()
        val sleepCol = header.indexOfFirst { it.trim().lowercase() in SLEEP_NAMES }.takeIf { it >= 0 }
        val known = setOfNotNull(dateCol, sleepCol) + mapped.keys
        val days = rows.mapNotNull { r ->
            val date = date(r.getOrNull(dateCol)) ?: return@mapNotNull null
            Day(
                date = date,
                values = mapped.mapNotNull { (i, key) -> r.getOrNull(i)?.trim()?.toIntOrNull()?.let { key to clampFor(key, it) } }.toMap(),
                sleepHours = sleepCol?.let { r.getOrNull(it) }?.replace(',', '.')?.toFloatOrNull()?.takeIf { it in 0f..24f },
                notes = leftovers("Импорт", header, r, known),
            )
        }
        return days.takeIf { it.isNotEmpty() }
    }

    // ---------- JSON ----------

    /** Generic JSON: an array of objects with a date field (top level or one level down). */
    private fun parseJson(text: String): Result? {
        val arrays = mutableListOf<JSONArray>()
        when (val root = runCatching { if (text.startsWith("[")) JSONArray(text) else JSONObject(text) }.getOrNull()) {
            is JSONArray -> arrays += root
            is JSONObject -> root.keys().forEach { k -> root.optJSONArray(k)?.let(arrays::add) }
            else -> return null
        }
        val days = arrays.flatMap { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { o ->
                val dateKey = o.keys().asSequence().firstOrNull { it.lowercase() in DATE_NAMES } ?: return@mapNotNull null
                val date = date(o.opt(dateKey)?.toString()) ?: return@mapNotNull null
                val values = mutableMapOf<String, Int>()
                var sleep: Float? = null
                val rest = mutableListOf<String>()
                o.keys().forEach { k ->
                    if (k == dateKey) return@forEach
                    val v = o.opt(k)
                    val lk = k.lowercase()
                    when {
                        lk in GENERIC_AXES && v is Number -> GENERIC_AXES.getValue(lk).let { key -> values[key] = clampFor(key, v.toInt()) }
                        lk in SLEEP_NAMES && v is Number -> sleep = v.toFloat().takeIf { it in 0f..24f }
                        v == null || v == JSONObject.NULL || v.toString().isBlank() -> Unit
                        v is JSONObject || v is JSONArray -> Unit
                        else -> rest += "$k: $v"
                    }
                }
                Day(date, values, sleep, if (rest.isEmpty()) emptyList() else listOf("Импорт · " + rest.joinToString(" · ")))
            }
        }
        return days.takeIf { it.isNotEmpty() }?.let { Result("JSON", it) }
    }

    // ---------- helpers ----------

    private val DATE_NAMES = setOf("date", "дата", "day", "день", "full_date", "date (yyyy-mm-dd)", "datetime", "timestamp")
    private val SLEEP_NAMES = setOf("sleep", "сон", "sleep_hours", "sleephours", "часы сна", "sleep hours")
    private val GENERIC_AXES = mapOf(
        "depressed" to "depressed", "depression" to "depressed", "подавленность" to "depressed", "спад" to "depressed",
        "elevated" to "elevated", "mania" to "elevated", "подъём" to "elevated", "подъем" to "elevated",
        "anxiety" to "anxious", "anxious" to "anxious", "тревога" to "anxious",
        "irritability" to "irritable", "irritable" to "irritable", "раздражение" to "irritable", "раздражительность" to "irritable",
        // Trace's own CSV export columns.
        "energy" to "energy", "concentration" to "concentration", "appetite" to "appetite",
        "sociability" to "sociability", "sleep_quality" to "sleepQuality", "functioning" to "functioning",
        "safety" to "safetyCheck", "routine" to "routineScore",
    )

    /** Option scales are 0–3; mood/intensity scales 0–5. */
    private fun clampFor(key: String, v: Int): Int = when (key) {
        "energy", "concentration", "appetite", "sociability", "sleepQuality", "safetyCheck" -> v.coerceIn(0, 3)
        else -> v.coerceIn(0, 5)
    }

    private fun leftovers(source: String, header: List<String>, row: List<String>, skip: Set<Int>): List<String> {
        val parts = header.indices
            .filter { it !in skip }
            .mapNotNull { i -> row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() && it != "0" }?.let { "${header[i].trim()}: $it" } }
        return if (parts.isEmpty()) emptyList() else listOf("$source · " + parts.joinToString(" · "))
    }

    private val PATTERNS = listOf("yyyy-MM-dd", "dd.MM.yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "d.M.yyyy", "M/d/yyyy")
        .map { DateTimeFormatter.ofPattern(it) }

    /** ISO date from common formats ("2024-01-31", "2024-01-31 22:10", "31.01.2024", epoch ms). */
    fun date(raw: String?): String? {
        val s = raw?.trim()?.trim('"') ?: return null
        if (s.isEmpty()) return null
        s.toLongOrNull()?.takeIf { it > 100_000_000L }?.let { n ->
            val ms = if (n > 100_000_000_000L) n else n * 1000
            return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
        val head = s.substringBefore('T').substringBefore(' ')
        for (p in PATTERNS) {
            val d = runCatching { LocalDate.parse(head, p) }.getOrNull() ?: continue
            if (d.year in 1990..2100) return d.toString()
        }
        return null
    }

    /** RFC-4180-ish CSV: quotes, escaped quotes, newlines inside quotes, `,` or `;`. */
    internal object Csv {
        fun parse(text: String): List<List<String>> {
            val firstLine = text.substringBefore('\n')
            val delim = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
            val rows = mutableListOf<List<String>>()
            var row = mutableListOf<String>()
            val cell = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                    c == '"' -> inQuotes = !inQuotes
                    !inQuotes && c == delim -> { row += cell.toString(); cell.clear() }
                    !inQuotes && (c == '\n' || c == '\r') -> {
                        if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                        row += cell.toString(); cell.clear()
                        rows += row; row = mutableListOf()
                    }
                    else -> cell.append(c)
                }
                i++
            }
            if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
            return rows
        }
    }
}
