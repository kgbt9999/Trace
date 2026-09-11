package com.moodlife.app.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.util.DateUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Printable monthly PDF with KPI cards, radar, line charts and daily table. */
internal class PdfReportRenderer(
    private val context: Context,
) {
    private val navy = Color.parseColor("#0B1C3D")
    private val teal = Color.parseColor("#2BBFA0")
    private val mint = Color.parseColor("#6EE7C5")
    private val ink = Color.parseColor("#1A2438")
    private val muted = Color.parseColor("#5A6A80")
    private val card = Color.parseColor("#F4F7FA")
    private val line = Color.parseColor("#D5DCE6")
    private val white = Color.WHITE
    private val dep = Color.parseColor("#3A5F9A")
    private val elev = Color.parseColor("#C9A227")
    private val anx = Color.parseColor("#7A4F9A")
    private val irr = Color.parseColor("#C45C3A")
    private val sleepC = Color.parseColor("#4A3A9A")
    private val energyC = Color.parseColor("#2BBFA0")
    private val funcC = Color.parseColor("#186898")

    private val pageW = 595
    private val pageH = 842
    private val mL = 36f
    private val mR = 36f
    private val contentW = pageW - mL - mR

    fun write(
        file: File,
        year: Int,
        month: Int,
        entries: List<MoodEntryEntity>,
        medsByDay: List<Pair<String, List<String>>> = emptyList(),
    ) {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 16f
            isFakeBoldText = true
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 10f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 8.5f }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 12f
            isFakeBoldText = true
        }
        val kpiValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 16f
            isFakeBoldText = true
        }

        val monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year"
        val exported = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())
        val n = entries.size
        fun avg(sel: (MoodEntryEntity) -> Int) =
            if (n == 0) "—" else String.format(Locale("ru"), "%.1f", entries.sumOf(sel) / n.toDouble())
        val avgSleep = entries.mapNotNull { it.sleepHours?.toDouble() }.average()
            .takeIf { !it.isNaN() }?.let { String.format(Locale("ru"), "%.1f", it) } ?: "—"
        val burden = MonthBurden.counts(entries.map { it.depressed to it.elevated })

        // Page 1 — summary + radar + mood chart
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)

        var y = 92f
        y = drawKpis(
            c, y, heading, small, kpiValue,
            listOf(
                "Подавл." to avg { it.depressed },
                "Подъём" to avg { it.elevated },
                "Тревога" to avg { it.anxious },
                "Раздр." to avg { it.irritable },
                "Силы" to avg { it.energy },
                "Сон, ч" to avgSleep,
            ),
        )
        y += 8f
        y = drawKpis(
            c, y, heading, small, kpiValue,
            listOf(
                "Дни спада" to "${burden.depressed}",
                "Дни подъёма" to "${burden.elevated}",
                "Смешанные" to "${burden.mixed}",
                "Прочие" to "${burden.other}",
            ),
        )

        y += 14f
        c.drawText("Профиль месяца", mL, y, heading)
        y += 8f
        val radarH = 200f
        drawRadar(
            c,
            RectF(mL, y, mL + contentW, y + radarH),
            listOf(
                "Д" to avgF(entries) { it.depressed } / 5f,
                "П" to avgF(entries) { it.elevated } / 5f,
                "Т" to avgF(entries) { it.anxious } / 5f,
                "Р" to avgF(entries) { it.irritable } / 5f,
                "Силы" to avgF(entries) { it.energy } / 3f,
                "Вним." to avgF(entries) { it.concentration } / 3f,
                "Общ." to avgF(entries) { it.sociability } / 3f,
            ),
            small,
        )
        y += radarH + 18f
        c.drawText("Основные оси по дням", mL, y, heading)
        y += 8f
        val moodH = 168f
        drawLines(
            c,
            RectF(mL, y, mL + contentW, y + moodH),
            listOf(
                Series("Д", dep, entries.map { it.depressed.toFloat() }),
                Series("П", elev, entries.map { it.elevated.toFloat() }),
                Series("Т", anx, entries.map { it.anxious.toFloat() }),
                Series("Р", irr, entries.map { it.irritable.toFloat() }),
            ),
            maxY = 5f,
            labels = entries.map { it.date.takeLast(2) },
            small = small,
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // Page 2 — heatmap, sleep+mood, anxiety/energy bars
        pageNum = 2
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        y = 92f
        c.drawText("Тепловая карта настроения", mL, y, heading)
        y += 8f
        val heatH = 120f
        drawMoodHeatmap(c, RectF(mL, y, mL + contentW, y + heatH), entries, small)
        y += heatH + 16f
        c.drawText("Сон и настроение", mL, y, heading)
        y += 8f
        val sleepVals = entries.map { it.sleepHours ?: 0f }
        val sleepMax = sleepVals.maxOrNull()?.coerceAtLeast(8f) ?: 8f
        // Normalize sleep to 0–5 visual scale alongside mood for combined readability
        drawLines(
            c,
            RectF(mL, y, mL + contentW, y + 150f),
            listOf(
                Series("Сон÷${String.format(Locale("ru"), "%.0f", sleepMax)}×5", sleepC, sleepVals.map { it / sleepMax * 5f }),
                Series("Д", dep, entries.map { it.depressed.toFloat() }),
                Series("П", elev, entries.map { it.elevated.toFloat() }),
            ),
            maxY = 5f,
            labels = entries.map { it.date.takeLast(2) },
            small = small,
        )
        y += 170f
        c.drawText("Тревога и силы (столбцы)", mL, y, heading)
        y += 8f
        drawAnxietyEnergyBars(
            c,
            RectF(mL, y, mL + contentW, y + 140f),
            entries,
            small,
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // Page 3 — polarity + energy/functioning
        pageNum = 3
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        y = 92f
        c.drawText("Силы и дела", mL, y, heading)
        y += 8f
        drawLines(
            c,
            RectF(mL, y, mL + contentW, y + 150f),
            listOf(
                Series("Силы", energyC, entries.map { it.energy.toFloat() }),
                Series("Дела", funcC, entries.map { it.functioning.toFloat() }),
            ),
            maxY = 10f,
            labels = entries.map { it.date.takeLast(2) },
            small = small,
        )
        y += 170f
        c.drawText("Полярность (подъём − спад)", mL, y, heading)
        y += 8f
        val polarity = entries.map { (it.elevated - it.depressed).toFloat() }
        val polMax = polarity.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(5f) ?: 5f
        drawPolarityBars(
            c,
            RectF(mL, y, mL + contentW, y + 120f),
            polarity,
            polMax,
            entries.map { it.date.takeLast(2) },
            small,
        )
        y += 140f
        c.drawText(
            "Полярность: положительная — преобладание подъёма, отрицательная — спада. Самоотчёт.",
            mL,
            y,
            small,
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // Page 4 — meds by day
        pageNum = 4
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        c = page.canvas
        drawHeader(c, titlePaint, small, monthLabel, exported, n)
        y = 92f
        c.drawText("Приём лекарств по дням", mL, y, heading)
        y += 14f
        if (medsByDay.isEmpty()) {
            c.drawText("Нет записей приёма за месяц.", mL, y, body)
        } else {
            for ((date, lines) in medsByDay) {
                if (y > pageH - 60f) {
                    drawFooter(c, small, pageNum)
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    c = page.canvas
                    drawHeader(c, titlePaint, small, monthLabel, exported, n)
                    y = 92f
                    c.drawText("Лекарства по дням (продолжение)", mL, y, heading)
                    y += 16f
                }
                c.drawText(date, mL, y, Paint(body).apply { isFakeBoldText = true })
                y += 12f
                for (line in lines) {
                    if (y > pageH - 48f) {
                        drawFooter(c, small, pageNum)
                        doc.finishPage(page)
                        pageNum++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                        c = page.canvas
                        drawHeader(c, titlePaint, small, monthLabel, exported, n)
                        y = 92f
                    }
                    c.drawText("  • $line", mL, y, body)
                    y += 11f
                }
                y += 4f
            }
        }
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        // Daily table pages
        val rowH = 16f
        val headerH = 18f
        var rowIndex = 0
        fun newTablePage(): Pair<PdfDocument.Page, Canvas> {
            pageNum++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            val canvas = p.canvas
            drawHeader(canvas, titlePaint, small, monthLabel, exported, n)
            canvas.drawText("Ежедневный журнал", mL, 92f, heading)
            drawTableHeader(canvas, 104f, headerH, small)
            return p to canvas
        }
        var pair = newTablePage()
        page = pair.first
        c = pair.second
        y = 104f + headerH
        if (entries.isEmpty()) {
            c.drawText("Нет записей за месяц.", mL, y + 14f, body)
        } else {
            entries.forEach { e ->
                if (y + rowH > pageH - 48f) {
                    drawFooter(c, small, pageNum)
                    doc.finishPage(page)
                    pair = newTablePage()
                    page = pair.first
                    c = pair.second
                    y = 104f + headerH
                }
                drawTableRow(c, y, rowH, e, body, rowIndex % 2 == 0)
                y += rowH
                rowIndex++
            }
        }
        c.drawText(
            "Д/П/Т/Р — оси настроения · самоотчёт, не диагноз",
            mL,
            pageH - 28f,
            small,
        )
        drawFooter(c, small, pageNum)
        doc.finishPage(page)

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun avgF(entries: List<MoodEntryEntity>, sel: (MoodEntryEntity) -> Int): Float =
        if (entries.isEmpty()) 0f else entries.map(sel).average().toFloat()

    private fun drawHeader(
        c: Canvas,
        title: Paint,
        small: Paint,
        month: String,
        exported: String,
        count: Int,
    ) {
        c.drawRect(0f, 0f, pageW.toFloat(), 72f, Paint().apply { color = navy })
        c.drawRect(0f, 72f, pageW.toFloat(), 76f, Paint().apply { color = teal })
        c.drawText("Trace — отчёт за $month", mL, 32f, title)
        small.color = mint
        c.drawText("Записей: $count  ·  $exported  ·  самоотчёт, не диагноз", mL, 52f, small)
        small.color = muted
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
        val radius = min(box.width(), box.height()) / 2f - 22f
        val n = axes.size
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 43, 191, 160)
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
        axes.forEachIndexed { i, (label, _) ->
            val a = angle(i, n)
            val x = cx + (radius + 14f) * cos(a) - 8f
            val y = cy + (radius + 14f) * sin(a) + 4f
            c.drawText(label, x, y, small)
        }
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
            "${e.energy}",
            e.sleepHours?.let { String.format(Locale("ru"), "%.1f", it) } ?: "—",
            "${e.elevated - e.depressed}",
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
            "Дата" to w * 0.16f,
            "Д" to w * 0.09f,
            "П" to w * 0.09f,
            "Т" to w * 0.09f,
            "Р" to w * 0.09f,
            "Силы" to w * 0.12f,
            "Сон" to w * 0.16f,
            "Пол." to w * 0.20f,
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
        val padT = 12f
        val padR = 10f
        val plot = RectF(box.left + padL, box.top + padT, box.right - padR, box.bottom - padB)
        val midY = plot.centerY()
        c.drawLine(plot.left, midY, plot.right, midY, Paint().apply { color = line; strokeWidth = 1.2f })
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
        c.drawCircle(box.left + 12f, box.top + 10f, 4f, Paint().apply { color = elev })
        c.drawText("Подъём", box.left + 20f, box.top + 13f, small)
        c.drawCircle(box.left + 80f, box.top + 10f, 4f, Paint().apply { color = dep })
        c.drawText("Спад", box.left + 88f, box.top + 13f, small)
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
        c.drawRect(0f, pageH - 18f, pageW.toFloat(), pageH.toFloat(), Paint().apply { color = navy })
        val p = Paint(small).apply { color = mint }
        c.drawText("Trace", mL, pageH - 6f, p)
        c.drawText("$page", pageW - mR - 12f, pageH - 6f, p)
    }
}
