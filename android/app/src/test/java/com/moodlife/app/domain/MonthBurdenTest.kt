package com.moodlife.app.domain

import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthBurdenTest {

    @Test
    fun classify_mixedBeatsSingleAxis() {
        assertEquals(MonthBurden.DayKind.MIXED, MonthBurden.classify(2, 2))
        assertEquals(MonthBurden.DayKind.DEPRESSED, MonthBurden.classify(3, 0))
        assertEquals(MonthBurden.DayKind.ELEVATED, MonthBurden.classify(0, 3))
        assertEquals(MonthBurden.DayKind.OTHER, MonthBurden.classify(1, 1))
    }

    @Test
    fun counts_sumToTotal() {
        val counts = MonthBurden.counts(
            listOf(3 to 0, 0 to 3, 2 to 2, 0 to 0, 4 to 1),
        )
        assertEquals(1, counts.mixed)
        assertEquals(2, counts.depressed)
        assertEquals(1, counts.elevated)
        assertEquals(1, counts.other)
        assertEquals(5, counts.total)
    }

    @Test
    fun bedtimeSpread_needsFourTimes() {
        assertNull(MonthBurden.bedtimeSpreadMinutes(listOf("23:00", "23:10", "22:50")))
        val spread = MonthBurden.bedtimeSpreadMinutes(listOf("23:00", "23:00", "23:00", "23:00"))
        assertEquals(0, spread)
    }

    @Test
    fun adherence_countsTakenSlots() {
        val med = MedicationEntity(
            id = "m1",
            name = "литиевая соль",
            intakeTimes = "[\"morning\",\"evening\"]",
            createdAt = 0,
            updatedAt = 0,
        )
        val log = MedicationLogEntity(
            id = "l1",
            medicationId = "m1",
            date = "2026-09-01",
            taken = true,
            slotsTaken = "{\"morning\":true,\"evening\":false}",
            createdAt = 0,
            updatedAt = 0,
        )
        val adh = MonthBurden.adherence(listOf(log), listOf(med))
        assertEquals(1, adh.taken)
        assertEquals(2, adh.scheduled)
        assertEquals(50, adh.percent)
    }

    @Test
    fun adherence_usesIntakeTimesSnapshot() {
        val med = MedicationEntity(
            id = "m1",
            name = "литиевая соль",
            intakeTimes = "[\"morning\",\"evening\",\"night\"]",
            createdAt = 0,
            updatedAt = 0,
        )
        val log = MedicationLogEntity(
            id = "l1",
            medicationId = "m1",
            date = "2026-09-01",
            taken = false,
            slotsTaken = "{\"morning\":true,\"evening\":true}",
            intakeTimesSnapshot = "[\"morning\",\"evening\"]",
            createdAt = 0,
            updatedAt = 0,
        )
        val adh = MonthBurden.adherence(listOf(log), listOf(med))
        assertEquals(2, adh.taken)
        assertEquals(2, adh.scheduled)
        assertEquals(100, adh.percent)
    }
}
