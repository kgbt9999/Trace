package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClinicalAlertsTest {

    @Test
    fun detectMixed_requiresBothAxes() {
        assertNull(ClinicalAlerts.detectMixed(3, 1))
        assertNull(ClinicalAlerts.detectMixed(1, 3))
        assertEquals(ClinicalAlerts.MixedSeverity.MILD, ClinicalAlerts.detectMixed(2, 2))
        assertEquals(ClinicalAlerts.MixedSeverity.MODERATE, ClinicalAlerts.detectMixed(3, 2))
        assertEquals(ClinicalAlerts.MixedSeverity.SEVERE, ClinicalAlerts.detectMixed(3, 3))
    }

    @Test
    fun safetyLevel_matchesColumbiaSteps() {
        assertEquals(ClinicalAlerts.SafetyLevel.SAFE, ClinicalAlerts.safetyLevel(0))
        assertEquals(ClinicalAlerts.SafetyLevel.CAUTION, ClinicalAlerts.safetyLevel(1))
        assertEquals(ClinicalAlerts.SafetyLevel.WARNING, ClinicalAlerts.safetyLevel(2))
        assertEquals(ClinicalAlerts.SafetyLevel.CRISIS, ClinicalAlerts.safetyLevel(3))
    }

    @Test
    fun phaseInfo_hidesEuthymia() {
        assertNull(ClinicalAlerts.phaseInfo("euthymic"))
        val mixed = ClinicalAlerts.phaseInfo("mixed")
        assertEquals("Одновременно спад и подъём (дневник)", mixed?.label)
        assert(mixed?.description?.contains("не диагноз") == true)
    }
}
