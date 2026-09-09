package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPhaseTest {
    @Test
    fun moonPhase_returnsKnownIcon() {
        val phase = MoonPhaseCalc.moonPhase("2026-09-04")
        assertTrue(phase.icon.isNotBlank())
        assertTrue(phase.label.isNotBlank())
        assertTrue(phase.age in 0.0..1.0)
    }

    @Test
    fun moonPhase_stableForSameDate() {
        assertEquals(
            MoonPhaseCalc.moonPhase("2024-01-01").id,
            MoonPhaseCalc.moonPhase("2024-01-01").id,
        )
    }
}
