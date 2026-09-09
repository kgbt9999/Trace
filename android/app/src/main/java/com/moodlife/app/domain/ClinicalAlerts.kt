package com.moodlife.app.domain

/**
 * Port of detectMixedEpisode / getSafetyAlert from web-reference/src/lib/mood.ts.
 */
object ClinicalAlerts {

    enum class MixedSeverity { MILD, MODERATE, SEVERE }

    enum class SafetyLevel { SAFE, CAUTION, WARNING, CRISIS }

    data class PhaseInfo(val label: String, val description: String)

    fun detectMixed(depressed: Int, elevated: Int): MixedSeverity? {
        if (depressed < 2 || elevated < 2) return null
        return when {
            depressed >= 3 && elevated >= 3 -> MixedSeverity.SEVERE
            (depressed >= 2 && elevated >= 3) || (depressed >= 3 && elevated >= 2) -> MixedSeverity.MODERATE
            else -> MixedSeverity.MILD
        }
    }

    fun safetyLevel(safetyCheck: Int): SafetyLevel = when {
        safetyCheck >= 3 -> SafetyLevel.CRISIS
        safetyCheck == 2 -> SafetyLevel.WARNING
        safetyCheck == 1 -> SafetyLevel.CAUTION
        else -> SafetyLevel.SAFE
    }

    fun phaseInfo(phase: String?): PhaseInfo? = when (phase) {
        null, "", "euthymic" -> null
        "prodromal_depression" -> PhaseInfo(
            "Продром депрессии",
            "Ранние признаки надвигающейся депрессии. Время для превентивных мер.",
        )
        "prodromal_mania" -> PhaseInfo(
            "Продром мании",
            "Ранние признаки надвигающейся мании/гипомании. Срочно: стабилизировать сон.",
        )
        "acute_depression" -> PhaseInfo(
            "Острая депрессия",
            "Активный депрессивный эпизод. Необходима консультация с врачом.",
        )
        "acute_mania" -> PhaseInfo(
            "Острый подъём",
            "Активный маниакальный/гипоманиакальный эпизод. Необходима консультация.",
        )
        "mixed" -> PhaseInfo(
            "Смешанный эпизод",
            "Опасное состояние: одновременные депрессивные и маниакальные симптомы. Наивысший риск. Немедленно к врачу!",
        )
        "recovery" -> PhaseInfo(
            "Восстановление",
            "Симптомы уменьшаются после эпизода. Поддерживающая терапия критически важна.",
        )
        else -> PhaseInfo(phase, "")
    }
}
