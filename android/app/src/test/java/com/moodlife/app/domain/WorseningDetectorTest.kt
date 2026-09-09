package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorseningDetectorTest {

    private fun slice(
        depressed: Int = 1,
        elevated: Int = 0,
        anxious: Int = 1,
        irritable: Int = 0,
        sleepQuality: Int = 1,
        symptoms: List<WorseningDetector.IdValue> = emptyList(),
        warnings: List<WorseningDetector.IdValue> = emptyList(),
    ) = WorseningDetector.WorseningSlice(
        depressed = depressed,
        elevated = elevated,
        anxious = anxious,
        irritable = irritable,
        sleepQuality = sleepQuality,
        symptomLogs = symptoms,
        warningTriggers = warnings,
    )

    @Test
    fun missingEitherDay_isNotWorsening() {
        assertFalse(WorseningDetector.detect(null, slice()).worsening)
        assertFalse(WorseningDetector.detect(slice(), null).worsening)
        assertFalse(WorseningDetector.detect(null, null).worsening)
    }

    @Test
    fun sameMood_isNotWorsening() {
        val day = slice()
        val result = WorseningDetector.detect(day, day)
        assertFalse(result.worsening)
        assertEquals("", result.reason)
    }

    @Test
    fun depressedUp_isWorsening() {
        val result = WorseningDetector.detect(slice(depressed = 3), slice(depressed = 1))
        assertTrue(result.worsening)
        assertEquals(WorseningDetector.WORSENING_REASON, result.reason)
    }

    @Test
    fun elevatedUp_countsAsWorsening() {
        val result = WorseningDetector.detect(slice(elevated = 2), slice(elevated = 0))
        assertTrue(result.worsening)
    }

    @Test
    fun sleepQualityHigher_isWorse() {
        val result = WorseningDetector.detect(slice(sleepQuality = 3), slice(sleepQuality = 1))
        assertTrue(result.worsening)
    }

    @Test
    fun symptomHigherThanYesterday_isWorsening() {
        val today = slice(symptoms = listOf(WorseningDetector.IdValue("s1", 2)))
        val yesterday = slice(symptoms = listOf(WorseningDetector.IdValue("s1", 0)))
        assertTrue(WorseningDetector.detect(today, yesterday).worsening)
    }

    @Test
    fun warningHigherThanYesterday_isWorsening() {
        val today = slice(warnings = listOf(WorseningDetector.IdValue("w1", 3)))
        val yesterday = slice()
        assertTrue(WorseningDetector.detect(today, yesterday).worsening)
    }

    @Test
    fun missingKey_badgeEnabledByDefault() {
        assertTrue(WorseningDetector.isCrisisBadgeEnabled(null))
        assertTrue(WorseningDetector.isCrisisBadgeEnabled("true"))
        assertFalse(WorseningDetector.isCrisisBadgeEnabled("false"))
    }

    @Test
    fun emptyPlan_allBlank() {
        assertTrue(WorseningDetector.isCrisisPlanEmpty("", "  ", ""))
        assertFalse(WorseningDetector.isCrisisPlanEmpty("врач", "", ""))
        assertFalse(WorseningDetector.isCrisisPlanEmpty("", "", "", wishes = "позвонить маме"))
        assertFalse(WorseningDetector.isCrisisPlanEmpty("", "", "", avoid = "не спорить"))
    }
}
