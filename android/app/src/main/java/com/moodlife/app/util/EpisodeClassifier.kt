package com.moodlife.app.util

/**
 * Auto-classify episode phase — port of `classifyEpisodePhase` from web-reference/src/lib/mood.ts.
 * Algorithm: DSM-5-TR + CANMAT/ISBD 2023 + Barcelona Program.
 */
object EpisodeClassifier {

    enum class EpisodePhase {
        EUTHYMIC,
        PRODROMAL_DEPRESSION,
        PRODROMAL_MANIA,
        ACUTE_DEPRESSION,
        ACUTE_MANIA,
        MIXED,
        RECOVERY,
    }

    data class ClassifyInput(
        val depressed: Int,
        val elevated: Int,
        val anxious: Int,
        val irritable: Int,
        val energy: Int,
        val concentration: Int,
        val sleepHours: Float?,
        val sleepQuality: Int,
        val functioning: Int,
    )

    fun classify(input: ClassifyInput): EpisodePhase {
        val depressed = input.depressed
        val elevated = input.elevated
        val irritable = input.irritable
        val energy = input.energy
        val concentration = input.concentration
        val sleepHours = input.sleepHours
        val sleepQuality = input.sleepQuality
        val functioning = input.functioning
        val anxious = input.anxious

        if (depressed >= 2 && elevated >= 2) return EpisodePhase.MIXED

        if (elevated >= 3 || (elevated >= 2 && irritable >= 3)) {
            if (sleepHours != null && sleepHours < 5f && energy >= 2) return EpisodePhase.ACUTE_MANIA
            if (elevated >= 4) return EpisodePhase.ACUTE_MANIA
            if (irritable >= 4 && energy >= 2) return EpisodePhase.ACUTE_MANIA
        }

        if (depressed >= 3) {
            val symptomCount = listOf(
                depressed >= 2,
                energy <= 1,
                concentration <= 1,
                sleepQuality >= 2,
                functioning <= 3,
            ).count { it }
            if (symptomCount >= 3) return EpisodePhase.ACUTE_DEPRESSION
        }

        if (elevated >= 2 || energy >= 3) {
            val maniaSigns = listOf(
                sleepHours != null && sleepHours < 6f,
                sleepQuality >= 1,
                concentration <= 1,
                irritable >= 2,
            ).count { it }
            if (maniaSigns >= 2) return EpisodePhase.PRODROMAL_MANIA
        }

        if (depressed >= 2 || energy <= 1) {
            val depSigns = listOf(
                sleepHours != null && sleepHours > 9f,
                concentration <= 1,
                functioning <= 5,
                anxious >= 2,
                sleepQuality >= 2,
            ).count { it }
            if (depSigns >= 2) return EpisodePhase.PRODROMAL_DEPRESSION
        }

        if (depressed >= 1 || elevated >= 1 || anxious >= 2) {
            return EpisodePhase.RECOVERY
        }

        return EpisodePhase.EUTHYMIC
    }

    fun toStorageKey(phase: EpisodePhase): String = when (phase) {
        EpisodePhase.EUTHYMIC -> "euthymic"
        EpisodePhase.PRODROMAL_DEPRESSION -> "prodromal_depression"
        EpisodePhase.PRODROMAL_MANIA -> "prodromal_mania"
        EpisodePhase.ACUTE_DEPRESSION -> "acute_depression"
        EpisodePhase.ACUTE_MANIA -> "acute_mania"
        EpisodePhase.MIXED -> "mixed"
        EpisodePhase.RECOVERY -> "recovery"
    }
}
