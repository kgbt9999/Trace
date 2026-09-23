package com.moodlife.app.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.LabResultEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.repository.MedDoseSeries
import com.moodlife.app.domain.CaseHistorySummary
import com.moodlife.app.domain.LabMarkerCatalog
import com.moodlife.app.util.DateUtils
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Printable monthly PDF with KPI cards, charts, case-history table and daily journal. */
internal class PdfReportRenderer(
    private val context: Context,
) {
    private val navy = Color.parseColor("#1A1D23")
    private val teal = Color.parseColor("#2A78D6")
    private val mint = Color.parseColor("#1A9E6E")
    private val ink = Color.parseColor("#1A1D23")
    private val muted = Color.parseColor("#5A6070")
    private val card = Color.parseColor("#FFFFFF")
    private val line = Color.parseColor("#E2E6ED")
    private val white = Color.WHITE
    private val pageBg = Color.parseColor("#F7F8FA")
    private val dep = Color.parseColor("#3A7FBA")
    private val elev = Color.parseColor("#E55D3C")
    private val anx = Color.parseColor("#E8A020")
    private val irr = Color.parseColor("#EB6834")
    private val sleepC = Color.parseColor("#2A78D6")
    private val energyC = Color.parseColor("#1A9E6E")
    private val funcC = Color.parseColor("#5A6070")

    private val pageW = 595
    private val pageH = 842
    private val mL = 40f
    private val mR = 40f
    private val contentW = pageW - mL - mR
    private val footerReserve = 44f
    private val headerBottom = 78f



    fun write(
        file: File,
        year: Int,
        month: Int,
        entries: List<MoodEntryEntity>,
        medsByDay: List<Pair<String, List<String>>> = emptyList(),
        medDoseSeries: List<MedDoseSeries> = emptyList(),
        labResults: List<LabResultEntity> = emptyList(),
        earlySignFrequency: List<Pair<String, Int>> = emptyList(),
        noteExcerpts: List<Pair<String, String>> = emptyList(),
        medAdherence: List<Triple<String, String, Int>> = emptyList(),
        caseHistoryRows: List<CaseHistorySummary.Row> = emptyList(),
        crisisDoctor: String = "",
        crisisSupport: String = "",
        crisisNotes: String = "",
        crisisWishes: String = "",
        crisisAvoid: String = "",
        crisisContacts: List<Pair<String, String>> = emptyList(),
    ) {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy; textSize = 14f; isFakeBoldText = true
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 9.5f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 8f }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy; textSize = 11f; isFakeBoldText = true
        }
        val section = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B"); textSize = 9f; isFakeBoldText = true
        }
        val kpiValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy; textSize = 20f; isFakeBoldText = true
        }
        val labelCaps = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 7f; isFakeBoldText = true
        }

        val monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year"
        val exported = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())
        val n = entries.size
        val polarityVals = entries.map { (it.elevated - it.depressed).toFloat().coerceIn(-5f, 5f) }
        val avgPolarity = if (polarityVals.isEmpty()) "—"
        else String.format(Locale("ru"), "%+.1f", polarityVals.average())
        val polMin = polarityVals.minOrNull()?.let { String.format(Locale("ru"), "%+.0f", it) } ?: "—"
        val polMax = polarityVals.maxOrNull()?.let { String.format(Locale("ru"), "%+.0f", it) } ?: "—"
        val sleepBadDays = entries.count { (it.sleepHours ?: 8f) < 6f }
        val sleepBadPct = if (n == 0) 0 else sleepBadDays * 100 / n
        val adherencePct = medAdherence.takeIf { it.isNotEmpty() }?.map { it.third }?.average()?.toInt()
        val adherenceLabel = adherencePct?.let { "$it%" } ?: "—"
        val missedSlots = medAdherence.sumOf { triple ->
            val parts = triple.second.split("/", limit = 2)
            if (parts.size == 2) {
                val taken = parts[0].trim().toIntOrNull() ?: 0
                val sched = parts[1].trim().toIntOrNull() ?: 0
                (sched - taken).coerceAtLeast(0)
            } else 0
        }
        val earlyCount = earlySignFrequency.sumOf { it.second }
        val avgPol = polarityVals.average().takeIf { polarityVals.isNotEmpty() }
        val riskLoad = sleepBadDays + earlyCount + when {
            avgPol == null -> 0
            kotlin.math.abs(avgPol) >= 2.0 -> 3
            kotlin.math.abs(avgPol) >= 1.0 -> 1
            else -> 0
        }
        val (riskLevel, riskTitle, riskDesc) = when {
            n == 0 -> Triple(
                "low", "Наблюдение: нет записей",
                "За период нет дневниковых отметок. Заполните дни для мониторинга.",
            )
            riskLoad >= 10 -> Triple(
                "high", "Наблюдение: высокая нагрузка отметок",
                "Много дней с коротким сном и/или частые отметки ранних признаков. Это самоотчёт, не диагноз.",
            )
            riskLoad >= 4 -> Triple(
                "mid", "Наблюдение: умеренная нагрузка отметок",
                "Есть дни с коротким сном и/или ранние признаки. Эпизод приложением не определяется.",
            )
            else -> Triple(
                "low", "Наблюдение: низкая нагрузка отметок",
                "По дневнику меньше сигналов короткого сна и ранних признаков. Это не клиническое заключение.",
            )
        }
        val riskBg = when (riskLevel) {
            "high" -> Color.parseColor("#FEF0EF")
            "mid" -> Color.parseColor("#FEF8EC")
            else -> Color.parseColor("#EDFAF4")
        }
        val riskBorder = when (riskLevel) {
            "high" -> Color.parseColor("#F5ABA8")
            "mid" -> Color.parseColor("#F5D080")
            else -> Color.parseColor("#A4DFC4")
        }
        val riskDot = when (riskLevel) {
            "high" -> Color.parseColor("#C8342A")
            "mid" -> Color.parseColor("#E8A020")
            else -> Color.parseColor("#1A9E6E")
        }
        val doctorName = crisisDoctor.trim().ifBlank { "—" }
        val disclaimerLines = listOf(
            "Самоотчёт пользователя. Не диагноз и не клиническая оценка.",
            "Не рекомендация начинать, прекращать или менять терапию.",
            "Ранние признаки — частота отметок, не эпизод.",
        )

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var c = page.canvas

        fun finishAndStart(): Float {
            drawFooter(c, small, pageNum)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            c = page.canvas
            drawHeader(c, titlePaint, small, monthLabel, exported, n)
            return headerBottom + 14f
        }

        fun ensure(space: Float, y: Float): Float =
            if (y + space > pageH - footerReserve) finishAndStart() else y

        fun sectionTitle(num: String, title: String, yIn: Float): Float {
            var y = ensure(28f, yIn)
            val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal }
            c.drawCircle(mL + 8f, y - 3f, 8f, badge)
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 8f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            }
            c.drawText(num, mL + 8f, y, numPaint)
            c.drawText(title.uppercase(Locale("ru")), mL + 24f, y, section)
            y += 6f
            c.drawLine(mL, y, mL + contentW, y, Paint().apply { color = line; strokeWidth = 1f })
            return y + 12f
        }

        // ── Page 1 ──
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        var y = headerBottom + 14f

        // Patient meta — 2 rows × 3 fields (readable, no overlap)
        val meta = listOf(
            "Пациент" to "—",
            "Дата рождения" to "—",
            "Диагноз" to "не заполняется приложением",
            "Лечащий врач" to doctorName,
            "Приверженность" to adherenceLabel,
            "Период" to monthLabel,
        )
        val metaCols = 3
        val metaGap = 10f
        val metaW = (contentW - metaGap * (metaCols - 1)) / metaCols
        val metaRowH = 34f
        val metaRows = (meta.size + metaCols - 1) / metaCols
        val metaBoxH = 10f + metaRows * metaRowH + 6f
        drawCard(c, RectF(mL, y, mL + contentW, y + metaBoxH))
        meta.forEachIndexed { i, (lab, value) ->
            val col = i % metaCols
            val row = i / metaCols
            val x = mL + 10f + col * (metaW + metaGap)
            val yy = y + 14f + row * metaRowH
            c.drawText(lab.uppercase(Locale("ru")), x, yy, labelCaps)
            c.drawText(ellipsize(value, body, metaW - 16f), x, yy + 14f, Paint(body).apply {
                color = ink; isFakeBoldText = true
            })
        }
        y += metaBoxH + 12f

        y = drawDisclaimerBox(c, y, disclaimerLines, small, body)
        y += 14f

        y = sectionTitle("↑", "Ключевые показатели периода", y)
        val kpiItems = listOf(
            Triple("Среднее настроение", avgPolarity, "Диапазон $polMin…$polMax · шкала −5…+5"),
            Triple("Нарушений сна", "$sleepBadDays", "Из $n дней ($sleepBadPct%) · сон <6 ч"),
            Triple("Приём лекарств", adherenceLabel, if (missedSlots > 0) "$missedSlots пропусков слотов" else "по отметкам дневника"),
        )
        val kpiGap = 10f
        val kpiW = (contentW - kpiGap * 2) / 3f
        val kpiH = 72f
        kpiItems.forEachIndexed { i, (lab, value, sub) ->
            val x = mL + i * (kpiW + kpiGap)
            drawCard(c, RectF(x, y, x + kpiW, y + kpiH))
            c.drawText(lab, x + 10f, y + 16f, small)
            c.drawText(value, x + 10f, y + 40f, kpiValue)
            val subLines = wrapText(sub, Paint(small).apply { textSize = 7.5f }, kpiW - 20f)
            var sy = y + 52f
            for (line in subLines.take(2)) {
                c.drawText(line, x + 10f, sy, Paint(small).apply { textSize = 7.5f })
                sy += 10f
            }
        }
        y += kpiH + 12f

        // Risk — height from wrapped text
        val riskInnerW = contentW - 44f
        val riskTitleLines = wrapText(riskTitle, Paint(body).apply { isFakeBoldText = true }, riskInnerW)
        val riskDescLines = wrapText(riskDesc, small, riskInnerW)
        val riskH = 16f + riskTitleLines.size * 12f + riskDescLines.size * 11f + 10f
        y = ensure(riskH + 8f, y)
        c.drawRoundRect(RectF(mL, y, mL + contentW, y + riskH), 8f, 8f, Paint().apply { color = riskBg })
        c.drawRoundRect(
            RectF(mL, y, mL + contentW, y + riskH), 8f, 8f,
            Paint().apply { color = riskBorder; style = Paint.Style.STROKE; strokeWidth = 1.2f },
        )
        c.drawCircle(mL + 16f, y + 18f, 5f, Paint().apply { color = riskDot })
        var ry = y + 16f
        for (line in riskTitleLines) {
            c.drawText(line, mL + 30f, ry, Paint(body).apply { color = ink; isFakeBoldText = true })
            ry += 12f
        }
        for (line in riskDescLines) {
            c.drawText(line, mL + 30f, ry, small)
            ry += 11f
        }
        y += riskH + 16f

        y = sectionTitle("1", "Динамика настроения (−5…+5)", y)
        c.drawText("Красная зона ≥+2 · синяя ≤−2 · самоотчёт, не диагноз", mL, y, small)
        y += 10f
        val dayLabs = entries.map { it.date.substring(8) }
        val polH = 155f
        y = ensure(polH + 24f, y)
        val polBox = RectF(mL, y, mL + contentW, y + polH)
        drawPolarityBars(c, polBox, polarityVals, 5f, dayLabs, small)
        y = polBox.bottom + 8f
        c.drawText(
            "Диапазон $polMin…$polMax. Визуализация шкал дневника, не диагноз.",
            mL, y, Paint(small).apply { textSize = 7.5f },
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // ── Page 2: charts stacked full-width ──
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        y = headerBottom + 14f

        y = sectionTitle("2", "Сон и настроение — двойная ось", y)
        c.drawText("Столбцы — часы сна · линия — полярность · ориентир сна 7–9 ч", mL, y, small)
        y += 10f
        val dualH = 150f
        val dualBox = RectF(mL, y, mL + contentW, y + dualH)
        drawSleepMoodDual(c, dualBox, entries, small)
        y = dualBox.bottom + 8f
        val sleepAvg = entries.mapNotNull { it.sleepHours }.average().takeIf { !it.isNaN() }
        c.drawText(
            if (sleepAvg == null) "Нет отметок часов сна."
            else "Средний сон ${String.format(Locale("ru"), "%.1f", sleepAvg)} ч. Дней <6 ч: $sleepBadDays.",
            mL, y, Paint(small).apply { textSize = 7.5f },
        )
        y += 18f

        y = sectionTitle("3", "Профиль симптомов — последние 7 дней", y)
        val last7 = entries.takeLast(7)
        val radarSrc = last7.ifEmpty { entries }
        fun norm(max: Float = 5f, sel: (MoodEntryEntity) -> Int): Float {
            if (radarSrc.isEmpty()) return 0f
            return (radarSrc.map(sel).average().toFloat() / max).coerceIn(0f, 1f)
        }
        val radarAxes = listOf(
            "Тревога" to norm { it.anxious },
            "Раздраж." to norm { it.irritable },
            "Энергия" to norm(3f) { it.energy },
            "Сон↓" to run {
                val avgH = radarSrc.mapNotNull { it.sleepHours }.average().takeIf { !it.isNaN() }?.toFloat()
                when {
                    avgH == null -> 0f
                    avgH in 7f..9f -> 0f
                    avgH in 6f..10f -> 0.4f
                    else -> 0.8f
                }
            },
            "Концентр." to norm(3f) { it.concentration },
            "Аппетит" to norm(3f) { it.appetite },
            "Подавл." to norm { it.depressed },
            "Подъём" to norm { it.elevated },
        )
        val radarH = 200f
        y = ensure(radarH + 20f, y)
        // Center radar in a card
        drawCard(c, RectF(mL, y, mL + contentW, y + radarH))
        val radarBox = RectF(mL + 40f, y + 8f, mL + contentW - 40f, y + radarH - 8f)
        drawRadar(c, radarBox, radarAxes, small)
        y += radarH + 8f
        c.drawText(
            "Средние значения шкал за ${radarSrc.size} дн. Не клинический профиль.",
            mL, y, Paint(small).apply { textSize = 7.5f },
        )
        y += 16f

        y = sectionTitle("4", "Тепловая карта настроения", y)
        val heatH = 130f
        y = ensure(heatH + 20f, y)
        val heatBox = RectF(mL, y, mL + contentW, y + heatH)
        drawPolarityCalendarHeatmap(c, heatBox, entries, year, month, small)
        y = heatBox.bottom + 8f
        c.drawText(
            "Цвет = полярность (подъём − спад) по дням. Самоотчёт.",
            mL, y, Paint(small).apply { textSize = 7.5f },
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // ── Page 3: early signs / meds / history ──
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        y = headerBottom + 14f

        y = sectionTitle("5", "Триггеры и ранние признаки", y)
        c.drawText("Ранние признаки (частота отметок)", mL, y, Paint(body).apply {
            color = ink; isFakeBoldText = true
        })
        y += 14f
        if (earlySignFrequency.isEmpty()) {
            c.drawText("Нет отметок ранних признаков за период.", mL, y, body)
            y += 16f
        } else {
            val maxCnt = earlySignFrequency.maxOf { it.second }.coerceAtLeast(1)
            val labelColW = 150f
            val barStart = mL + labelColW + 8f
            val barEnd = mL + contentW - 28f
            for ((name, cnt) in earlySignFrequency.take(10)) {
                y = ensure(18f, y)
                c.drawText(ellipsize(name, body, labelColW), mL, y, body)
                val barW = (barEnd - barStart) * (cnt.toFloat() / maxCnt)
                c.drawRoundRect(
                    RectF(barStart, y - 7f, barStart + barW.coerceAtLeast(4f), y + 3f),
                    3f, 3f,
                    Paint().apply { color = teal },
                )
                c.drawText("$cnt", barStart + barW + 6f, y, small)
                y += 15f
            }
            y += 4f
            c.drawText(
                "Доля дней с отметкой. Не вероятность эпизода и не диагноз.",
                mL, y, Paint(small).apply { textSize = 7.5f },
            )
            y += 14f
        }

        if (sleepBadDays > 0 || missedSlots > 0) {
            y = ensure(40f, y)
            c.drawText("Наблюдаемые триггеры (из дневника)", mL, y, Paint(body).apply {
                color = ink; isFakeBoldText = true
            })
            y += 14f
            if (sleepBadDays > 0) {
                c.drawCircle(mL + 5f, y - 3f, 3.5f, Paint().apply { color = anx })
                c.drawText("Короткий сон (<6 ч) — $sleepBadDays дн.", mL + 14f, y, body)
                y += 14f
            }
            if (missedSlots > 0) {
                c.drawCircle(mL + 5f, y - 3f, 3.5f, Paint().apply { color = Color.parseColor("#C8342A") })
                c.drawText("Пропуски слотов приёма — $missedSlots", mL + 14f, y, body)
                y += 14f
            }
            y += 6f
        }

        y = sectionTitle("6", "Приём лекарств — период отчёта", y)
        if (medAdherence.isEmpty()) {
            c.drawText("Нет записей приёма за период.", mL, y, body)
            y += 16f
        } else {
            val colName = contentW * 0.46f
            val colRatio = contentW * 0.34f
            y = ensure(20f, y)
            c.drawRoundRect(
                RectF(mL, y - 11f, mL + contentW, y + 5f),
                4f, 4f,
                Paint().apply { color = Color.parseColor("#EEF2F7") },
            )
            c.drawText("Препарат", mL + 8f, y, Paint(small).apply { isFakeBoldText = true })
            c.drawText("Принято / слотов", mL + colName, y, Paint(small).apply { isFakeBoldText = true })
            c.drawText("%", mL + colName + colRatio, y, Paint(small).apply { isFakeBoldText = true })
            y += 14f
            medAdherence.take(14).forEachIndexed { idx, (name, ratio, pct) ->
                y = ensure(16f, y)
                if (idx % 2 == 0) {
                    c.drawRect(mL, y - 10f, mL + contentW, y + 4f, Paint().apply {
                        color = Color.parseColor("#F8FAFC")
                    })
                }
                c.drawText(ellipsize(name, body, colName - 12f), mL + 8f, y, body)
                c.drawText(ratio, mL + colName, y, body)
                c.drawText("$pct%", mL + colName + colRatio, y, body)
                y += 14f
            }
            y += 6f
            c.drawText(
                "Самоотчёт пользователя, не оценка терапии и не совет менять схему.",
                mL, y, Paint(small).apply { textSize = 7.5f },
            )
            y += 14f
        }

        val doseNonEmpty = medDoseSeries.filter { it.points.isNotEmpty() }
        if (doseNonEmpty.isNotEmpty()) {
            y = ensure(140f, y)
            c.drawText("Дозы по дням (мг)", mL, y, Paint(body).apply {
                color = ink; isFakeBoldText = true
            })
            y += 10f
            val doseMax = (doseNonEmpty.flatMap { it.points.map { p -> p.second } }.maxOrNull() ?: 1f)
                .coerceAtLeast(1f) * 1.1f
            val palette = DoctorExportGenerator.MED_DOSE_PALETTE_HEX.map { Color.parseColor(it) }
            drawSparseLines(
                c,
                RectF(mL, y, mL + contentW, y + 110f),
                doseNonEmpty.take(5).mapIndexed { i, s ->
                    SparseSeries(s.name, palette[i % palette.size], s.points)
                },
                maxY = doseMax,
                small = small,
            )
            y += 124f
        }

        y = sectionTitle("7", "Таблица истории по шкалам", y)
        c.drawText("Агрегаты дневника (не типы эпизодов)", mL, y, small)
        y += 12f
        val historyRows = caseHistoryRows.ifEmpty {
            CaseHistorySummary.build(CaseHistorySummary.Inputs(entries = entries), minDays = 3)
        }
        // If table won't fit, start fresh page
        y = ensure(120f, y)
        y = drawCaseHistoryTable(c, y, historyRows, body, small, heading)
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // ── Page 4: crisis + notes ──
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        y = headerBottom + 14f

        y = sectionTitle("8", "Кризисный план (из настроек)", y)
        y = drawCrisisLevels(
            c = c,
            yStart = y,
            body = body,
            small = small,
            doctor = doctorName,
            support = crisisSupport,
            notes = crisisNotes,
            wishes = crisisWishes,
            avoid = crisisAvoid,
            contacts = crisisContacts,
            ensureSpace = { need, cur -> ensure(need, cur) },
        )
        y += 12f

        if (labResults.isNotEmpty()) {
            y = ensure(36f, y)
            c.drawText("Лабораторные показатели (самоотчёт)", mL, y, Paint(body).apply {
                color = ink; isFakeBoldText = true
            })
            y += 12f
            for (row in labResults.take(8)) {
                y = ensure(14f, y)
                val unit = row.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                c.drawText(
                    ellipsize("${formatDateShort(row.date)}  ${row.name}: ${row.valueText}$unit", body, contentW),
                    mL, y, body,
                )
                y += 13f
            }
            y += 8f
        }

        if (noteExcerpts.isNotEmpty()) {
            y = ensure(36f, y)
            c.drawText("Краткие выдержки из заметок", mL, y, Paint(body).apply {
                color = ink; isFakeBoldText = true
            })
            y += 12f
            for ((date, text) in noteExcerpts.take(8)) {
                val lines = wrapText(text, body, contentW - 8f)
                y = ensure(16f + lines.size * 11f, y)
                c.drawText(date, mL, y, Paint(body).apply { isFakeBoldText = true; color = ink })
                y += 12f
                for (line in lines.take(3)) {
                    c.drawText(line, mL + 4f, y, body)
                    y += 11f
                }
                y += 6f
            }
        }

        y = ensure(70f, y)
        y = drawDisclaimerBox(c, y, disclaimerLines, small, body)
        y += 12f
        c.drawText(
            "Отчёт сформирован автоматически трекером Trace. Не замена клинической оценки врача.",
            mL, y, Paint(small).apply { textSize = 7.5f },
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        if (medsByDay.isNotEmpty()) {
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            c = page.canvas
            drawHeader(c, titlePaint, small, monthLabel, exported, n)
            y = headerBottom + 14f
            y = sectionTitle("※", "Приложение: приём по дням", y)
            for ((date, lines) in medsByDay) {
                y = ensure(24f + lines.size.coerceAtMost(6) * 11f, y)
                c.drawText(date, mL, y, Paint(body).apply { isFakeBoldText = true })
                y += 12f
                for (line in lines.take(6)) {
                    y = ensure(14f, y)
                    c.drawText("• ${ellipsize(line, body, contentW - 16f)}", mL + 6f, y, body)
                    y += 11f
                }
                y += 6f
            }
            drawFooter(c, small, pageNum)
            doc.finishPage(page)
        }

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun drawCard(c: Canvas, rect: RectF) {
        c.drawRoundRect(rect, 8f, 8f, Paint().apply { color = card })
        c.drawRoundRect(
            rect, 8f, 8f,
            Paint().apply { color = line; style = Paint.Style.STROKE; strokeWidth = 1f },
        )
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank() || maxWidth <= 8f) return listOf(text)
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(trial) <= maxWidth) {
                cur = trial
            } else {
                if (cur.isNotEmpty()) lines += cur
                if (paint.measureText(w) <= maxWidth) {
                    cur = w
                } else {
                    // hard-break long token
                    var rest = w
                    while (rest.isNotEmpty()) {
                        var cut = rest.length
                        while (cut > 1 && paint.measureText(rest.take(cut)) > maxWidth) cut--
                        lines += rest.take(cut)
                        rest = rest.drop(cut)
                    }
                    cur = ""
                }
            }
        }
        if (cur.isNotEmpty()) lines += cur
        return lines.ifEmpty { listOf(text) }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ell = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ell) > maxWidth) end--
        return text.take(end.coerceAtLeast(0)) + ell
    }


    private fun drawSleepMoodDual(
        c: Canvas,
        box: RectF,
        entries: List<MoodEntryEntity>,
        small: Paint,
    ) {
        c.drawRoundRect(box, 8f, 8f, Paint().apply { color = card })
        c.drawRoundRect(
            box,
            8f,
            8f,
            Paint().apply { color = line; style = Paint.Style.STROKE; strokeWidth = 1f },
        )
        val padL = 28f
        val padR = 28f
        val padT = 14f
        val padB = 18f
        val plot = RectF(box.left + padL, box.top + padT, box.right - padR, box.bottom - padB)
        if (entries.isEmpty()) {
            c.drawText("Нет данных", plot.centerX() - 24f, plot.centerY(), small)
            return
        }
        val sleepMax = 12f
        val barW = (plot.width() / entries.size.coerceAtLeast(1)).coerceAtMost(12f)
        val gap = (plot.width() - barW * entries.size) / (entries.size + 1).coerceAtLeast(1)
        // sleep guide band 7-9h
        val y7 = plot.bottom - (7f / sleepMax) * plot.height()
        val y9 = plot.bottom - (9f / sleepMax) * plot.height()
        c.drawRect(plot.left, y9, plot.right, y7, Paint().apply { color = Color.argb(30, 26, 158, 110) })
        entries.forEachIndexed { i, e ->
            val hours = e.sleepHours
            val x = plot.left + gap + i * (barW + gap)
            if (hours != null) {
                val h = (hours / sleepMax).coerceIn(0f, 1f) * plot.height()
                c.drawRoundRect(
                    RectF(x, plot.bottom - h, x + barW, plot.bottom),
                    2f,
                    2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 42, 120, 214) },
                )
            }
        }
        // polarity line on right scale -5..+5 mapped to plot height
        val midY = plot.centerY()
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = elev
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val path = Path()
        var started = false
        entries.forEachIndexed { i, e ->
            val pol = (e.elevated - e.depressed).toFloat().coerceIn(-5f, 5f)
            val x = plot.left + gap + i * (barW + gap) + barW / 2f
            val py = midY - (pol / 5f) * (plot.height() / 2f)
            if (!started) {
                path.moveTo(x, py)
                started = true
            } else {
                path.lineTo(x, py)
            }
            c.drawCircle(x, py, 2.2f, Paint().apply { color = elev })
        }
        if (started) c.drawPath(path, linePaint)
        c.drawLine(plot.left, midY, plot.right, midY, Paint().apply { color = line; strokeWidth = 1f })
        c.drawText("Сон", box.left + 8f, box.top + 12f, small)
        c.drawText("Настр.", box.right - 40f, box.top + 12f, small)
        val step = when {
            entries.size <= 16 -> 1
            entries.size <= 31 -> 2
            else -> 5
        }
        entries.forEachIndexed { i, e ->
            if (i % step == 0) {
                val x = plot.left + gap + i * (barW + gap)
                c.drawText(e.date.substring(8), x, box.bottom - 4f, small)
            }
        }
    }

    private fun drawPolarityCalendarHeatmap(
        c: Canvas,
        box: RectF,
        entries: List<MoodEntryEntity>,
        year: Int,
        month: Int,
        small: Paint,
    ) {
        drawCard(c, box)
        val byDate = entries.associateBy { it.date }
        val ym = java.time.YearMonth.of(year, month + 1)
        val daysInMonth = ym.lengthOfMonth()
        val cols = 7
        val gap = 4f
        val padX = 16f
        val padTop = 18f
        val padBottom = 22f
        val availW = box.width() - padX * 2
        val availH = box.height() - padTop - padBottom
        val mondayBased = (ym.atDay(1).dayOfWeek.value + 6) % 7
        val rows = ((mondayBased + daysInMonth - 1) / cols) + 1
        val cell = min((availW - gap * (cols - 1)) / cols, (availH - gap * (rows - 1)) / rows)
            .coerceIn(10f, 18f)
        val gridW = cols * cell + (cols - 1) * gap
        val x0 = box.left + (box.width() - gridW) / 2f
        val y0 = box.top + padTop
        val dow = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val dowPaint = Paint(small).apply { textSize = 7f; textAlign = Paint.Align.CENTER; color = muted }
        dow.forEachIndexed { i, d ->
            c.drawText(d, x0 + i * (cell + gap) + cell / 2f, y0 - 4f, dowPaint)
        }
        for (d in 1..daysInMonth) {
            val idx = mondayBased + (d - 1)
            val col = idx % cols
            val row = idx / cols
            val iso = "%04d-%02d-%02d".format(year, month + 1, d)
            val e = byDate[iso]
            val color = if (e == null) Color.parseColor("#E8EEF5")
            else polarityHeatColor((e.elevated - e.depressed).toFloat())
            val x = x0 + col * (cell + gap)
            val y = y0 + row * (cell + gap)
            c.drawRoundRect(RectF(x, y, x + cell, y + cell), 3f, 3f, Paint().apply { this.color = color })
        }
        c.drawText(
            "Цвет = полярность по дням",
            box.centerX(),
            box.bottom - 8f,
            Paint(small).apply { textSize = 7f; textAlign = Paint.Align.CENTER },
        )
    }

    private fun polarityHeatColor(pol: Float): Int {
        val v = pol.coerceIn(-5f, 5f)
        return when {
            v >= 4.5f -> Color.parseColor("#8B2020")
            v >= 3.5f -> Color.parseColor("#C0392B")
            v >= 2.5f -> Color.parseColor("#E55D3C")
            v >= 1.5f -> Color.parseColor("#F0944A")
            v >= 0.5f -> Color.parseColor("#F5C09A")
            v > -0.5f -> Color.parseColor("#B0B8C8")
            v > -1.5f -> Color.parseColor("#A8C8E8")
            v > -2.5f -> Color.parseColor("#6AA3D0")
            v > -3.5f -> Color.parseColor("#3A7FBA")
            v > -4.5f -> Color.parseColor("#1E5A9A")
            else -> Color.parseColor("#0F3060")
        }
    }

    private fun drawCrisisLevels(
        c: Canvas,
        yStart: Float,
        body: Paint,
        small: Paint,
        doctor: String,
        support: String,
        notes: String,
        wishes: String,
        avoid: String,
        contacts: List<Pair<String, String>>,
        ensureSpace: (need: Float, y: Float) -> Float = { _, y -> y },
    ): Float {
        var y = yStart
        data class Level(val title: String, val hint: String, val bg: Int, val accent: Int, val bullets: List<String>)
        val greenBullets = buildList {
            add("Продолжать плановый приём лекарств по назначению врача")
            add("Вести ежедневный трекер")
            add("Соблюдать режим сна/бодрствования")
            notes.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        val yellowBullets = buildList {
            add("Связаться с лечащим врачом в ближайшее время")
            support.trim().takeIf { it.isNotEmpty() }?.let { add("Опора: $it") }
            wishes.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            add("Усилить гигиену сна; попросить помощи близких")
        }
        val redBullets = buildList {
            if (doctor.isNotBlank() && doctor != "—") add("Врач: $doctor")
            contacts.take(3).forEach { (label, phone) -> add("$label: $phone") }
            add("Кризисная линия: 8-800-2000-122")
            add("Экстренно: 112")
            avoid.trim().takeIf { it.isNotEmpty() }?.let { add("Избегать: $it") }
        }
        val levels = listOf(
            Level("Зелёный уровень", "Настроение около −1…+1, сон 7–9 ч", Color.parseColor("#EDFAF4"), Color.parseColor("#1A9E6E"), greenBullets),
            Level("Жёлтый уровень", "Настроение ±2…±3 или сон <6 ч ≥2 дней", Color.parseColor("#FEF8EC"), Color.parseColor("#E8A020"), yellowBullets),
            Level("Красный уровень", "Сильный подъём/спад или мысли о самоповреждении", Color.parseColor("#FEF0EF"), Color.parseColor("#C8342A"), redBullets),
        )
        val textW = contentW - 36f
        for (lvl in levels) {
            val bulletLines = lvl.bullets.flatMap { b ->
                wrapText("• $b", body, textW).ifEmpty { listOf("• $b") }
            }
            val h = 32f + bulletLines.size * 12f + 8f
            y = ensureSpace(h + 10f, y)
            c.drawRoundRect(RectF(mL, y, mL + contentW, y + h), 8f, 8f, Paint().apply { color = lvl.bg })
            c.drawRoundRect(
                RectF(mL, y, mL + contentW, y + h), 8f, 8f,
                Paint().apply { color = Color.argb(40, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 1f },
            )
            c.drawCircle(mL + 14f, y + 14f, 4.5f, Paint().apply { color = lvl.accent })
            c.drawText(lvl.title, mL + 26f, y + 16f, Paint(body).apply { color = ink; isFakeBoldText = true })
            c.drawText(lvl.hint, mL + 26f, y + 28f, Paint(small).apply { textSize = 7.5f })
            var by = y + 42f
            for (line in bulletLines) {
                c.drawText(line, mL + 14f, by, body)
                by += 12f
            }
            y += h + 10f
        }
        return y
    }



    private fun drawDisclaimerBox(
        c: Canvas,
        yStart: Float,
        lines: List<String>,
        small: Paint,
        body: Paint,
    ): Float {
        val pad = 12f
        val lineH = 11f
        val boxH = pad + lines.size * lineH + 8f
        val rect = RectF(mL, yStart, mL + contentW, yStart + boxH)
        c.drawRoundRect(rect, 8f, 8f, Paint().apply { color = Color.parseColor("#FFF7ED") })
        c.drawRoundRect(
            rect, 8f, 8f,
            Paint().apply { color = Color.parseColor("#FED7AA"); style = Paint.Style.STROKE; strokeWidth = 1f },
        )
        c.drawRect(mL, yStart, mL + 4f, yStart + boxH, Paint().apply { color = irr })
        var y = yStart + pad + 2f
        for (line in lines) {
            c.drawText(line, mL + 12f, y, small)
            y += lineH
        }
        return yStart + boxH
    }

    private fun labResultLines(row: LabResultEntity): List<String> {
        val lines = mutableListOf<String>()
        lines += row.name
        val valueLine = buildString {
            append(row.valueText)
            row.unit?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
        }
        lines += "Значение: $valueLine"
        row.clinic?.takeIf { it.isNotBlank() }?.let { lines += "Клиника: $it" }
        return lines
    }

    private fun labDayLabel(iso: String): String =
        iso.takeLast(2).trimStart('0').ifEmpty { iso.takeLast(2) }

    private fun formatLabNumber(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString()
        else String.format(Locale("ru"), "%.2f", v)

    private fun formatDateShort(iso: String): String {
        val d = DateUtils.parseIso(iso)
        return d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru")))
    }

    private fun avgF(entries: List<MoodEntryEntity>, sel: (MoodEntryEntity) -> Int): Float =
        if (entries.isEmpty()) 0f else entries.map(sel).average().toFloat()

    private data class SparseSeries(
        val label: String,
        val color: Int,
        val points: List<Pair<String, Float>>,
    )

    /** Multi-series line chart with shared X labels (day-of-month), gaps not connected. */
    private fun drawSparseLines(
        c: Canvas,
        box: RectF,
        series: List<SparseSeries>,
        maxY: Float,
        small: Paint,
    ) {
        c.drawRoundRect(box, 8f, 8f, Paint().apply { color = card })
        val padL = 28f
        val padB = 18f
        val padT = 18f
        val padR = 10f
        val plot = RectF(box.left + padL, box.top + padT, box.right - padR, box.bottom - padB)
        val gridP = Paint().apply { color = line; strokeWidth = 1f }
        val safeMax = maxY.coerceAtLeast(0.01f)
        for (i in 0..4) {
            val gy = plot.bottom - plot.height() * i / 4f
            c.drawLine(plot.left, gy, plot.right, gy, gridP)
            c.drawText(formatLabNumber(safeMax * i / 4f), box.left + 2f, gy + 3f, small)
        }
        val nonEmpty = series.filter { it.points.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            c.drawText("Нет данных", plot.centerX() - 24f, plot.centerY(), small)
            return
        }
        val xLabels = nonEmpty
            .flatMap { s -> s.points.map { it.first } }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        val count = xLabels.size
        val stepX = if (count <= 1) 0f else plot.width() / (count - 1)
        nonEmpty.forEach { s ->
            val byX = s.points.associate { it.first to it.second }
            val indexed = xLabels.mapIndexedNotNull { i, lab ->
                val v = byX[lab] ?: return@mapIndexedNotNull null
                val x = plot.left + i * stepX
                val y = plot.bottom - (v / safeMax).coerceIn(0f, 1f) * plot.height()
                i to (x to y)
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.color
                style = Paint.Style.STROKE
                strokeWidth = 2.4f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            for (j in 1 until indexed.size) {
                val (iPrev, p0) = indexed[j - 1]
                val (iCur, p1) = indexed[j]
                if (iCur == iPrev + 1) {
                    c.drawLine(p0.first, p0.second, p1.first, p1.second, stroke)
                }
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.color }
            indexed.forEach { (_, p) -> c.drawCircle(p.first, p.second, 2.8f, dot) }
        }
        val step = when {
            count <= 16 -> 1
            count <= 31 -> 2
            else -> 5
        }
        xLabels.forEachIndexed { i, lab ->
            if (i % step == 0) {
                c.drawText(lab, plot.left + i * stepX - 4f, box.bottom - 4f, small)
            }
        }
        var lx = box.left + 8f
        nonEmpty.forEach { s ->
            c.drawCircle(lx + 4f, box.top + 8f, 4f, Paint().apply { color = s.color })
            val label = s.label.take(18)
            c.drawText(label, lx + 12f, box.top + 12f, small)
            lx += small.measureText(label) + 28f
            if (lx > box.right - 40f) return@forEach
        }
    }

    private fun drawHeader(
        c: Canvas,
        title: Paint,
        small: Paint,
        month: String,
        exported: String,
        count: Int,
    ) {
        c.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), Paint().apply { color = pageBg })
        c.drawRect(0f, 0f, pageW.toFloat(), headerBottom, Paint().apply { color = white })
        c.drawRect(0f, headerBottom, pageW.toFloat(), headerBottom + 2f, Paint().apply { color = teal })
        title.color = navy
        title.textAlign = Paint.Align.LEFT
        c.drawText("Клинический отчёт пациента", mL, 30f, title)
        small.color = muted
        small.textAlign = Paint.Align.LEFT
        c.drawText("Trace · мониторинг самоотчёта · не диагноз", mL, 48f, small)
        val right = Paint(small).apply { textAlign = Paint.Align.RIGHT; color = muted }
        c.drawText(month, pageW - mR, 30f, Paint(title).apply {
            textAlign = Paint.Align.RIGHT; textSize = 11f; isFakeBoldText = true; color = navy
        })
        c.drawText("$exported · $count записей", pageW - mR, 48f, right)
        // reset aligns for subsequent draws
        title.textAlign = Paint.Align.LEFT
        small.textAlign = Paint.Align.LEFT
    }

    private fun drawKpis(
        c: Canvas,
        y: Float,
        heading: Paint,
        small: Paint,
        valuePaint: Paint,
        items: List<Pair<String, String>>,
    ): Float {
        val gap = 8f
        val cols = items.size.coerceAtMost(6)
        val w = (contentW - gap * (cols - 1)) / cols
        val h = 46f
        items.forEachIndexed { i, (label, value) ->
            val x = mL + i * (w + gap)
            val r = RectF(x, y, x + w, y + h)
            c.drawRoundRect(r, 8f, 8f, Paint().apply { color = card })
            c.drawRoundRect(r, 8f, 8f, Paint().apply {
                color = line
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            c.drawText(label, x + 8f, y + 16f, small)
            c.drawText(value, x + 8f, y + 36f, valuePaint)
        }
        return y + h
    }

    private fun drawRadar(c: Canvas, box: RectF, axes: List<Pair<String, Float>>, small: Paint) {
        val cx = box.centerX()
        val cy = box.centerY()
        val radius = min(box.width(), box.height()) / 2f - 28f
        val n = axes.size
        if (n == 0) return
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 42, 120, 214)
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        for (ring in 1..4) {
            val path = Path()
            val rr = radius * ring / 4f
            for (i in 0 until n) {
                val a = angle(i, n)
                val x = cx + rr * cos(a)
                val y = cy + rr * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            c.drawPath(path, grid)
        }
        val data = Path()
        axes.forEachIndexed { i, (_, v) ->
            val a = angle(i, n)
            val rr = radius * v.coerceIn(0f, 1f)
            val x = cx + rr * cos(a)
            val y = cy + rr * sin(a)
            if (i == 0) data.moveTo(x, y) else data.lineTo(x, y)
        }
        data.close()
        c.drawPath(data, fill)
        c.drawPath(data, stroke)
        val labelPaint = Paint(small).apply { color = muted; textSize = 8f }
        axes.forEachIndexed { i, (label, _) ->
            val a = angle(i, n)
            val cosA = cos(a)
            val sinA = sin(a)
            val lx = cx + (radius + 16f) * cosA
            val ly = cy + (radius + 16f) * sinA
            labelPaint.textAlign = when {
                cosA < -0.35f -> Paint.Align.RIGHT
                cosA > 0.35f -> Paint.Align.LEFT
                else -> Paint.Align.CENTER
            }
            val baseline = when {
                sinA < -0.4f -> ly - 2f
                sinA > 0.4f -> ly + 10f
                else -> ly + 3f
            }
            c.drawText(label, lx, baseline, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
    }

    private fun angle(i: Int, n: Int): Float =
        (Math.PI / -2.0 + 2.0 * Math.PI * i / n).toFloat()

    private data class Series(val label: String, val color: Int, val values: List<Float>)

    private fun drawLines(
        c: Canvas,
        box: RectF,
        series: List<Series>,
        maxY: Float,
        labels: List<String>,
        small: Paint,
    ) {
        c.drawRoundRect(box, 8f, 8f, Paint().apply { color = card })
        val padL = 22f
        val padB = 18f
        val padT = 10f
        val padR = 10f
        val plot = RectF(box.left + padL, box.top + padT, box.right - padR, box.bottom - padB)
        val gridP = Paint().apply { color = line; strokeWidth = 1f }
        for (i in 0..5) {
            val gy = plot.bottom - plot.height() * i / 5f
            c.drawLine(plot.left, gy, plot.right, gy, gridP)
            c.drawText("${(maxY * i / 5f).toInt()}", box.left + 4f, gy + 3f, small)
        }
        val count = series.maxOfOrNull { it.values.size } ?: 0
        if (count == 0) {
            c.drawText("Нет данных", plot.centerX() - 24f, plot.centerY(), small)
            return
        }
        val stepX = if (count == 1) 0f else plot.width() / (count - 1)
        series.forEach { s ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.color
                style = Paint.Style.STROKE
                strokeWidth = 2.6f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            val path = Path()
            s.values.forEachIndexed { i, v ->
                val x = plot.left + i * stepX
                val y = plot.bottom - (v / maxY.coerceAtLeast(0.01f)).coerceIn(0f, 1f) * plot.height()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, p)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.color }
            s.values.forEachIndexed { i, v ->
                if (count > 40 && i % 2 != 0) return@forEachIndexed
                val x = plot.left + i * stepX
                val y = plot.bottom - (v / maxY.coerceAtLeast(0.01f)).coerceIn(0f, 1f) * plot.height()
                c.drawCircle(x, y, 2.2f, dot)
            }
        }
        val step = when {
            count <= 16 -> 1
            count <= 31 -> 2
            else -> 5
        }
        labels.forEachIndexed { i, lab ->
            if (i % step == 0) {
                c.drawText(lab, plot.left + i * stepX - 4f, box.bottom - 4f, small)
            }
        }
        var lx = box.left + 8f
        series.forEach { s ->
            c.drawCircle(lx + 4f, box.top + 8f, 4f, Paint().apply { color = s.color })
            c.drawText(s.label, lx + 12f, box.top + 12f, small)
            lx += small.measureText(s.label) + 28f
        }
    }

    private fun drawTableHeader(c: Canvas, y: Float, h: Float, small: Paint) {
        c.drawRect(mL, y, mL + contentW, y + h, Paint().apply { color = navy })
        val whiteS = Paint(small).apply { color = white; isFakeBoldText = true }
        val cols = tableCols()
        var x = mL + 4f
        cols.forEach { (label, w) ->
            c.drawText(label, x, y + 12f, whiteS)
            x += w
        }
    }

    private fun drawTableRow(
        c: Canvas,
        y: Float,
        h: Float,
        e: MoodEntryEntity,
        body: Paint,
        zebra: Boolean,
    ) {
        if (zebra) {
            c.drawRect(mL, y, mL + contentW, y + h, Paint().apply { color = card })
        }
        val cols = tableCols()
        val values = listOf(
            e.date.takeLast(5),
            "${e.depressed}",
            "${e.elevated}",
            "${e.anxious}",
            "${e.irritable}",
            e.sleepHours?.let { String.format(Locale("ru"), "%.1f", it) } ?: "—",
        )
        var x = mL + 4f
        values.forEachIndexed { i, v ->
            c.drawText(v, x, y + 11f, body)
            x += cols[i].second
        }
    }

    private fun tableCols(): List<Pair<String, Float>> {
        val w = contentW
        return listOf(
            "Дата" to w * 0.22f,
            "Д" to w * 0.13f,
            "П" to w * 0.13f,
            "Т" to w * 0.13f,
            "Р" to w * 0.13f,
            "Сон" to w * 0.26f,
        )
    }

    private fun drawPolarityBars(
        c: Canvas,
        box: RectF,
        values: List<Float>,
        maxAbs: Float,
        labels: List<String>,
        small: Paint,
    ) {
        c.drawRoundRect(box, 10f, 10f, Paint().apply { color = white })
        c.drawRoundRect(box, 10f, 10f, Paint().apply {
            color = line
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        })
        val padL = 22f
        val padB = 18f
        val padT = 22f
        val padR = 10f
        val plot = RectF(box.left + padL, box.top + padT, box.right - padR, box.bottom - padB)
        val midY = plot.centerY()
        val yPlus2 = midY - (2f / maxAbs.coerceAtLeast(0.01f)) * (plot.height() / 2f)
        val yMinus2 = midY + (2f / maxAbs.coerceAtLeast(0.01f)) * (plot.height() / 2f)
        c.drawRect(plot.left, plot.top, plot.right, yPlus2, Paint().apply { color = Color.argb(28, 229, 93, 60) })
        c.drawRect(plot.left, yMinus2, plot.right, plot.bottom, Paint().apply { color = Color.argb(28, 58, 127, 186) })
        c.drawLine(plot.left, midY, plot.right, midY, Paint().apply { color = line; strokeWidth = 1.2f })
        // Legend in top padding — doesn't collide with bars
        c.drawCircle(box.left + 14f, box.top + 11f, 3.5f, Paint().apply { color = elev })
        c.drawText("Подъём", box.left + 22f, box.top + 14f, small)
        c.drawCircle(box.left + 78f, box.top + 11f, 3.5f, Paint().apply { color = dep })
        c.drawText("Спад", box.left + 86f, box.top + 14f, small)
        if (values.isEmpty()) {
            c.drawText("Нет данных", plot.centerX() - 24f, plot.centerY(), small)
            return
        }
        val barW = (plot.width() / values.size.coerceAtLeast(1)).coerceAtMost(14f)
        val gap = (plot.width() - barW * values.size) / (values.size + 1).coerceAtLeast(1)
        values.forEachIndexed { i, v ->
            val x = plot.left + gap + i * (barW + gap)
            val h = (kotlin.math.abs(v) / maxAbs.coerceAtLeast(0.01f)).coerceIn(0f, 1f) * (plot.height() / 2f)
            val top = if (v >= 0f) midY - h else midY
            val color = if (v >= 0f) elev else dep
            c.drawRoundRect(
                RectF(x, top, x + barW, top + h.coerceAtLeast(1f)),
                2f,
                2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
            )
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = plot.left + gap + i * (barW + gap) + barW / 2f
            val py = midY - (v / maxAbs.coerceAtLeast(0.01f)).coerceIn(-1f, 1f) * (plot.height() / 2f)
            if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
        }
        c.drawPath(path, linePaint)
        val step = when {
            values.size <= 16 -> 1
            values.size <= 31 -> 2
            else -> 5
        }
        labels.forEachIndexed { i, lab ->
            if (i % step == 0) {
                val x = plot.left + gap + i * (barW + gap)
                c.drawText(lab, x, box.bottom - 4f, small)
            }
        }
    }

    private fun drawCaseHistoryTable(
        c: Canvas,
        yStart: Float,
        rows: List<CaseHistorySummary.Row>,
        body: Paint,
        small: Paint,
        heading: Paint,
    ): Float {
        var y = yStart
        val cols = floatArrayOf(0.16f, 0.16f, 0.12f, 0.22f, 0.16f, 0.18f)
        val headers = listOf("Период", "Тип (дневник)", "Тяжесть", "Лекарства", "Рутина", "События")
        fun colX(i: Int): Float {
            var x = mL
            for (k in 0 until i) x += contentW * cols[k]
            return x
        }
        val headerH = 18f
        val rowH = 28f
        // Header
        c.drawRect(mL, y, mL + contentW, y + headerH, Paint().apply { color = Color.parseColor("#E8EEF5") })
        headers.forEachIndexed { i, h ->
            c.drawText(h, colX(i) + 3f, y + 12f, Paint(small).apply {
                isFakeBoldText = true
                color = muted
            })
        }
        y += headerH
        if (rows.isEmpty()) {
            c.drawText("Недостаточно записей для сводной таблицы.", mL + 4f, y + 14f, body)
            return y + 28f
        }
        rows.forEachIndexed { idx, row ->
            if (y + rowH > pageH - footerReserve) {
                return y
            }
            val bg = if (idx % 2 == 0) white else Color.parseColor("#F7F9FC")
            c.drawRect(mL, y, mL + contentW, y + rowH, Paint().apply { color = bg })
            c.drawText(ellipsize(row.periodLabel, small, contentW * cols[0] - 6f), colX(0) + 3f, y + 12f, small)
            // Band badge
            val bandColor = CaseHistorySummary.bandColorArgb(row.band)
            val badgeLabel = CaseHistorySummary.bandLabel(row.band)
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bandColor
                alpha = 40
            }
            val bx = colX(1) + 2f
            val badgeW = (contentW * cols[1] - 6f).coerceAtMost(72f)
            c.drawRoundRect(RectF(bx, y + 6f, bx + badgeW, y + 22f), 8f, 8f, badgePaint)
            c.drawText(
                badgeLabel,
                bx + 6f,
                y + 17f,
                Paint(small).apply { color = bandColor; isFakeBoldText = true },
            )
            // Severity
            val sev = row.severity
            if (sev != CaseHistorySummary.Severity.NONE) {
                c.drawCircle(
                    colX(2) + 8f,
                    y + 14f,
                    3.5f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CaseHistorySummary.severityDotArgb(sev) },
                )
                c.drawText(CaseHistorySummary.severityLabel(sev), colX(2) + 14f, y + 17f, small)
            } else {
                c.drawText("—", colX(2) + 8f, y + 17f, small)
            }
            c.drawText(ellipsize(row.medications, small, contentW * cols[3] - 4f), colX(3) + 2f, y + 17f, small)
            c.drawText(ellipsize(row.routine, small, contentW * cols[4] - 4f), colX(4) + 2f, y + 17f, small)
            c.drawText(ellipsize(row.keyEvents, small, contentW * cols[5] - 4f), colX(5) + 2f, y + 17f, small)
            y += rowH
        }
        y += 10f
        // Legend
        val legend = listOf(
            CaseHistorySummary.Severity.SEVERE to "Тяжёлая",
            CaseHistorySummary.Severity.MODERATE to "Умеренная",
            CaseHistorySummary.Severity.MILD to "Лёгкая",
            CaseHistorySummary.Severity.NONE to "Ровные",
        )
        var lx = mL
        legend.forEach { (sev, lab) ->
            c.drawCircle(lx + 4f, y, 3.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = CaseHistorySummary.severityDotArgb(sev)
            })
            c.drawText(lab, lx + 12f, y + 3f, small)
            lx += 90f
        }
        return y + 14f
    }

    private fun drawMoodHeatmap(
        c: Canvas,
        box: RectF,
        entries: List<MoodEntryEntity>,
        small: Paint,
    ) {
        c.drawRoundRect(box, 8f, 8f, Paint().apply { color = card })
        if (entries.isEmpty()) {
            c.drawText("Нет данных", box.centerX() - 24f, box.centerY(), small)
            return
        }
        val labels = listOf("Д", "П", "Т", "Р")
        val getters: List<(MoodEntryEntity) -> Int> = listOf(
            { it.depressed }, { it.elevated }, { it.anxious }, { it.irritable },
        )
        val labelW = 18f
        val cellW = ((box.width() - labelW - 8f) / entries.size).coerceAtMost(14f)
        val cellH = ((box.height() - 16f) / labels.size).coerceAtMost(22f)
        labels.forEachIndexed { row, lab ->
            val y = box.top + 10f + row * cellH
            c.drawText(lab, box.left + 4f, y + cellH * 0.7f, small)
            entries.forEachIndexed { i, e ->
                val v = getters[row](e)
                val t = (v / 5f).coerceIn(0f, 1f)
                val color = Color.rgb(
                    (255 * t).toInt(),
                    (220 - 140 * t).toInt(),
                    (220 - 100 * t).toInt(),
                )
                val x = box.left + labelW + i * cellW
                c.drawRoundRect(
                    RectF(x, y, x + cellW - 1f, y + cellH - 2f),
                    2f,
                    2f,
                    Paint().apply { this.color = color },
                )
            }
        }
    }

    private fun drawAnxietyEnergyBars(
        c: Canvas,
        box: RectF,
        entries: List<MoodEntryEntity>,
        small: Paint,
    ) {
        c.drawRoundRect(box, 8f, 8f, Paint().apply { color = card })
        val padL = 22f
        val padB = 18f
        val padT = 14f
        val padR = 10f
        val plot = RectF(box.left + padL, box.top + padT, box.right - padR, box.bottom - padB)
        val gridP = Paint().apply { color = line; strokeWidth = 1f }
        for (i in 0..5) {
            val gy = plot.bottom - plot.height() * i / 5f
            c.drawLine(plot.left, gy, plot.right, gy, gridP)
            c.drawText("${i * 2}", box.left + 4f, gy + 3f, small)
        }
        if (entries.isEmpty()) {
            c.drawText("Нет данных", plot.centerX() - 24f, plot.centerY(), small)
            return
        }
        val slot = plot.width() / entries.size
        val barW = (slot * 0.35f).coerceAtLeast(2f)
        entries.forEachIndexed { i, e ->
            val x = plot.left + i * slot
            val hAnx = plot.height() * (e.anxious / 5f).coerceIn(0f, 1f)
            val hEn = plot.height() * (e.energy / 10f).coerceIn(0f, 1f)
            c.drawRect(
                x,
                plot.bottom - hAnx,
                x + barW,
                plot.bottom,
                Paint().apply { color = anx },
            )
            c.drawRect(
                x + barW + 1f,
                plot.bottom - hEn,
                x + barW * 2 + 1f,
                plot.bottom,
                Paint().apply { color = energyC },
            )
        }
        c.drawCircle(box.left + 12f, box.top + 10f, 4f, Paint().apply { color = anx })
        c.drawText("Тревога", box.left + 20f, box.top + 13f, small)
        c.drawCircle(box.left + 90f, box.top + 10f, 4f, Paint().apply { color = energyC })
        c.drawText("Силы", box.left + 98f, box.top + 13f, small)
    }

    private fun drawFooter(c: Canvas, small: Paint, page: Int) {
        c.drawLine(mL, pageH - 28f, pageW - mR, pageH - 28f, Paint().apply {
            color = line
            strokeWidth = 1f
        })
        val left = Paint(small).apply { color = muted; textSize = 7.5f }
        val right = Paint(small).apply {
            color = muted
            textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("Trace · самоотчёт, не диагноз", mL, pageH - 12f, left)
        c.drawText("стр. $page", pageW - mR, pageH - 12f, right)
    }
}
