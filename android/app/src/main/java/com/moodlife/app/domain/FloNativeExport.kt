package com.moodlife.app.domain

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.moodlife.app.util.DateUtils
import java.time.LocalDate

/**
 * Flo's real account export («Request my data»): data is nested, not at the top level —
 * periods in `operationalData.cycles` (`period_start_date` / `period_end_date`, dates like
 * "2017-01-15 00:00:00.0") and day marks in `pointEventsManualData.point_events_manual_v2`
 * (category + subcategory, e.g. Symptom/Fatigue). The generic walker only looked for top-level
 * arrays, found nothing and the import reported "no Flo records".
 */
internal object FloNativeExport {

    fun matches(root: JsonObject): Boolean =
        root.has("operationalData") || root.has("pointEventsManualData")

    fun parse(root: JsonObject, into: MutableMap<String, FloParse.FloParsedDay>, today: LocalDate = LocalDate.now()) {
        val cycles = root.getAsJsonObject("operationalData")?.getAsJsonArray("cycles")
        cycles?.forEach { el -> if (el.isJsonObject) addCycle(el.asJsonObject, into, today) }
        val events = root.getAsJsonObject("pointEventsManualData")?.getAsJsonArray("point_events_manual_v2")
        events?.forEach { el -> if (el.isJsonObject) addEvent(el.asJsonObject, into) }
    }

    /** Median gap between period starts (completed cycles only), if plausible. */
    fun cycleLength(root: JsonObject, today: LocalDate = LocalDate.now()): Int? {
        val starts = root.getAsJsonObject("operationalData")?.getAsJsonArray("cycles")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let { c -> date(c.get("period_start_date")) } }
            ?.filter { !it.isAfter(today) }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        val gaps = starts.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b).toInt() }
            .filter { it in 15..90 }
            .sorted()
        return gaps.getOrNull(gaps.size / 2)
    }

    private fun addCycle(c: JsonObject, into: MutableMap<String, FloParse.FloParsedDay>, today: LocalDate) {
        if (c.get("pregnant")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) return
        val start = date(c.get("period_start_date")) ?: return
        // Flo also stores predicted future periods; only real (past/current) days are imported.
        if (start.isAfter(today)) return
        val end = (date(c.get("period_end_date")) ?: start).coerceAtMost(today)
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceIn(0, 14)
        val intensity = intensityByDay(c.get("period_intensity"))
        for (i in 0..days) {
            val iso = DateUtils.formatIso(start.plusDays(i.toLong()))
            val day = into.getOrPut(iso) { FloParse.FloParsedDay(date = iso) }
            // Flo levels 1..3 → app flow 2..4; Flo leaves intensity empty in most cycles, those
            // days are recorded as a period day of medium flow.
            day.flow = intensity[i]?.let { (it + 1).coerceIn(2, 4) } ?: 3
        }
    }

    private fun addEvent(e: JsonObject, into: MutableMap<String, FloParse.FloParsedDay>) {
        val iso = (date(e.get("local_date")) ?: date(e.get("date")))?.let(DateUtils::formatIso) ?: return
        val category = e.get("category")?.takeIf { it.isJsonPrimitive }?.asString ?: return
        val sub = e.get("subcategory")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val label = LABELS[sub] ?: words(sub)
        val day = { into.getOrPut(iso) { FloParse.FloParsedDay(date = iso) } }
        when (category) {
            "Symptom", "Breasts" -> if (sub != "EverythingIsFine") day().items.addIfAbsent(label)
            "Mood" -> day().apply { mood = label; items.addIfAbsent("настроение Flo: $label") }
            "Fluid" -> day().apply { discharge = label; items.addIfAbsent("выделения: $label") }
            "Sex" -> day().apply { intimacy = label; items.addIfAbsent("близость: $label") }
            "Disturber" -> day().items.addIfAbsent("фактор: $label")
            "Period" -> day().flow = FloParse.mapFlow(sub)
            // Sport, Water, Sleep and the rest are not part of the diary.
        }
    }

    private fun MutableList<String>.addIfAbsent(s: String) {
        if (s !in this) add(s)
    }

    private fun intensityByDay(el: JsonElement?): Map<Int, Int> {
        val raw = el?.takeIf { it.isJsonPrimitive }?.asString ?: return emptyMap()
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.entrySet()
                .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.asInt } }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun date(el: JsonElement?): LocalDate? {
        val s = el?.takeIf { it.isJsonPrimitive }?.asString?.take(10) ?: return null
        return if (DateUtils.isValidIsoDate(s)) LocalDate.parse(s) else null
    }

    /** "IncreasedAppetite" → "increased appetite" for subcategories without a translation. */
    private fun words(camel: String): String =
        camel.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().ifBlank { "отметка" }

    private val LABELS = mapOf(
        // Symptoms
        "Fatigue" to "усталость", "IncreasedAppetite" to "повышенный аппетит", "DrawingPain" to "тянущая боль",
        "Acne" to "акне", "Backache" to "боль в спине", "TenderBreasts" to "чувствительная грудь",
        "Headache" to "головная боль", "Cramps" to "спазмы", "Bloating" to "вздутие", "Insomnia" to "бессонница",
        "Nausea" to "тошнота", "Cravings" to "тяга к еде", "Diarrhea" to "диарея", "Constipation" to "запор",
        "PelvicPain" to "боль внизу живота", "BreastPain" to "боль в груди", "BreastEngorgement" to "нагрубание груди",
        "Dizziness" to "головокружение", "Chills" to "озноб",
        // Mood
        "Swings" to "перепады настроения", "Angry" to "злость", "Depressed" to "подавленность", "Sad" to "грусть",
        "Confused" to "растерянность", "Panic" to "паника", "Anxious" to "тревога", "Calm" to "спокойствие",
        "Happy" to "радость", "Energetic" to "энергичность", "Irritated" to "раздражение", "Apathetic" to "апатия",
        "Frisky" to "игривость", "Guilt" to "вина", "Obsessive" to "навязчивые мысли", "SelfCritical" to "самокритика",
        "LowEnergy" to "мало сил", "Tired" to "усталость",
        // Fluid
        "Bloody" to "кровянистые", "Dry" to "сухо", "Eggwhite" to "как яичный белок", "Sticky" to "густые",
        "Watery" to "водянистые", "Creamy" to "кремообразные", "Spotting" to "мажущие", "Unusual" to "необычные",
        // Sex
        "SexNone" to "не было", "Masturbation" to "мастурбация", "HighDrive" to "высокое влечение",
        "SexUnprotected" to "без защиты", "SexProtected" to "с защитой", "LowDrive" to "низкое влечение",
        // Disturbers
        "Stress" to "стресс", "Alcohol" to "алкоголь", "Travel" to "поездка", "Disease" to "болезнь",
    )
}
