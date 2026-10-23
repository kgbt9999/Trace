package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloNativeExportTest {

    // Same shape as Flo's «Request my data» export; values are synthetic.
    private val export = """
        {
          "operationalData": {
            "cycles": [
              {"period_start_date": "2026-08-01 00:00:00.0", "period_end_date": "2026-08-03 00:00:00.0",
               "period_intensity": "{\"0\": 3, \"1\": 2}", "pregnant": false},
              {"period_start_date": "2026-08-29 00:00:00.0", "period_end_date": "2026-08-30 00:00:00.0",
               "period_intensity": "{}", "pregnant": false},
              {"period_start_date": "2099-01-01 00:00:00.0", "period_end_date": "2099-01-05 00:00:00.0",
               "period_intensity": "{}", "pregnant": false}
            ]
          },
          "pointEventsManualData": {
            "point_events_manual_v2": [
              {"local_date": "2026-08-02 00:00:00", "category": "Symptom", "subcategory": "Fatigue"},
              {"local_date": "2026-08-02 00:00:00", "category": "Mood", "subcategory": "Depressed"},
              {"local_date": "2026-08-10 00:00:00", "category": "Sport", "subcategory": "Running"}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `nested Flo export yields period days and marks, skipping future and sport`() {
        val r = FloParse.parseJson(export)
        val byDate = r.days.associateBy { it.date }
        assertEquals(listOf("2026-08-01", "2026-08-02", "2026-08-03", "2026-08-29", "2026-08-30"), r.days.map { it.date }.sorted())
        assertEquals(4, byDate.getValue("2026-08-01").flow) // Flo level 3 → heavy
        assertEquals(3, byDate.getValue("2026-08-29").flow) // no intensity → medium period day
        assertTrue("усталость" in byDate.getValue("2026-08-02").items)
        assertEquals("подавленность", byDate.getValue("2026-08-02").mood)
        assertEquals(28, r.cycleLength)
    }
}
