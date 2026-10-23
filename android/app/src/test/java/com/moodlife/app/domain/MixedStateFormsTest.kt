package com.moodlife.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MixedStateFormsTest {

    private val all = setOf("depressed", "elevated", "energy", "concentration")

    @Test
    fun `low mood, racing thoughts and restlessness read as depressive mania`() {
        val axes = MixedStateForms.axes(depressed = 3, elevated = 0, energy = 2, concentration = 3, marked = all,
            symptoms = listOf(MixedStateForms.Symptom("Двигательное беспокойство", 3, 5)))
        assertEquals("Депрессивная (тревожная) мания", MixedStateForms.match(axes)?.name)
    }

    @Test
    fun `high mood with no strength reads as inhibited mania`() {
        val axes = MixedStateForms.axes(depressed = 0, elevated = 3, energy = 0, concentration = 3, marked = all, symptoms = emptyList())
        assertEquals("Заторможенная мания", MixedStateForms.match(axes)?.name)
    }

    @Test
    fun `plain depression is not a mixed form`() {
        val axes = MixedStateForms.axes(depressed = 4, elevated = 0, energy = 0, concentration = 0, marked = all, symptoms = emptyList())
        assertNull(MixedStateForms.match(axes))
    }

    @Test
    fun `unmarked scales say nothing`() {
        // energy and concentration default to 0 but were not set by the user
        val axes = MixedStateForms.axes(depressed = 0, elevated = 3, energy = 0, concentration = 0,
            marked = setOf("depressed", "elevated"), symptoms = emptyList())
        assertNull(axes.thoughts)
        assertNull(MixedStateForms.match(axes))
    }
}
