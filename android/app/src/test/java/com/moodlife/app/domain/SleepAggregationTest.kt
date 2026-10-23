package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SleepAggregationTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private fun t(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant()

    @Test
    fun `same night from two apps is not double counted and Sleep as Android wins`() {
        val days = SleepAggregation.byDay(
            listOf(
                SleepAggregation.Session("com.urbandroid.sleep", t("2026-10-09T23:30"), t("2026-10-10T07:00"), awakeMinutes = 30),
                SleepAggregation.Session("com.sec.android.app.shealth", t("2026-10-09T23:00"), t("2026-10-10T07:30")),
            ),
            zone,
        )
        assertEquals(1, days.size)
        val d = days.single()
        assertEquals("2026-10-10", d.date)
        assertEquals(7.0f, d.hours, 0.01f) // 7.5 h in bed − 30 min awake
        assertEquals("23:30", d.bedTime)
        assertEquals("07:00", d.wakeTime)
        assertEquals("com.urbandroid.sleep", d.origin)
    }

    @Test
    fun `overlapping sessions of one app are merged, a nap adds up`() {
        val days = SleepAggregation.byDay(
            listOf(
                SleepAggregation.Session("app", t("2026-10-09T23:00"), t("2026-10-10T03:00")),
                SleepAggregation.Session("app", t("2026-10-10T02:00"), t("2026-10-10T06:00")),
                SleepAggregation.Session("app", t("2026-10-10T14:00"), t("2026-10-10T15:00")),
            ),
            zone,
        )
        assertEquals(8.0f, days.single().hours, 0.01f) // 23–06 merged (7 h) + 1 h nap
        assertEquals("23:00", days.single().bedTime) // merged night block, not the nap
        assertEquals("06:00", days.single().wakeTime)
    }

    @Test
    fun `without the preferred app the longest source is used`() {
        val d = SleepAggregation.byDay(
            listOf(
                SleepAggregation.Session("a", t("2026-10-10T00:00"), t("2026-10-10T05:00")),
                SleepAggregation.Session("b", t("2026-10-09T23:00"), t("2026-10-10T07:00")),
            ),
            zone,
        ).single()
        assertEquals("b", d.origin)
        assertEquals(8.0f, d.hours, 0.01f)
    }

    @Test
    fun `payload round trip`() {
        val d = SleepAggregation.Day("2026-10-10", 7f, "23:30", "07:00", "x")
        assertEquals("23:30", SleepAggregation.bedTime(d.payload()))
        assertEquals("07:00", SleepAggregation.wakeTime(d.payload()))
    }
}
