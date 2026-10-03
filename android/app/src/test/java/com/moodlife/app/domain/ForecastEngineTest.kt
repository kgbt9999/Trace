package com.moodlife.app.domain

import com.moodlife.app.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {

    private fun history(days: Int, depressedEvery: Int = 4, warningDep: Boolean = false): List<ForecastEngine.HistDay> {
        val start = "2026-09-01"
        return ForecastEngine.buildHistory(
            (0 until days).map { i ->
                ForecastEngine.HistDayInput(
                    date = DateUtils.addDays(start, i.toLong()),
                    depressed = if (i % depressedEvery == 0) 3 else 0,
                    elevated = 0,
                    anxious = 1,
                    irritable = 0,
                    sleepQuality = 0,
                    sleepHours = 7.5f,
                    warningDep = warningDep && i % depressedEvery == depressedEvery - 1,
                )
            },
            lastPeriodStart = null,
            cycleLength = 28,
            periodLength = 5,
        )
    }

    @Test
    fun `a month of diary without warning signs or cycle still yields a forecast`() {
        val hist = history(28)
        val days = ForecastEngine.buildForecast("2026-09-29", hist, null, 28, 5, false, dayCount = 7)
        assertEquals(7, days.size)
        val p = days.first().probabilities
        assertNotNull(p.depression)
        assertNotNull(p.elevation)
        assertNotNull(days.first().score)
        // 7 of 28 days had depressed >= 2.
        assertEquals(0.25, p.depression!!, 0.05)
        assertEquals(0.0, p.elevation!!, 1e-9)
    }

    @Test
    fun `too few days stays empty`() {
        val days = ForecastEngine.buildForecast("2026-09-06", history(5), null, 28, 5, false, dayCount = 3)
        assertNull(days.first().probabilities.depression)
        assertNull(days.first().score)
    }

    @Test
    fun `warning signs preceding low days raise depression probability`() {
        val plain = ForecastEngine.buildForecast("2026-10-10", history(40), null, 28, 5, false, 1)
            .first().probabilities.depression!!
        val withSigns = ForecastEngine.buildForecast("2026-10-10", history(40, warningDep = true), null, 28, 5, false, 1)
            .first().probabilities.depression!!
        assertTrue("signs=$withSigns plain=$plain", withSigns > plain)
    }
}
