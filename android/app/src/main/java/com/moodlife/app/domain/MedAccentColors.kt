package com.moodlife.app.domain

import androidx.compose.ui.graphics.Color

/**
 * Stable accent colors for medications — keyed by **normalized name** so duplicates
 * of the same drug share one color in calendar cells, legend, and reports.
 *
 * Collision strategy: hash into an expanded palette; when [assignDistinct] is used,
 * unique names get sequential slots (preferred for legends / export).
 */
object MedAccentColors {
    val palette: List<Color> = listOf(
        Color(0xFF5B8DEF),
        Color(0xFF2BBFA0),
        Color(0xFFE8A838),
        Color(0xFF9B7EBD),
        Color(0xFFE57373),
        Color(0xFF4DB6AC),
        Color(0xFFFF8A65),
        Color(0xFF7986CB),
        Color(0xFF26A69A),
        Color(0xFFEC407A),
        Color(0xFF7E57C2),
        Color(0xFF42A5F5),
        Color(0xFFFFA726),
        Color(0xFF66BB6A),
        Color(0xFFAB47BC),
        Color(0xFF26C6DA),
    )

    /** Hex strings matching [palette] for SVG / PDF export. */
    val paletteHex: List<String> = listOf(
        "#5B8DEF", "#2BBFA0", "#E8A838", "#9B7EBD",
        "#E57373", "#4DB6AC", "#FF8A65", "#7986CB",
        "#26A69A", "#EC407A", "#7E57C2", "#42A5F5",
        "#FFA726", "#66BB6A", "#AB47BC", "#26C6DA",
    )

    fun normalizeName(name: String): String = name.trim().lowercase()

    /** Preferred: color by display name (same name → same color). */
    fun accentForName(name: String): Color {
        val key = normalizeName(name)
        if (key.isEmpty()) return palette[0]
        val idx = (key.hashCode().and(0x7FFF_FFFF)) % palette.size
        return palette[idx]
    }

    /**
     * Collision-free within [allNames]: sorted unique names get sequential palette slots.
     * Falls back to [accentForName] when [allNames] is empty.
     */
    fun accentForName(name: String, allNames: Collection<String>): Color {
        if (allNames.isEmpty()) return accentForName(name)
        return accentForNameInSet(name, assignDistinct(allNames))
    }

    fun accentHexForName(name: String): String {
        val key = normalizeName(name)
        if (key.isEmpty()) return paletteHex[0]
        val idx = (key.hashCode().and(0x7FFF_FFFF)) % paletteHex.size
        return paletteHex[idx]
    }

    fun accentHexForName(name: String, allNames: Collection<String>): String {
        if (allNames.isEmpty()) return accentHexForName(name)
        val assigned = assignDistinctHex(allNames)
        val key = normalizeName(name)
        return assigned[key] ?: accentHexForName(name)
    }

    fun assignDistinctHex(names: Collection<String>): Map<String, String> {
        val keys = names.map { normalizeName(it) }.filter { it.isNotEmpty() }.distinct().sorted()
        return keys.mapIndexed { i, key ->
            key to paletteHex[i % paletteHex.size]
        }.toMap()
    }

    /**
     * Assign distinct palette slots by sorted unique names (minimizes collisions in a set).
     */
    fun assignDistinct(names: Collection<String>): Map<String, Color> {
        val keys = names.map { normalizeName(it) }.filter { it.isNotEmpty() }.distinct().sorted()
        return keys.mapIndexed { i, key ->
            key to palette[i % palette.size]
        }.toMap()
    }

    fun accentForNameInSet(name: String, assigned: Map<String, Color>): Color {
        val key = normalizeName(name)
        return assigned[key] ?: accentForName(name)
    }

    /**
     * Legacy id-based accent. Prefer [accentForName] for UI that groups by drug name.
     */
    fun accentFor(medicationId: String): Color {
        val idx = (medicationId.hashCode().and(0x7FFF_FFFF)) % palette.size
        return palette[idx]
    }

    fun glyphFor(name: String): String {
        val ch = name.trim().firstOrNull { it.isLetterOrDigit() } ?: '·'
        return ch.uppercaseChar().toString()
    }
}
