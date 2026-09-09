package com.moodlife.app.domain

import androidx.compose.ui.graphics.Color
import com.moodlife.app.util.DateUtils
import java.time.temporal.ChronoUnit

object CycleUtils {

    enum class CyclePhase { MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL, PMS }

    data class PhaseInfo(val label: String, val emoji: String, val description: String, val color: Color)

    data class CycleMarkers(
        val phase: CyclePhase,
        val dayOfCycle: Int,
        val daysUntilNext: Int,
        val isPeriod: Boolean,
        val isFertile: Boolean,
        val isOvulation: Boolean,
        val isPms: Boolean,
    )

    val PHASE_INFO = mapOf(
        CyclePhase.MENSTRUAL to PhaseInfo("Менструация", "🌙", "Дни кровотечения. Энергия может быть снижена.", Color(0xFFEC4899)),
        CyclePhase.FOLLICULAR to PhaseInfo("Фолликулярная", "🌱", "После менструации до окна фертильности.", Color(0xFF10B981)),
        CyclePhase.OVULATION to PhaseInfo("Овуляция (оценка)", "✨", "Оценка вокруг середины цикла.", Color(0xFFF59E0B)),
        CyclePhase.LUTEAL to PhaseInfo("Лютеиновая", "🍂", "После овуляции до следующей менструации.", Color(0xFF6366F1)),
        CyclePhase.PMS to PhaseInfo("ПМС", "💭", "Последние дни цикла — возможны перепады.", Color(0xFFEF4444)),
    )

    fun avgMood(depressed: Int, elevated: Int, anxious: Int, irritable: Int): Double =
        (depressed + elevated + anxious + irritable) / 4.0

    fun moodTintColor(severity: Double, alpha: Float = 0.35f): Color {
        val base = when {
            severity < 0.5 -> Color(0xFFE2E8F0)
            severity < 1.5 -> Color(0xFFBAE6FD)
            severity < 2.5 -> Color(0xFF7DD3FC)
            severity < 3.5 -> Color(0xFF38BDF8)
            severity < 4.5 -> Color(0xFF0EA5E9)
            else -> Color(0xFF0369A1)
        }
        return base.copy(alpha = alpha)
    }

    fun calcCyclePhase(
        lastPeriodStart: String,
        cycleLength: Int,
        periodLength: Int,
        targetDate: String,
        irregular: Boolean = false,
    ): CycleMarkers {
        val diffDays = ChronoUnit.DAYS.between(DateUtils.parseIso(lastPeriodStart), DateUtils.parseIso(targetDate)).toInt()
        val len = cycleLength.coerceIn(15, 60).let { if (it == 0) 28 else it }
        val bleed = periodLength.coerceIn(1, 14).let { if (it == 0) 5 else it }
        val dayOfCycle = ((diffDays % len) + len) % len + 1

        val ovDay = if (irregular) (len * 0.5).toInt() else (len / 2.0).toInt()
        val fertilePad = if (irregular) 4 else 2
        val fertileStart = maxOf(bleed + 1, ovDay - if (irregular) 6 else 5)
        val fertileEnd = minOf(len - 3, ovDay + fertilePad)
        val pmsStart = maxOf(ovDay + 2, len - 5)

        val isPeriod = dayOfCycle <= bleed
        val isOvulation = dayOfCycle in (ovDay - if (irregular) 1 else 0)..(ovDay + 1)
        val isFertile = !isPeriod && dayOfCycle in fertileStart..fertileEnd
        val isPms = !isPeriod && dayOfCycle >= pmsStart

        val phase = when {
            isPeriod -> CyclePhase.MENSTRUAL
            isOvulation -> CyclePhase.OVULATION
            isPms -> CyclePhase.PMS
            dayOfCycle < ovDay -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
        return CycleMarkers(phase, dayOfCycle, len - dayOfCycle + 1, isPeriod, isFertile, isOvulation, isPms)
    }
}
