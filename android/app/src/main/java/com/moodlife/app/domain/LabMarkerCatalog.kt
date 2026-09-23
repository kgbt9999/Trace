package com.moodlife.app.domain

import androidx.compose.ui.graphics.Color

data class LabNameSuggestion(
    val labelRu: String,
    val defaultUnit: String,
    val chartColor: Color,
)

/**
 * Suggested analysis names (not diagnoses). User can type any custom name.
 */
object LabMarkerCatalog {
    val suggestions: List<LabNameSuggestion> = listOf(
        LabNameSuggestion("ТТГ", "мЕд/л", Color(0xFF5B8DEF)),
        LabNameSuggestion("Креатинин", "мкмоль/л", Color(0xFF2BBFA0)),
        LabNameSuggestion("Кальций в крови", "ммоль/л", Color(0xFFE8A838)),
        LabNameSuggestion("Литий в крови", "ммоль/л", Color(0xFF9B7EBD)),
        LabNameSuggestion("Общий анализ мочи", "", Color(0xFF90A4AE)),
        LabNameSuggestion("Ферритин", "нг/мл", Color(0xFFE57373)),
        LabNameSuggestion("25-ОН витамин D", "нг/мл", Color(0xFF4DB6AC)),
        LabNameSuggestion("Т4 свободный", "пмоль/л", Color(0xFFFF8A65)),
        LabNameSuggestion("Витамин B12", "пг/мл", Color(0xFF7986CB)),
    )

    fun colorForName(name: String): Color {
        val n = name.trim().lowercase()
        suggestions.firstOrNull { it.labelRu.lowercase() == n }?.let { return it.chartColor }
        val palette = listOf(
            Color(0xFF5B8DEF), Color(0xFF2BBFA0), Color(0xFFE8A838), Color(0xFF9B7EBD),
            Color(0xFFE57373), Color(0xFF4DB6AC), Color(0xFFFF8A65), Color(0xFF7986CB),
        )
        val idx = (n.hashCode().and(0x7FFF_FFFF)) % palette.size
        return palette[idx]
    }

    fun defaultUnitFor(name: String): String =
        suggestions.firstOrNull { it.labelRu.equals(name.trim(), ignoreCase = true) }?.defaultUnit.orEmpty()
}
