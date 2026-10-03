package com.moodlife.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedsUtilsTest {

    @Test
    fun `ticks recorded under an old scheme stay visible after scheme change`() {
        // Day was ticked as morning/evening, then the scheme moved to explicit times.
        val day = MedsUtils.daySlots(
            intakeTimesRaw = """["09:00","21:00"]""",
            taken = true,
            slotsTakenJson = """{"morning":true,"evening":true}""",
        )
        assertEquals(listOf("09:00", "21:00", "morning", "evening"), day.slots)
        assertEquals(2, day.takenCount)
        assertTrue(day.taken["morning"] == true)
        assertFalse(day.taken["09:00"] == true)
    }

    @Test
    fun `unticked orphan keys are not resurrected`() {
        val day = MedsUtils.daySlots("""["09:00"]""", false, """{"morning":false,"09:00":true}""")
        assertEquals(listOf("09:00"), day.slots)
        assertEquals(1, day.takenCount)
    }

    @Test
    fun `legacy whole-day flag without slot map counts as taken`() {
        val day = MedsUtils.daySlots("""["by-scheme"]""", taken = true, slotsTakenJson = "{}")
        assertTrue(day.untimed)
        assertEquals(1, day.scheduledCount)
        assertEquals(1, day.takenCount)
    }

    @Test
    fun `by-scheme tick survives switch to timed scheme`() {
        val day = MedsUtils.daySlots("""["morning","evening"]""", true, """{"by-scheme":true}""")
        assertEquals(3, day.scheduledCount)
        assertEquals(1, day.takenCount)
        assertFalse(day.untimed)
    }
}
