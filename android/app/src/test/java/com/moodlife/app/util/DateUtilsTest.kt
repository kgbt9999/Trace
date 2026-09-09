package com.moodlife.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateUtilsTest {

    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun todayIso_formatsAsYyyyMmDd() {
        val result = DateUtils.formatIso(LocalDate.of(2026, 8, 31))
        assertEquals("2026-08-31", result)
    }

    @Test
    fun addDays_crossesMonthBoundary() {
        assertEquals("2026-09-01", DateUtils.addDays("2026-08-31", 1, zone))
    }

    @Test
    fun addDays_negativeDelta() {
        assertEquals("2026-08-30", DateUtils.addDays("2026-08-31", -1, zone))
    }

    @Test
    fun daysBetween_sameDayIsZero() {
        assertEquals(0, DateUtils.daysBetween("2026-08-31", "2026-08-31"))
    }

    @Test
    fun daysBetween_weekApart() {
        assertEquals(7, DateUtils.daysBetween("2026-08-24", "2026-08-31"))
    }

    @Test
    fun parseAndFormat_roundTrip() {
        val iso = "2026-01-15"
        assertEquals(iso, DateUtils.formatIso(DateUtils.parseIso(iso)))
    }

    @Test
    fun isValidIsoDate_rejectsInvalid() {
        assertEquals(true, DateUtils.isValidIsoDate("2026-09-01"))
        assertEquals(false, DateUtils.isValidIsoDate("2026-13-01"))
        assertEquals(false, DateUtils.isValidIsoDate("01-09-2026"))
        assertEquals(false, DateUtils.isValidIsoDate(null))
    }
}
