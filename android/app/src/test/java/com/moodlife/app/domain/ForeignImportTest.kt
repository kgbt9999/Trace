package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignImportTest {

    @Test
    fun `eMoods csv maps 0-3 levels to Trace scale and keeps other columns as a note`() {
        val csv = "DATE (YYYY-MM-DD),DEPRESSED,ELEVATED,IRRITABILITY,ANXIETY,SLEEP,PSYCHOTIC SYMPTOMS,THERAPY,NOTE\n" +
            "2026-09-01,2,0,1,3,6.5,0,1,\"плохой день, \"\"устала\"\"\"\n"
        val r = ForeignImport.parse("emoods.csv", csv)!!
        assertEquals("eMoods", r.format)
        val d = r.days.single()
        assertEquals("2026-09-01", d.date)
        assertEquals(mapOf("depressed" to 3, "elevated" to 0, "irritable" to 1, "anxious" to 4), d.values)
        assertEquals(6.5f, d.sleepHours!!, 0.001f)
        assertTrue(d.notes.single().contains("NOTE: плохой день, \"устала\""))
        assertTrue(d.notes.single().contains("THERAPY: 1"))
    }

    @Test
    fun `daylio rows become notes, no invented scale values`() {
        val csv = "full_date,date,weekday,time,mood,activities,note_title,note\n" +
            "2026-09-02,Sep 2,Wednesday,21:10,bad,work | sleep early,,устала\n"
        val r = ForeignImport.parse("daylio.csv", csv)!!
        assertEquals("Daylio", r.format)
        val d = r.days.single()
        assertTrue(d.values.isEmpty())
        assertEquals("Daylio · 21:10 · настроение: bad · занятия: work, sleep early · устала", d.notes.single())
    }

    @Test
    fun `trace csv round trips the day scales`() {
        val csv = "date,depressed,elevated,anxious,irritable,energy,concentration,appetite,sociability,sleep_hours,sleep_quality,sleep_time,wake_time,functioning,safety,routine,note\n" +
            "2026-09-03,1,0,2,0,2,1,0,2,7.5,1,23:30,07:00,4,0,3,\n"
        val d = ForeignImport.parse("Trace-report.csv", csv)!!.days.single()
        assertEquals(2, d.values["anxious"])
        assertEquals(2, d.values["energy"])
        assertEquals(4, d.values["functioning"])
        assertEquals(7.5f, d.sleepHours!!, 0.001f)
        assertTrue(d.notes.single().contains("sleep_time: 23:30"))
    }

    @Test
    fun `generic json array with dates`() {
        val json = "[{\"date\":\"31.08.2026\",\"тревога\":3,\"comment\":\"ок\"},{\"note\":\"без даты\"}]"
        val r = ForeignImport.parse("x.json", json)!!
        val d = r.days.single()
        assertEquals("2026-08-31", d.date)
        assertEquals(3, d.values["anxious"])
        assertEquals("Импорт · comment: ок", d.notes.single())
    }

    @Test
    fun `dates in common formats`() {
        assertEquals("2026-09-04", ForeignImport.date("2026-09-04 22:15:00"))
        assertEquals("2026-09-04", ForeignImport.date("04.09.2026"))
        assertEquals("2026-09-04", ForeignImport.date("2026-09-04T10:00:00Z"))
        assertNull(ForeignImport.date("вчера"))
    }

    @Test
    fun `file without dates is not recognised`() {
        assertNull(ForeignImport.parse("a.csv", "name,value\nfoo,1\n"))
    }
}
