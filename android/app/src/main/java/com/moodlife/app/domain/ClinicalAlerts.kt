package com.moodlife.app.domain

/**
 * Diary-scale attention cues derived from user-entered axes.
 * Not a diagnosis and not therapy advice.
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

    /**
     * Labels describe diary patterns only — never episode diagnosis or medication changes.
     */
    fun phaseInfo(phase: String?): PhaseInfo? = when (phase) {
        null, "", "euthymic" -> null
        "prodromal_depression" -> PhaseInfo(
            "Ранние признаки спада (дневник)",
            "По вашим шкалам заметны ранние изменения в сторону спада. Имеет смысл внимательнее вести дневник и при необходимости обсудить это с врачом.",
        )
        "prodromal_mania" -> PhaseInfo(
            "Ранние признаки подъёма (дневник)",
            "По вашим шкалам заметны ранние изменения в сторону подъёма. Обратите внимание на сон и режим; при необходимости обсудите записи с врачом.",
        )
        "acute_depression" -> PhaseInfo(
            "Выраженный спад (дневник)",
            "Шкалы дневника показывают выраженный спад. Это самоотчёт, не диагноз. При ухудшении обратитесь к врачу или в кризисную службу.",
        )
        "acute_mania" -> PhaseInfo(
            "Выраженный подъём (дневник)",
            "Шкалы дневника показывают выраженный подъём. Это самоотчёт, не диагноз. При ухудшении обратитесь к врачу или в кризисную службу.",
        )
        "mixed" -> PhaseInfo(
            "Одновременно спад и подъём (дневник)",
            "В записях одновременно высокие шкалы спада и подъёма. Это самоотчёт, не диагноз. При сильном дискомфорте или мыслях о вреде себе — к врачу или в экстренную помощь.",
        )
        "recovery" -> PhaseInfo(
            "Ослабление интенсивности (дневник)",
            "Интенсивность отметок снижается относительно недавних дней. Продолжайте наблюдения; решения о лечении принимает только врач.",
        )
        else -> PhaseInfo(phase, "")
    }
}
