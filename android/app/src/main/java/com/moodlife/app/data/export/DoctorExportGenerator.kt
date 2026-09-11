package com.moodlife.app.data.export

import android.content.Context
import com.moodlife.app.R
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.ExternalHealthDayDao
import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.dao.PeriodDao
import com.moodlife.app.data.local.dao.WarningSignDao
import com.moodlife.app.data.local.dao.WarningTriggerDao
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dense clinician-facing monthly HTML (CANMAT/psychosocial monitoring themes):
 * mood axes + sleep, anxiety/energy/irritability, meds by day with dose,
 * adherence, prodromes/warnings, cycle if set, physical if present.
 * Alcohol/substance self-report is intentionally omitted from clinician export.
 * One short non-diagnostic note only — observational self-report summary.
 */
@Singleton
class DoctorExportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moodEntryDao: MoodEntryDao,
    private val medicationLogDao: MedicationLogDao,
    private val medicationDao: MedicationDao,
    private val medicationRepository: MedicationRepository,
    private val dayNoteDao: DayNoteDao,
    private val externalHealthDayDao: ExternalHealthDayDao,
    private val warningTriggerDao: WarningTriggerDao,
    private val warningSignDao: WarningSignDao,
    private val periodDao: PeriodDao,
) {
    suspend fun generateHtml(year: Int, month: Int): String {
        val entries = loadMonthEntries(year, month)
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val dayNotes = dayNoteDao.listRange(from, to)
        val allMeds = medicationDao.observeVisibleInRange(from, to).first()
        val medById = allMeds.associateBy { it.id }
        val health = externalHealthDayDao.observeRange(from, to).first()
        val signs = warningSignDao.observeAll().first().associateBy { it.id }
        val triggers = if (entries.isEmpty()) {
            emptyList()
        } else {
            warningTriggerDao.listForEntries(entries.map { it.id })
        }
        val period = periodDao.get()

        val totalDays = entries.size
        fun avg(key: (MoodEntryEntity) -> Int): String =
            if (totalDays == 0) "—"
            else String.format(Locale("ru"), "%.1f", entries.sumOf(key) / totalDays.toDouble())

        val avgSleep = if (totalDays == 0) "—"
        else String.format(
            Locale("ru"),
            "%.1f",
            entries.mapNotNull { it.sleepHours?.toDouble() }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
        )

        val notesByDate = dayNotes.groupBy { it.date }.mapValues { (_, notes) ->
            notes.map { n -> "${formatTime(n.createdAt)} — ${esc(n.content)}" }
        }

        val warningsByEntry = triggers.groupBy { it.moodEntryId }
        val warningBlock = buildProdromesByDayHtml(entries, warningsByEntry, signs)

        val medAdherence = linkedMapOf<String, Pair<Int, Int>>()
        for (log in medLogs) {
            val med = medById[log.medicationId] ?: continue
            val name = medicationRepository.effectiveName(med, log)
            val cur = medAdherence.getOrPut(name) { 0 to 0 }
            val raw = medicationRepository.effectiveIntakeTimes(med, log)
            val slots = MedsUtils.parseIntakeTimes(raw).filter { it != "by-scheme" }
            val scheduled = if (slots.isEmpty()) 1 else slots.size
            val taken = if (slots.isEmpty()) {
                if (log.taken) 1 else 0
            } else {
                slots.count { MedsUtils.isSlotTaken(log.taken, log.slotsTaken, it, slots) }
            }
            medAdherence[name] = cur.first + scheduled to cur.second + taken
        }

        val burden = MonthBurden.counts(entries.map { it.depressed to it.elevated })
        val bedtimeSpread = MonthBurden.bedtimeSpreadMinutes(entries.mapNotNull { it.sleepTime })
        val slotAdherence = MonthBurden.adherence(medLogs, allMeds)

        val monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year"
        val exportedAt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())

        val rows = entries.joinToString("") { e ->
            val phase = e.episodePhase?.let { phaseLabel(it) } ?: "—"
            val notes = (notesByDate[e.date] ?: listOfNotNull(e.note?.let { esc(it) }))
                .joinToString(" · ").ifBlank { "—" }
            val warn = warningsByEntry[e.id].orEmpty().joinToString("; ") { t ->
                signs[t.warningSignId]?.name ?: "?"
            }.ifBlank { "—" }
            """
            <tr>
              <td>${esc(formatDateRu(e.date))}</td>
              <td>${e.depressed}</td><td>${e.elevated}</td><td>${e.anxious}</td><td>${e.irritable}</td>
              <td>${e.energy}</td>
              <td>${e.sleepHours ?: "—"}</td>
              <td>${esc(phase)}</td>
              <td>${esc(warn)}</td>
              <td>$notes</td>
            </tr>
            """.trimIndent()
        }

        val medRows = medAdherence.entries.joinToString("") { (name, pair) ->
            val (scheduled, taken) = pair
            val pct = if (scheduled > 0) (taken * 100 / scheduled) else 0
            "<tr><td>${esc(name)}</td><td>$taken/$scheduled</td><td>$pct%</td></tr>"
        }

        val medsByDay = buildMedsByDayHtml(from, to, medLogs, allMeds, includeUntaken = true)
        val chartSvg = buildMoodSvg(entries)
        val sleepSvg = buildSleepSvg(entries)
        val energySvg = buildEnergySvg(entries)
        val heatmapSvg = buildMoodHeatmapSvg(entries)
        val profileSvg = buildStateProfileSvg(entries)
        val anxietyEnergyBars = buildAnxietyEnergyBarsSvg(entries)
        val physicalBlock = buildPhysicalHtml(health)
        val cycleBlock = buildCycleHtml(period)

        return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="utf-8"/>
          <title>${esc(context.getString(R.string.export_html_title, monthLabel))}</title>
          <style>
            body { font-family: "Segoe UI", system-ui, sans-serif; max-width: 1100px; margin: 1.25rem auto; color: #1A2438; line-height: 1.35; background: #F3F6FA; }
            h1 { font-size: 1.25rem; margin: 0 0 0.2rem; color: #0B1C3D; }
            h2 { font-size: 1.05rem; margin: 1.25rem 0 0.4rem; border-bottom: 2px solid #2BBFA0; padding-bottom: 0.15rem; color: #0B1C3D; }
            .meta { color: #5A6A80; font-size: 0.8rem; margin: 0.25rem 0 0.75rem; }
            .note { border-left: 3px solid #2BBFA0; background: #fff; padding: 0.4rem 0.75rem; margin: 0.5rem 0 1rem; font-size: 0.8rem; color: #333; }
            table { width: 100%; border-collapse: collapse; font-size: 0.75rem; margin: 0.4rem 0 0.8rem; background: #fff; }
            th, td { border: 1px solid #D5DCE6; padding: 0.3rem 0.45rem; text-align: left; vertical-align: top; }
            th { background: #0B1C3D; color: #fff; }
            tr:nth-child(even) td { background: #F4F7FA; }
            .summary { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 0.5rem; margin: 0.5rem 0; }
            .summary div { border: 1px solid #D5DCE6; background: #fff; border-radius: 8px; padding: 0.45rem 0.55rem; }
            .summary strong { display: block; font-size: 1.05rem; color: #0B1C3D; }
            .chart { margin: 0.5rem 0; overflow-x: auto; background: #fff; border: 1px solid #D5DCE6; border-radius: 8px; padding: 0.5rem; }
            .prodrome-day { font-family: ui-monospace, Consolas, monospace; font-size: 0.85rem; margin: 0.25rem 0; white-space: pre-wrap; }
            @media print { body { margin: 0.4in; background: #fff; } }
          </style>
        </head>
        <body>
          <h1>${esc(context.getString(R.string.export_html_heading))}</h1>
          <p class="meta">${esc(monthLabel)} · ${esc(exportedAt)} · дней с записью: $totalDays · самоотчёт пользователя</p>
          <div class="note">
            Наблюдательная сводка дневника для клинициста. Не диагноз и не рекомендация менять терапию.
            Алкоголь и ПАВ в этот отчёт не включаются.
          </div>
          <h2>Сводка (расчёт по дневнику)</h2>
          <div class="summary">
            <div><span>Подавленность</span><strong>${avg { it.depressed }}</strong></div>
            <div><span>Подъём</span><strong>${avg { it.elevated }}</strong></div>
            <div><span>Тревога</span><strong>${avg { it.anxious }}</strong></div>
            <div><span>Раздражение</span><strong>${avg { it.irritable }}</strong></div>
            <div><span>Силы</span><strong>${avg { it.energy }}</strong></div>
            <div><span>Сон, ч</span><strong>$avgSleep</strong></div>
            <div><span>Дела</span><strong>${avg { it.functioning }}</strong></div>
            <div><span>${esc(context.getString(R.string.export_html_days_depressed))}</span><strong>${burden.depressed}</strong></div>
            <div><span>${esc(context.getString(R.string.export_html_days_elevated))}</span><strong>${burden.elevated}</strong></div>
            <div><span>${esc(context.getString(R.string.export_html_days_mixed))}</span><strong>${burden.mixed}</strong></div>
            ${slotAdherence.percent?.let { "<div><span>${esc(context.getString(R.string.export_html_adherence))}</span><strong>$it%</strong></div>" } ?: ""}
            ${bedtimeSpread?.let { "<div><span>${esc(context.getString(R.string.export_html_bedtime))}</span><strong>~${it} мин</strong></div>" } ?: ""}
          </div>
          $cycleBlock
          <h2>Профиль состояния (средние)</h2>
          <div class="chart">$profileSvg</div>
          <h2>Тепловая карта настроения</h2>
          <div class="chart">$heatmapSvg</div>
          <h2>Траектории</h2>
          <div class="chart">$chartSvg</div>
          <div class="chart">$anxietyEnergyBars</div>
          <div class="chart">$energySvg</div>
          <div class="chart">$sleepSvg</div>
          $physicalBlock
          $warningBlock
          ${if (medRows.isNotBlank()) """
          <h2>Приём препаратов — сводка</h2>
          <table><thead><tr><th>Препарат</th><th>Принято/запланировано</th><th>%</th></tr></thead><tbody>$medRows</tbody></table>
          """ else ""}
          <h2>Лекарства по дням (имя · доза · слоты утро/вечер)</h2>
          $medsByDay
          <h2>Ежедневный журнал</h2>
          <table>
            <thead><tr>
              <th>Дата</th><th>Д</th><th>П</th><th>Т</th><th>Р</th><th>Силы</th><th>Сон</th>
              <th>Фаза</th><th>Продромы</th><th>Заметки</th>
            </tr></thead>
            <tbody>${rows.ifBlank { "<tr><td colspan=\"10\">Нет записей</td></tr>" }}</tbody>
          </table>
          <p class="meta">Д — подавленность · П — подъём · Т — тревога · Р — раздражение · пользовательский ввод, кроме строк «Сводка (расчёт)»</p>
        </body>
        </html>
        """.trimIndent()
    }

    suspend fun loadMonthEntries(year: Int, month: Int): List<MoodEntryEntity> {
        val (from, to) = DateUtils.monthRange(year, month)
        return moodEntryDao.observeRange(from, to).first()
    }

    /** Day-by-day med listing for PDF renderer (taken + missed with dose). */
    suspend fun loadMedsByDay(year: Int, month: Int): List<Pair<String, List<String>>> {
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val allMeds = medicationDao.observeVisibleInRange(from, to).first()
        return buildMedsByDayLines(from, to, medLogs, allMeds, includeUntaken = true)
    }

    private fun buildMedsByDayLines(
        from: String,
        to: String,
        medLogs: List<MedicationLogEntity>,
        allMeds: List<MedicationEntity>,
        includeUntaken: Boolean,
    ): List<Pair<String, List<String>>> {
        if (allMeds.isEmpty()) return emptyList()
        val byDate = medLogs.groupBy { it.date }
        val start = java.time.LocalDate.parse(from)
        val end = java.time.LocalDate.parse(to)
        val result = mutableListOf<Pair<String, List<String>>>()
        var d = start
        while (!d.isAfter(end)) {
            val iso = d.toString()
            val dayLogs = byDate[iso].orEmpty()
            val lines = allMeds.mapNotNull { med ->
                val log = dayLogs.find { it.medicationId == med.id }
                if (log == null && !med.isActive) return@mapNotNull null
                val raw = medicationRepository.effectiveIntakeTimes(med, log)
                val slots = MedsUtils.parseIntakeTimes(raw)
                val timed = slots.filter { it != "by-scheme" }
                val scheduled = if (timed.isEmpty()) 1 else timed.size
                val taken = if (timed.isEmpty()) {
                    if (log?.taken == true) 1 else 0
                } else {
                    timed.count { slot ->
                        MedsUtils.isSlotTaken(log?.taken == true, log?.slotsTaken, slot, timed)
                    }
                }
                if (!includeUntaken && taken <= 0) return@mapNotNull null
                if (log == null && taken <= 0 && !includeUntaken) return@mapNotNull null
                // Show day only if there is a log or med is active regular (planned)
                if (log == null && !includeUntaken) return@mapNotNull null
                if (log == null && includeUntaken && !med.isRegular) return@mapNotNull null
                val dose = medicationRepository.effectiveDosage(med, log)?.takeIf { it.isNotBlank() }
                val name = medicationRepository.effectiveName(med, log)
                val slotChecks = if (timed.isEmpty()) {
                    val ok = log?.taken == true
                    listOf("день: ${if (ok) "✓" else "○"}")
                } else {
                    timed.map { slot ->
                        val ok = MedsUtils.isSlotTaken(log?.taken == true, log?.slotsTaken, slot, timed)
                        "${MedsUtils.slotLabel(slot)}: ${if (ok) "✓" else "○"}"
                    }
                }
                buildString {
                    append(name)
                    if (dose != null) append(" · ").append(dose)
                    append(" · ").append(slotChecks.joinToString("  "))
                    append(" ($taken/$scheduled)")
                }
            }
            if (lines.isNotEmpty()) result += iso to lines
            d = d.plusDays(1)
        }
        return result
    }

    private fun buildMedsByDayHtml(
        from: String,
        to: String,
        medLogs: List<MedicationLogEntity>,
        allMeds: List<MedicationEntity>,
        includeUntaken: Boolean,
    ): String {
        val days = buildMedsByDayLines(from, to, medLogs, allMeds, includeUntaken)
        if (days.isEmpty()) return "<p>Нет записей приёма за месяц.</p>"
        val rows = days.joinToString("") { (date, lines) ->
            "<tr><td>${esc(formatDateRu(date))}</td><td>${lines.joinToString("<br/>") { esc(it) }}</td></tr>"
        }
        return "<table><thead><tr><th>Дата</th><th>Препараты</th></tr></thead><tbody>$rows</tbody></table>"
    }

    private fun buildCycleHtml(period: com.moodlife.app.data.local.entity.PeriodSettingEntity?): String {
        if (period == null) return ""
        val start = period.lastPeriodStart?.takeIf { it.isNotBlank() } ?: return ""
        return """
          <h2>Цикл (пользовательские настройки)</h2>
          <p class="meta">Последнее начало: ${esc(formatDateRu(start))} · длина цикла: ${period.cycleLength} дн. · менструация: ${period.periodLength} дн.${if (period.irregular) " · нерегулярный" else ""}</p>
        """.trimIndent()
    }

    private fun buildPhysicalHtml(health: List<com.moodlife.app.data.local.entity.ExternalHealthDayEntity>): String {
        if (health.isEmpty()) return ""
        val steps = health.filter { it.kind == "activity" }.mapNotNull { it.steps }
        val sleep = health.filter { it.kind == "sleep" }.mapNotNull { it.sleepHours }
        val nutrition = health.filter { it.kind == "nutrition" }
        val weight = health.filter { it.kind == "weight" }.mapNotNull { it.weightKg }.lastOrNull()
        val avgSteps = steps.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgSleep = sleep.takeIf { it.isNotEmpty() }?.average()?.let {
            String.format(Locale("ru"), "%.1f", it)
        }
        val avgKcal = nutrition.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        if (avgSteps == null && avgSleep == null && avgKcal == null && weight == null) return ""
        return """
          <h2>Физическое (внешние данные)</h2>
          <div class="summary">
            ${avgSteps?.let { "<div><span>Шаги (ср.)</span><strong>$it</strong></div>" } ?: ""}
            ${avgSleep?.let { "<div><span>Сон HC, ч</span><strong>$it</strong></div>" } ?: ""}
            ${avgKcal?.let { "<div><span>Ккал (ср.)</span><strong>$it</strong></div>" } ?: ""}
            ${weight?.let { "<div><span>Вес, кг</span><strong>${String.format(Locale("ru"), "%.1f", it)}</strong></div>" } ?: ""}
          </div>
        """.trimIndent()
    }

    private fun buildMoodSvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return "<p>Нет данных.</p>"
        val w = 720
        val h = 220
        val padL = 36
        val padR = 16
        val padT = 28
        val padB = 28
        fun x(i: Int) = padL + (w - padL - padR) * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Int) = (h - padB) - (h - padT - padB) * (v / 5f)
        fun path(sel: (MoodEntryEntity) -> Int, color: String): String {
            val pts = entries.mapIndexed { i, e -> "${x(i)},${y(sel(e))}" }.joinToString(" ")
            return """<polyline fill="none" stroke="$color" stroke-width="2" points="$pts"/>"""
        }
        val grid = (0..5).joinToString("") { v ->
            val gy = y(v)
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#D5DCE6" stroke-width="1"/>
               <text x="4" y="${gy + 3}" font-size="10" fill="#5A6A80">$v</text>"""
        }
        val xLabels = entries.mapIndexed { i, e ->
            if (i % ((entries.size / 8).coerceAtLeast(1)) == 0 || i == entries.lastIndex) {
                """<text x="${x(i)}" y="${h - 6}" font-size="9" fill="#5A6A80" text-anchor="middle">${e.date.takeLast(2)}</text>"""
            } else ""
        }.joinToString("")
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Оси настроения">
          <text x="$padL" y="14" font-size="12" fill="#0B1C3D">Д · П · Т · Р (шкала 0–5)</text>
          $grid
          ${path({ it.depressed }, "#3A5F9A")}
          ${path({ it.elevated }, "#C9A227")}
          ${path({ it.anxious }, "#7A4F9A")}
          ${path({ it.irritable }, "#C45C3A")}
          $xLabels
          <text x="${w - 200}" y="14" font-size="10" fill="#3A5F9A">Д</text>
          <text x="${w - 160}" y="14" font-size="10" fill="#C9A227">П</text>
          <text x="${w - 120}" y="14" font-size="10" fill="#7A4F9A">Т</text>
          <text x="${w - 80}" y="14" font-size="10" fill="#C45C3A">Р</text>
        </svg>
        """.trimIndent()
    }

    private fun buildEnergySvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return ""
        val w = 720
        val h = 180
        val padL = 36
        val padR = 16
        val padT = 28
        val padB = 28
        fun x(i: Int) = padL + (w - padL - padR) * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Int) = (h - padB) - (h - padT - padB) * (v / 10f)
        val energy = entries.mapIndexed { i, e -> "${x(i)},${y(e.energy)}" }.joinToString(" ")
        val func = entries.mapIndexed { i, e -> "${x(i)},${y(e.functioning)}" }.joinToString(" ")
        val grid = listOf(0, 2, 4, 6, 8, 10).joinToString("") { v ->
            val gy = y(v)
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#D5DCE6"/>
               <text x="4" y="${gy + 3}" font-size="10" fill="#5A6A80">$v</text>"""
        }
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Силы и дела">
          <text x="$padL" y="14" font-size="12" fill="#0B1C3D">Силы · Дела (0–10)</text>
          $grid
          <polyline fill="none" stroke="#2BBFA0" stroke-width="2" points="$energy"/>
          <polyline fill="none" stroke="#186898" stroke-width="2" points="$func"/>
        </svg>
        """.trimIndent()
    }

    private fun buildSleepSvg(entries: List<MoodEntryEntity>): String {
        val vals = entries.map { it.sleepHours ?: 0f }
        if (vals.all { it == 0f }) return ""
        val w = 720
        val h = 180
        val padL = 36
        val padR = 16
        val padT = 28
        val padB = 28
        val maxY = vals.maxOrNull()?.coerceAtLeast(8f) ?: 8f
        fun x(i: Int) = padL + (w - padL - padR) * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Float) = (h - padB) - (h - padT - padB) * (v / maxY)
        val pts = vals.mapIndexed { i, v -> "${x(i)},${y(v)}" }.joinToString(" ")
        val ticks = (0..4).map { maxY * it / 4f }
        val grid = ticks.joinToString("") { v ->
            val gy = y(v)
            val lab = String.format(Locale("ru"), "%.0f", v)
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#D5DCE6"/>
               <text x="4" y="${gy + 3}" font-size="10" fill="#5A6A80">$lab</text>"""
        }
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Сон">
          <text x="$padL" y="14" font-size="12" fill="#0B1C3D">Сон, часы</text>
          $grid
          <polyline fill="none" stroke="#4A3A9A" stroke-width="2" points="$pts"/>
        </svg>
        """.trimIndent()
    }

    private fun buildMoodHeatmapSvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return "<p>Нет данных.</p>"
        val cell = 18
        val labels = listOf("Д", "П", "Т", "Р")
        val keys: List<(MoodEntryEntity) -> Int> = listOf(
            { it.depressed }, { it.elevated }, { it.anxious }, { it.irritable },
        )
        val w = 80 + entries.size * cell
        val h = 40 + labels.size * cell
        fun color(v: Int): String {
            val t = (v / 5f).coerceIn(0f, 1f)
            val r = (255 * t).toInt()
            val g = (220 - 140 * t).toInt()
            val b = (220 - 100 * t).toInt()
            return String.format("#%02X%02X%02X", r, g, b)
        }
        val cells = labels.indices.joinToString("") { row ->
            val y = 28 + row * cell
            val label = """<text x="8" y="${y + 13}" font-size="11" fill="#0B1C3D">${labels[row]}</text>"""
            val rowCells = entries.mapIndexed { i, e ->
                val v = keys[row](e)
                val x = 40 + i * cell
                """<rect x="$x" y="$y" width="${cell - 1}" height="${cell - 1}" fill="${color(v)}" rx="2"/>"""
            }.joinToString("")
            label + rowCells
        }
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Тепловая карта">
          <text x="40" y="14" font-size="12" fill="#0B1C3D">Интенсивность осей по дням (0–5)</text>
          $cells
        </svg>
        """.trimIndent()
    }

    private fun buildStateProfileSvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return "<p>Нет данных.</p>"
        fun avg(sel: (MoodEntryEntity) -> Int) = entries.map(sel).average().toFloat()
        val axes = listOf(
            "Д" to avg { it.depressed } / 5f,
            "П" to avg { it.elevated } / 5f,
            "Т" to avg { it.anxious } / 5f,
            "Р" to avg { it.irritable } / 5f,
            "Силы" to avg { it.energy } / 10f,
            "Сон" to (
                entries.mapNotNull { it.sleepHours }.average().takeIf { !it.isNaN() }?.toFloat()?.div(10f) ?: 0f
                ),
        )
        val cx = 160f
        val cy = 140f
        val r = 90f
        val n = axes.size
        fun pt(i: Int, rr: Float): Pair<Float, Float> {
            val a = -Math.PI / 2 + 2 * Math.PI * i / n
            return (cx + rr * Math.cos(a).toFloat()) to (cy + rr * Math.sin(a).toFloat())
        }
        val rings = (1..4).joinToString("") { ring ->
            val pts = (0 until n).map { i -> pt(i, r * ring / 4f) }
            val d = pts.mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${p.first},${p.second}" }.joinToString(" ") + " Z"
            """<path d="$d" fill="none" stroke="#D5DCE6"/>"""
        }
        val dataPts = axes.mapIndexed { i, (_, v) -> pt(i, r * v.coerceIn(0f, 1f)) }
        val dataD = dataPts.mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${p.first},${p.second}" }.joinToString(" ") + " Z"
        val labels = axes.mapIndexed { i, (lab, _) ->
            val p = pt(i, r + 16f)
            """<text x="${p.first}" y="${p.second}" font-size="11" fill="#0B1C3D" text-anchor="middle">$lab</text>"""
        }.joinToString("")
        return """
        <svg viewBox="0 0 320 280" width="320" role="img" aria-label="Профиль состояния">
          $rings
          <path d="$dataD" fill="rgba(43,191,160,0.35)" stroke="#2BBFA0" stroke-width="2"/>
          $labels
        </svg>
        """.trimIndent()
    }

    private fun buildAnxietyEnergyBarsSvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return ""
        val w = 720
        val h = 180
        val padL = 36
        val padR = 16
        val padT = 28
        val padB = 28
        val maxY = 10f
        val slot = (w - padL - padR) / entries.size.toFloat()
        val bars = entries.mapIndexed { i, e ->
            val x = padL + i * slot
            val bw = (slot * 0.35f).coerceAtLeast(2f)
            val hAnx = (h - padT - padB) * (e.anxious / 5f).coerceIn(0f, 1f)
            val hEn = (h - padT - padB) * (e.energy / maxY).coerceIn(0f, 1f)
            """
            <rect x="$x" y="${h - padB - hAnx}" width="$bw" height="$hAnx" fill="#7A4F9A"/>
            <rect x="${x + bw + 1}" y="${h - padB - hEn}" width="$bw" height="$hEn" fill="#2BBFA0"/>
            """.trimIndent()
        }.joinToString("")
        val grid = listOf(0, 2, 4, 6, 8, 10).joinToString("") { v ->
            val gy = (h - padB) - (h - padT - padB) * (v / maxY)
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#D5DCE6"/>
               <text x="4" y="${gy + 3}" font-size="10" fill="#5A6A80">$v</text>"""
        }
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Тревога и силы">
          <text x="$padL" y="14" font-size="12" fill="#0B1C3D">Столбцы: тревога (0–5) · силы (0–10)</text>
          $grid
          $bars
        </svg>
        """.trimIndent()
    }

    private fun buildProdromesByDayHtml(
        entries: List<MoodEntryEntity>,
        warningsByEntry: Map<String, List<com.moodlife.app.data.local.entity.WarningTriggerEntity>>,
        signs: Map<String, com.moodlife.app.data.local.entity.EarlyWarningSignEntity>,
    ): String {
        val lines = entries.mapNotNull { e ->
            val triggers = warningsByEntry[e.id].orEmpty()
            if (triggers.isEmpty()) return@mapNotNull null
            val byDir = linkedMapOf("depression" to mutableListOf<String>(), "mania" to mutableListOf<String>(), "mixed" to mutableListOf<String>())
            for (t in triggers) {
                val sign = signs[t.warningSignId]
                val dir = sign?.direction ?: "depression"
                val name = sign?.name ?: t.warningSignId
                byDir.getOrPut(dir) { mutableListOf() }.add(name)
            }
            fun join(key: String) = byDir[key].orEmpty().joinToString(", ").ifBlank { "—" }
            val dateLabel = formatDateLongRu(e.date)
            """<div class="prodrome-day">${esc(dateLabel)}   Д: ${esc(join("depression"))}   М: ${esc(join("mania"))}   С: ${esc(join("mixed"))}</div>"""
        }
        if (lines.isEmpty()) return ""
        return """
          <h2>Продромы / ранние признаки по дням (самоотчёт)</h2>
          <p class="meta">Д — депрессивное направление · М — маниакальное · С — смешанное. Не диагноз.</p>
          ${lines.joinToString("\n")}
        """.trimIndent()
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun formatTime(ts: Long) =
        SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))

    private fun formatDateRu(iso: String): String {
        val d = DateUtils.parseIso(iso)
        return d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru")))
    }

    private fun formatDateLongRu(iso: String): String {
        val d = DateUtils.parseIso(iso)
        return d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
    }

    private fun phaseLabel(phase: String): String = when (phase) {
        "euthymic" -> "Эйтимия"
        "prodromal_depression" -> "Продром депрессии"
        "prodromal_mania" -> "Продром мании"
        "acute_depression" -> "Острая депрессия"
        "acute_mania" -> "Острый подъём"
        "mixed" -> "Смешанный эпизод"
        "recovery" -> "Восстановление"
        else -> phase
    }
}
