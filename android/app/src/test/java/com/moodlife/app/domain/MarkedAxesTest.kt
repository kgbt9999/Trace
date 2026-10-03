package com.moodlife.app.domain

import com.moodlife.app.data.local.entity.MoodEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkedAxesTest {

    private fun entry(marked: String = "", depressed: Int = 0, elevated: Int = 0, energy: Int = 0, appetite: Int = 0) =
        MoodEntryEntity(
            id = "x", date = "2026-09-01",
            depressed = depressed, elevated = elevated, energy = energy, appetite = appetite,
            markedAxes = marked, createdAt = 0, updatedAt = 0,
        )

    @Test
    fun `legacy rows keep mood zeros but drop untouched option scales`() {
        val e = entry(depressed = 0, elevated = 2, energy = 0, appetite = 1)
        assertEquals(0, e.markedValue("depressed"))
        assertEquals(2, e.markedValue("elevated"))
        assertNull(e.markedValue("energy"))
        assertEquals(1, e.markedValue("appetite"))
    }

    @Test
    fun `v2 rows use the stored list, including deliberate zeros`() {
        val e = entry(marked = MarkedAxes.serialize(setOf("energy", "depressed")), energy = 0, elevated = 3)
        assertEquals(0, e.markedValue("energy"))
        assertEquals(0, e.markedValue("depressed"))
        assertNull(e.markedValue("elevated"))
        assertEquals(-0, e.markedPolarity())
    }

    @Test
    fun `day with nothing marked has no polarity`() {
        val e = entry(marked = MarkedAxes.serialize(emptySet()))
        assertNull(e.markedPolarity())
        assertFalse(e.isMarked("depressed"))
    }

    @Test
    fun `serialize is stable and round-trips`() {
        val raw = MarkedAxes.serialize(setOf("safetyCheck", "anxious"))
        assertEquals("v2:anxious,safetyCheck", raw)
        assertTrue(entry(marked = raw).isMarked("safetyCheck"))
    }
}
