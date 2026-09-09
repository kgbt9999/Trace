package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloParseTest {

    @Test
    fun parse_samplePeriodsArray_detectsThreeDays() {
        val json = """
            {
              "periods": [
                { "date": "2026-08-01", "flow": "medium", "symptoms": ["cramps", "fatigue"] },
                { "date": "2026-08-02", "flow": "heavy", "symptoms": ["cramps"] },
                { "date": "2026-08-03", "flow": "light", "symptoms": ["bloating"] }
              ]
            }
        """.trimIndent()
        val result = FloParse.parseJson(json)
        assertEquals(3, result.detected)
        assertEquals(3, result.days.size)
        assertEquals("2026-08-01", result.days[0].date)
        assertEquals(3, FloParse.mapFlow(result.days[0].flow))
        assertEquals(4, FloParse.mapFlow(result.days[1].flow))
        assertTrue(result.days[0].items.contains("cramps"))
        assertTrue(result.days[0].items.contains("fatigue"))
    }

    @Test
    fun parse_topLevelArray_mergesSameDate() {
        val json = """
            [
              { "date": "2026-07-10", "pain": "сильная", "flow": 2 },
              { "date": "2026-07-10", "mood": "тревога", "note": "плохо спала" }
            ]
        """.trimIndent()
        val result = FloParse.parseJson(json)
        assertEquals(1, result.detected)
        val day = result.days.single()
        assertEquals("сильная", day.pain)
        assertEquals("тревога", day.mood)
        assertEquals("плохо спала", day.note)
        assertTrue(day.items.any { it.contains("боль") })
        assertTrue(day.items.any { it.contains("настроение Flo") })
    }

    @Test
    fun parse_invalidJson_returnsError() {
        val result = FloParse.parseJson("not-json")
        assertEquals(0, result.detected)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun parse_emptyObject_detectedZero() {
        val result = FloParse.parseJson("{}")
        assertEquals(0, result.detected)
        assertTrue(result.days.isEmpty())
    }

    @Test
    fun mapFlow_russianAndEnglishLabels() {
        assertEquals(4, FloParse.mapFlow("heavy"))
        assertEquals(4, FloParse.mapFlow("обильные"))
        assertEquals(3, FloParse.mapFlow("medium"))
        assertEquals(3, FloParse.mapFlow("средние"))
        assertEquals(2, FloParse.mapFlow("light"))
        assertEquals(2, FloParse.mapFlow("лёгкие"))
        assertEquals(0, FloParse.mapFlow("none"))
        assertEquals(2, FloParse.mapFlow(2))
    }

    @Test
    fun serializeAndRead_roundTrip() {
        val details = FloParse.FloDayDetails(
            items = listOf("cramps"),
            pain = "mild",
            note = "ok",
        )
        val raw = FloParse.serializeFloDetails(details)
        val back = FloParse.readFloDetails(raw)
        assertEquals(listOf("cramps"), back.items)
        assertEquals("mild", back.pain)
        assertEquals("ok", back.note)
    }

    @Test
    fun readFloDetails_legacyStringArray() {
        val back = FloParse.readFloDetails("""["a","b"]""")
        assertEquals(listOf("a", "b"), back.items)
    }

    @Test
    fun cycleLength_fromRoot() {
        val json = """{"cycle_length": 30, "periods": [{"date": "2026-01-01", "flow": 1}]}"""
        val result = FloParse.parseJson(json)
        assertEquals(30, result.cycleLength)
        assertEquals(1, result.detected)
    }
}
