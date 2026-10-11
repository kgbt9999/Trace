package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProdromeFactorsTest {

    private val signs = DefaultSeedData.DEFAULT_EARLY_WARNING_SIGNS.mapIndexed { i, s ->
        ProdromeInference.SignRef("s$i", s.name, s.direction)
    }
    private fun idOf(namePart: String) = signs.first { it.name.contains(namePart) }.id

    @Test
    fun `sleep factors switch on the matching sleep prodromes`() {
        assertEquals(setOf(idOf("Сон менее")), ProdromeInference.matchSignIdsFromFactors(signs, listOf("Недосып")))
        assertEquals(setOf(idOf("Увеличение сна")), ProdromeInference.matchSignIdsFromFactors(signs, listOf("Долгий сон")))
    }

    @Test
    fun `shared words link a custom factor to a sign`() {
        assertTrue(idOf("изоляция") in ProdromeInference.matchSignIdsFromFactors(signs, listOf("Изоляция")))
    }

    @Test
    fun `unrelated factors switch nothing on`() {
        assertEquals(emptySet<String>(), ProdromeInference.matchSignIdsFromFactors(signs, listOf("Кофе", "Солнечно", "Спорт")))
    }
}
