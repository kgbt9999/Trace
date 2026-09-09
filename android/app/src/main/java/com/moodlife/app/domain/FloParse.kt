package com.moodlife.app.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.moodlife.app.util.DateUtils
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** Port of web-reference/src/lib/flo-parse.ts */
object FloParse {
    const val MAX_ENTRIES = 5000

    data class FloDayDetails(
        val items: List<String> = emptyList(),
        val pain: String? = null,
        val mood: String? = null,
        val discharge: String? = null,
        val intimacy: String? = null,
        val note: String? = null,
    )

    data class FloParsedDay(
        val date: String,
        var flow: Any? = null,
        var items: MutableList<String> = mutableListOf(),
        var pain: String? = null,
        var mood: String? = null,
        var discharge: String? = null,
        var intimacy: String? = null,
        var note: String? = null,
    )

    data class ParseResult(
        val days: List<FloParsedDay>,
        val detected: Int,
        val errors: List<String>,
        val cycleLength: Int? = null,
    )

    fun parseJson(text: String): ParseResult {
        val element = try {
            JsonParser.parseString(text)
        } catch (_: Exception) {
            return ParseResult(emptyList(), 0, listOf("Неверный JSON"))
        }
        return parseFloExport(element)
    }

    fun parseFloExport(body: JsonElement?): ParseResult {
        val into = linkedMapOf<String, FloParsedDay>()
        val errors = mutableListOf<String>()
        if (body == null || body.isJsonNull) {
            return ParseResult(emptyList(), 0, listOf("Тело запроса не JSON-объект"))
        }
        if (body.isJsonArray) {
            walkArray(body.asJsonArray, into)
        } else if (body.isJsonObject) {
            val rec = body.asJsonObject
            val buckets = listOf(
                "periods", "cycles", "period_entries", "periodEntries",
                "symptoms", "symptom_entries", "symptomEntries",
                "daily", "days", "daily_logs", "logs",
                "sex", "intimacy", "notes", "discharge",
            )
            for (key in buckets) {
                val b = rec.get(key)
                if (b != null && b.isJsonArray) walkArray(b.asJsonArray, into)
            }
        } else {
            return ParseResult(emptyList(), 0, listOf("Тело запроса не JSON-объект"))
        }

        val days = into.values.filter { DateUtils.isValidIsoDate(it.date) }
        val cycleLength = cycleLengthFrom(body)
        if (days.size > MAX_ENTRIES) {
            return ParseResult(
                days = days.take(MAX_ENTRIES),
                detected = days.size,
                errors = listOf("Обрезано до $MAX_ENTRIES"),
                cycleLength = cycleLength,
            )
        }
        return ParseResult(days, days.size, errors, cycleLength)
    }

    fun serializeFloDetails(day: FloDayDetails): String {
        val o = JsonObject()
        val items = JsonArray()
        day.items.forEach { items.add(it) }
        o.add("items", items)
        o.addProperty("pain", day.pain)
        o.addProperty("mood", day.mood)
        o.addProperty("discharge", day.discharge)
        o.addProperty("intimacy", day.intimacy)
        o.addProperty("note", day.note)
        return o.toString()
    }

    fun readFloDetails(raw: String?): FloDayDetails {
        if (raw.isNullOrBlank()) return FloDayDetails()
        return try {
            val parsed = JsonParser.parseString(raw)
            if (parsed.isJsonArray) {
                FloDayDetails(items = parsed.asJsonArray.mapNotNull { asString(it) })
            } else if (parsed.isJsonObject) {
                val o = parsed.asJsonObject
                FloDayDetails(
                    items = asStringList(o.get("items") ?: o.get("symptoms")),
                    pain = asString(o.get("pain")),
                    mood = asString(o.get("mood")),
                    discharge = asString(o.get("discharge")),
                    intimacy = asString(o.get("intimacy")),
                    note = asString(o.get("note")),
                )
            } else {
                FloDayDetails()
            }
        } catch (_: Exception) {
            FloDayDetails(items = listOf(raw.take(200)))
        }
    }

    fun mapFlow(value: Any?): Int {
        when (value) {
            null -> return 0
            is Number -> return value.toInt().coerceIn(0, 4)
            is String -> {
                val s = value.lowercase(Locale.getDefault())
                if (s.contains("heavy") || s.contains("обильн")) return 4
                if (s.contains("medium") || s.contains("средн") || s.contains("moderate")) return 3
                if (s.contains("light") || s.contains("лёгк") || s.contains("легк") ||
                    s.contains("spotting") || s.contains("маж")
                ) {
                    return 2
                }
                if (s.contains("none") || s.contains("нет") || s.contains("no")) return 0
            }
            is JsonElement -> {
                if (value.isJsonNull) return 0
                if (value.isJsonPrimitive) {
                    val p = value.asJsonPrimitive
                    if (p.isNumber) return p.asInt.coerceIn(0, 4)
                    if (p.isString) return mapFlow(p.asString)
                }
            }
        }
        return 0
    }

    private fun cycleLengthFrom(body: JsonElement): Int? {
        if (!body.isJsonObject) return null
        val rec = body.asJsonObject
        val raw = rec.get("cycle_length") ?: rec.get("avg_cycle_length") ?: return null
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber) return null
        val n = raw.asInt
        return n.takeIf { it in 15..90 }
    }

    private fun isoDate(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive) return null
        val p = value.asJsonPrimitive
        if (p.isString) {
            val slice = p.asString.take(10)
            return if (DateUtils.isValidIsoDate(slice)) slice else null
        }
        if (p.isNumber) {
            val n = p.asDouble
            val millis = if (n > 1e12) n.toLong() else (n * 1000).toLong()
            val d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            val iso = DateUtils.formatIso(d)
            return if (DateUtils.isValidIsoDate(iso)) iso else null
        }
        return null
    }

    private fun asString(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive) return null
        val p = value.asJsonPrimitive
        return when {
            p.isString -> p.asString.trim().takeIf { it.isNotEmpty() }?.take(200)
            p.isNumber -> p.asString
            p.isBoolean -> if (p.asBoolean) "да" else "нет"
            else -> null
        }
    }

    private fun asStringList(value: JsonElement?): List<String> {
        if (value == null || value.isJsonNull) return emptyList()
        if (value.isJsonPrimitive) {
            return asString(value)?.let { listOf(it) }.orEmpty()
        }
        if (value.isJsonArray) {
            return value.asJsonArray.flatMap { v ->
                when {
                    v.isJsonPrimitive -> asString(v)?.let { listOf(it) }.orEmpty()
                    v.isJsonObject -> {
                        val rec = v.asJsonObject
                        val name = asString(
                            rec.get("name") ?: rec.get("symptom") ?: rec.get("title")
                                ?: rec.get("type") ?: rec.get("label"),
                        )
                        if (name != null) listOf(name) else emptyList()
                    }
                    else -> emptyList()
                }
            }
        }
        if (value.isJsonObject) {
            return value.asJsonObject.entrySet().mapNotNull { (k, v) ->
                when {
                    v.isJsonPrimitive && v.asJsonPrimitive.isBoolean && v.asBoolean -> k
                    v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asInt == 1 -> k
                    v.isJsonPrimitive && v.asJsonPrimitive.isString -> {
                        val s = v.asString
                        if (s.isNotEmpty() && s != "none") "$k: $s" else null
                    }
                    else -> null
                }
            }
        }
        return emptyList()
    }

    private fun mergeDay(into: FloParsedDay, extra: FloParsedDay) {
        if (extra.flow != null && extra.flow != "") into.flow = extra.flow
        extra.pain?.let { into.pain = it }
        extra.mood?.let { into.mood = it }
        extra.discharge?.let { into.discharge = it }
        extra.intimacy?.let { into.intimacy = it }
        if (extra.note != null) {
            into.note = listOfNotNull(into.note, extra.note).joinToString(" · ")
        }
        if (extra.items.isNotEmpty()) {
            val set = linkedSetOf<String>().apply {
                addAll(into.items)
                addAll(extra.items)
            }
            into.items = set.toMutableList()
        }
    }

    private fun fromRecord(rec: JsonObject, fallbackDate: String?): FloParsedDay? {
        val date = isoDate(
            rec.get("date") ?: rec.get("day") ?: rec.get("datetime")
                ?: rec.get("start_date") ?: rec.get("startDate"),
        ) ?: fallbackDate
        if (date == null) return null
        val items = mutableListOf<String>()
        items += asStringList(rec.get("symptoms"))
        items += asStringList(rec.get("symptom"))
        items += asStringList(rec.get("tags"))
        val pain = asString(rec.get("pain") ?: rec.get("cramps") ?: rec.get("cramp") ?: rec.get("backache"))
        val mood = asString(rec.get("mood") ?: rec.get("feeling") ?: rec.get("emotions"))
        val discharge = asString(rec.get("discharge") ?: rec.get("cervical_mucus") ?: rec.get("mucus"))
        val intimacy = asString(rec.get("sex") ?: rec.get("intimacy") ?: rec.get("intercourse") ?: rec.get("had_sex"))
        val note = asString(rec.get("note") ?: rec.get("notes") ?: rec.get("comment") ?: rec.get("description"))
        val flowEl = rec.get("flow") ?: rec.get("flowLevel") ?: rec.get("bleeding") ?: rec.get("period_flow")
        val flow: Any? = when {
            flowEl == null || flowEl.isJsonNull -> null
            flowEl.isJsonPrimitive && flowEl.asJsonPrimitive.isNumber -> flowEl.asInt
            flowEl.isJsonPrimitive && flowEl.asJsonPrimitive.isString -> flowEl.asString
            else -> null
        }
        if (pain != null) items += "боль: $pain"
        if (mood != null) items += "настроение Flo: $mood"
        if (discharge != null) items += "выделения: $discharge"
        if (intimacy != null) items += "близость: $intimacy"
        return FloParsedDay(
            date = date,
            flow = flow,
            items = items.distinct().toMutableList(),
            pain = pain,
            mood = mood,
            discharge = discharge,
            intimacy = intimacy,
            note = note,
        )
    }

    private fun walkArray(arr: JsonArray, into: MutableMap<String, FloParsedDay>, fallbackDate: String? = null) {
        for (item in arr) {
            if (item == null || !item.isJsonObject) continue
            val rec = item.asJsonObject
            val parsed = fromRecord(rec, fallbackDate)
            if (parsed != null) {
                val existing = into[parsed.date] ?: FloParsedDay(date = parsed.date)
                mergeDay(existing, parsed)
                into[parsed.date] = existing
            }
            for ((_, nested) in rec.entrySet()) {
                if (nested.isJsonArray) {
                    val size = nested.asJsonArray.size()
                    if (size in 1..1999) {
                        val nestedDate = isoDate(rec.get("date") ?: rec.get("start_date"))
                        walkArray(nested.asJsonArray, into, nestedDate ?: fallbackDate)
                    }
                }
            }
        }
    }
}
