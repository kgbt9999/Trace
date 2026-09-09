package com.moodlife.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeClassifierTest {

    private fun input(
        depressed: Int = 0,
        elevated: Int = 0,
        anxious: Int = 0,
        irritable: Int = 0,
        energy: Int = 0,
        concentration: Int = 0,
        sleepHours: Float? = null,
        sleepQuality: Int = 0,
        functioning: Int = 5,
    ) = EpisodeClassifier.ClassifyInput(
        depressed = depressed,
        elevated = elevated,
        anxious = anxious,
        irritable = irritable,
        energy = energy,
        concentration = concentration,
        sleepHours = sleepHours,
        sleepQuality = sleepQuality,
        functioning = functioning,
    )

    @Test
    fun baselineNormalDay_returnsEuthymic() {
        assertEquals(
            EpisodeClassifier.EpisodePhase.EUTHYMIC,
            EpisodeClassifier.classify(input(energy = 2, concentration = 2, functioning = 7)),
        )
    }

    @Test
    fun depressedAndElevatedBoth2_returnsMixed() {
        assertEquals(
            EpisodeClassifier.EpisodePhase.MIXED,
            EpisodeClassifier.classify(input(depressed = 2, elevated = 2)),
        )
    }

    @Test
    fun elevated4_returnsAcuteMania() {
        assertEquals(
            EpisodeClassifier.EpisodePhase.ACUTE_MANIA,
            EpisodeClassifier.classify(input(elevated = 4)),
        )
    }

    @Test
    fun depressed3WithSymptoms_returnsAcuteDepression() {
        assertEquals(
            EpisodeClassifier.EpisodePhase.ACUTE_DEPRESSION,
            EpisodeClassifier.classify(
                input(depressed = 3, energy = 0, concentration = 0, sleepQuality = 2, functioning = 2),
            ),
        )
    }

    @Test
    fun mildSymptoms_returnsRecovery() {
        assertEquals(
            EpisodeClassifier.EpisodePhase.RECOVERY,
            EpisodeClassifier.classify(input(depressed = 1, energy = 2, concentration = 2, functioning = 7)),
        )
    }
}
