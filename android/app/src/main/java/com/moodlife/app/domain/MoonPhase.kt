package com.moodlife.app.domain

import com.moodlife.app.util.DateUtils
import java.time.ZoneOffset

/** Port of web-reference/src/lib/moon.ts — correlation marker, not causation. */
object MoonPhaseCalc {

    data class MoonPhase(
        val id: String,
        /** 0 = new, 0.5 = full. */
        val age: Double,
        val label: String,
        val shortLabel: String,
        val icon: String,
    )

    private data class Meta(
        val id: String,
        val label: String,
        val shortLabel: String,
        val icon: String,
    )

    private val PHASES = listOf(
        Meta("new", "Новолуние", "Новая", "🌑"),
        Meta("waxing-crescent", "Растущий серп", "Растёт", "🌒"),
        Meta("first-quarter", "Первая четверть", "¼", "🌓"),
        Meta("waxing-gibbous", "Растущая", "Растёт", "🌔"),
        Meta("full", "Полнолуние", "Полная", "🌕"),
        Meta("waning-gibbous", "Убывающая", "Убывает", "🌖"),
        Meta("last-quarter", "Последняя четверть", "¾", "🌗"),
        Meta("waning-crescent", "Убывающий серп", "Убывает", "🌘"),
    )

    private const val SYNODIC = 29.530588853
    private const val NEW_MOON_JD = 2451550.1

    fun moonPhase(isoDate: String): MoonPhase {
        val jd = julianDayAtNoon(isoDate)
        var t = ((jd - NEW_MOON_JD) / SYNODIC) % 1.0
        if (t < 0) t += 1.0
        val idx = (Math.round(t * 8).toInt() % 8 + 8) % 8
        val meta = PHASES[idx]
        return MoonPhase(meta.id, t, meta.label, meta.shortLabel, meta.icon)
    }

    private fun julianDayAtNoon(isoDate: String): Double {
        val d = DateUtils.parseIso(isoDate)
        val utcMillis = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return utcMillis / 86_400_000.0 + 2_440_587.5
    }
}
