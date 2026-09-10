package com.moodlife.app.data.export

import android.content.Context
import com.moodlife.app.R
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.ExternalHealthDayDao
import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.domain.MoodScales
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorExportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moodEntryDao: MoodEntryDao,
    private val medicationLogDao: MedicationLogDao,
    private val medicationDao: MedicationDao,
    private val medicationRepository: MedicationRepository,
    private val dayNoteDao: DayNoteDao,
    private val externalHealthDayDao: ExternalHealthDayDao,
) {
    suspend fun generateHtml(year: Int, month: Int): String {
        val entries = loadMonthEntries(year, month)
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val dayNotes = dayNoteDao.listRange(from, to)
        val allMeds = medicationDao.observeAll().first()
        val medNames = allMeds.associateBy { it.id }
        val health = externalHealthDayDao.observeRange(from, to).first()

        val totalDays = entries.size
        fun avg(key: (MoodEntryEntity) -> Int): String =
            if (totalDays == 0) "—"
            else String.format(Locale("ru"), "%.1f", entries.sumOf(key) / totalDays.toDouble())

        val avgSleep = if (totalDays == 0) "—"
        else String.format(Locale("ru"), "%.1f", entries.sumOf { (it.sleepHours ?: 0f).toDouble() } / totalDays)

        val notesByDate = dayNotes.groupBy { it.date }.mapValues { (_, notes) ->
            notes.map { n -> "${formatTime(n.createdAt)} — ${esc(n.content)}" }
        }

        val medAdherence = linkedMapOf<String, Pair<Int, Int>>()
        for (log in medLogs) {
            val med = medNames[log.medicationId]?.name ?: continue
            val cur = medAdherence.getOrPut(med) { 0 to 0 }
            medAdherence[med] = cur.first + 1 to cur.second + if (log.taken) 1 else 0
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
            """
            <tr>
              <td>${esc(formatDateRu(e.date))}</td>
              <td>${e.depressed}</td><td>${e.elevated}</td><td>${e.anxious}</td><td>${e.irritable}</td>
              <td>${e.sleepHours ?: "—"}</td>
              <td>${if (e.alcoholUse > 0) esc(alcoholLabel(e.alcoholUse)) else "—"}</td>
              <td>${if (e.substanceUse > 0) esc(substanceLabel(e.substanceUse)) else "—"}</td>
              <td>${esc(phase)}</td>
              <td>—</td>
              <td>$notes</td>
            </tr>
            """.trimIndent()
        }

        val medRows = medAdherence.entries.joinToString("") { (name, pair) ->
            val (scheduled, taken) = pair
            val pct = if (scheduled > 0) (taken * 100 / scheduled) else 0
            "<tr><td>${esc(name)}</td><td>$taken/$scheduled</td><td>$pct%</td></tr>"
        }

        val medsByDay = buildMedsByDayHtml(from, to, medLogs, allMeds)
        val chartSvg = buildMoodSvg(entries)
        val sleepSvg = buildSleepSvg(entries)
        val physicalBlock = buildPhysicalHtml(health)

        return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="utf-8"/>
          <title>${esc(context.getString(R.string.export_html_title, monthLabel))}</title>
          <style>
            body { font-family: system-ui, sans-serif; max-width: 960px; margin: 2rem auto; color: #111; line-height: 1.45; }
            h1 { font-size: 1.35rem; margin-bottom: 0.25rem; }
            h2 { font-size: 1.1rem; margin-top: 1.6rem; }
            .meta { color: #555; font-size: 0.85rem; margin-bottom: 1.5rem; }
            .disclaimer { background: #f5f5f5; border-left: 4px solid #888; padding: 0.75rem 1rem; margin: 1rem 0; font-size: 0.85rem; }
            .help { background: #eef6ff; border-left: 4px solid #3A5F9A; padding: 0.75rem 1rem; margin: 1rem 0; font-size: 0.85rem; }
            table { width: 100%; border-collapse: collapse; font-size: 0.8rem; margin: 1rem 0; }
            th, td { border: 1px solid #ccc; padding: 0.35rem 0.5rem; text-align: left; vertical-align: top; }
            th { background: #eee; }
            .summary { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 0.75rem; margin: 1rem 0; }
            .summary div { border: 1px solid #ddd; border-radius: 8px; padding: 0.5rem 0.75rem; }
            .summary strong { display: block; font-size: 1.1rem; }
            .chart { margin: 1rem 0; overflow-x: auto; }
            @media print { body { margin: 0.5in; } }
          </style>
        </head>
        <body>
          <h1>${esc(context.getString(R.string.export_html_heading))}</h1>
          <p class="meta">${esc(monthLabel)} · экспорт ${esc(exportedAt)} · записей: $totalDays</p>
          <div class="disclaimer">
            Self-tracker, не медицинский диагноз. Подъём настроения ≠ «хорошо». Данные введены пользователем.
            Наблюдательные сводки для обсуждения с лечащим врачом — не рекомендация менять терапию.
          </div>
          <div class="help">
            <strong>ПАВ</strong> — психоактивные вещества (пользовательская шкала самоотчёта: алкоголь и другие вещества).
            Подписи ступеней (например «заметно») — якоря шкалы по умолчанию или настроенные пользователем;
            это не клиническая оценка и не обвинение.
          </div>
          <h2>Средние за месяц</h2>
          <div class="summary">
            <div><span>Подавленность</span><strong>${avg { it.depressed }}</strong></div>
            <div><span>Подъём</span><strong>${avg { it.elevated }}</strong></div>
            <div><span>Тревога</span><strong>${avg { it.anxious }}</strong></div>
            <div><span>Раздражение</span><strong>${avg { it.irritable }}</strong></div>
            <div><span>Сон, ч</span><strong>$avgSleep</strong></div>
            <div><span>Силы</span><strong>${avg { it.energy }}</strong></div>
            <div><span>Дела</span><strong>${avg { it.functioning }}</strong></div>
          </div>
          <h2>${esc(context.getString(R.string.export_html_burden))}</h2>
          <div class="summary">
            <div><span>${esc(context.getString(R.string.export_html_days_depressed))}</span><strong>${burden.depressed}</strong></div>
            <div><span>${esc(context.getString(R.string.export_html_days_elevated))}</span><strong>${burden.elevated}</strong></div>
            <div><span>${esc(context.getString(R.string.export_html_days_mixed))}</span><strong>${burden.mixed}</strong></div>
            <div><span>${esc(context.getString(R.string.export_html_days_other))}</span><strong>${burden.other}</strong></div>
            ${slotAdherence.percent?.let { "<div><span>${esc(context.getString(R.string.export_html_adherence))}</span><strong>$it%</strong></div>" } ?: ""}
            ${bedtimeSpread?.let { "<div><span>${esc(context.getString(R.string.export_html_bedtime))}</span><strong>~${it} мин</strong></div>" } ?: ""}
          </div>
          <h2>Графики (самоотчёт)</h2>
          <div class="chart">$chartSvg</div>
          <div class="chart">$sleepSvg</div>
          $physicalBlock
          ${if (medRows.isNotBlank()) """
          <h2>Приём препаратов (сводка)</h2>
          <table><thead><tr><th>Препарат</th><th>Принято/запланировано</th><th>%</th></tr></thead><tbody>$medRows</tbody></table>
          """ else ""}
          <h2>Приём лекарств по дням</h2>
          <p class="meta">Что отмечено принятым в каждый день. Факт самоотчёта, не оценка схемы.</p>
          $medsByDay
          <h2>Ежедневный журнал</h2>
          <table>
            <thead><tr>
              <th>Дата</th><th>Д</th><th>П</th><th>Т</th><th>Р</th><th>Сон</th><th>Алк.</th><th>ПАВ</th><th>Фаза</th><th>Симптомы</th><th>Заметки</th>
            </tr></thead>
            <tbody>${rows.ifBlank { "<tr><td colspan=\"11\">Нет записей</td></tr>" }}</tbody>
          </table>
          <p class="meta">Д — подавленность · П — подъём · Т — тревога · Р — раздражение · ПАВ — психоактивные вещества (самоотчёт)</p>
        </body>
        </html>
        """.trimIndent()
    }

    suspend fun loadMonthEntries(year: Int, month: Int): List<MoodEntryEntity> {
        val (from, to) = DateUtils.monthRange(year, month)
        return moodEntryDao.observeRange(from, to).first()
    }

    /** Day-by-day med listing for PDF renderer. */
    suspend fun loadMedsByDay(year: Int, month: Int): List<Pair<String, List<String>>> {
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val allMeds = medicationDao.observeAll().first()
        return buildMedsByDayLines(from, to, medLogs, allMeds)
    }

    private fun buildMedsByDayLines(
        from: String,
        to: String,
        medLogs: List<com.moodlife.app.data.local.entity.MedicationLogEntity>,
        allMeds: List<com.moodlife.app.data.local.entity.MedicationEntity>,
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
                val slots = MedsUtils.parseIntakeTimes(med.intakeTimes)
                val timed = slots.filter { it != "by-scheme" }
                val scheduled = if (timed.isEmpty()) 1 else timed.size
                val taken = if (timed.isEmpty()) {
                    if (log?.taken == true) 1 else 0
                } else {
                    timed.count { slot ->
                        MedsUtils.isSlotTaken(log?.taken == true, log?.slotsTaken, slot, timed)
                    }
                }
                if (taken <= 0 && (log == null || !log.taken)) return@mapNotNull null
                val dose = medicationRepository.effectiveDosage(med, log)?.takeIf { it.isNotBlank() }
                buildString {
                    append(med.name)
                    if (dose != null) append(" · ").append(dose)
                    append(" · ").append(taken).append('/').append(scheduled)
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
        medLogs: List<com.moodlife.app.data.local.entity.MedicationLogEntity>,
        allMeds: List<com.moodlife.app.data.local.entity.MedicationEntity>,
    ): String {
        val days = buildMedsByDayLines(from, to, medLogs, allMeds)
        if (days.isEmpty()) return "<p>Нет отметок приёма за месяц.</p>"
        val rows = days.joinToString("") { (date, lines) ->
            "<tr><td>${esc(formatDateRu(date))}</td><td>${lines.joinToString("<br/>") { esc(it) }}</td></tr>"
        }
        return "<table><thead><tr><th>Дата</th><th>Принято</th></tr></thead><tbody>$rows</tbody></table>"
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
        return """
          <h2>Физическое состояние (внешние данные)</h2>
          <div class="summary">
            ${avgSteps?.let { "<div><span>Шаги (ср.)</span><strong>$it</strong></div>" } ?: ""}
            ${avgSleep?.let { "<div><span>Сон HC, ч</span><strong>$it</strong></div>" } ?: ""}
            ${avgKcal?.let { "<div><span>Ккал съедено (ср.)</span><strong>$it</strong></div>" } ?: ""}
            ${weight?.let { "<div><span>Вес, кг</span><strong>${String.format(Locale("ru"), "%.1f", it)}</strong></div>" } ?: ""}
          </div>
          <p class="meta">Данные Health Connect / внешних источников. Не диагноз.</p>
        """.trimIndent()
    }

    private fun buildMoodSvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return "<p>Нет данных для графика настроения.</p>"
        val w = 640
        val h = 180
        val pad = 24
        fun x(i: Int) = pad + (w - 2 * pad) * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Int) = (h - pad) - (h - 2 * pad) * (v / 5f)
        fun path(sel: (MoodEntryEntity) -> Int, color: String): String {
            val pts = entries.mapIndexed { i, e -> "${x(i)},${y(sel(e))}" }.joinToString(" ")
            return """<polyline fill="none" stroke="$color" stroke-width="2" points="$pts"/>"""
        }
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Оси настроения">
          <text x="$pad" y="14" font-size="12" fill="#333">Основные оси (0–5)</text>
          ${path({ it.depressed }, "#3A5F9A")}
          ${path({ it.elevated }, "#C9A227")}
          ${path({ it.anxious }, "#7A4F9A")}
          ${path({ it.irritable }, "#C45C3A")}
          <text x="${w - 200}" y="14" font-size="10" fill="#555">Д · П · Т · Р</text>
        </svg>
        """.trimIndent()
    }

    private fun buildSleepSvg(entries: List<MoodEntryEntity>): String {
        val vals = entries.map { it.sleepHours ?: 0f }
        if (vals.all { it == 0f }) return ""
        val w = 640
        val h = 140
        val pad = 24
        val maxY = vals.maxOrNull()?.coerceAtLeast(8f) ?: 8f
        fun x(i: Int) = pad + (w - 2 * pad) * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Float) = (h - pad) - (h - 2 * pad) * (v / maxY)
        val pts = vals.mapIndexed { i, v -> "${x(i)},${y(v)}" }.joinToString(" ")
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Сон">
          <text x="$pad" y="14" font-size="12" fill="#333">Сон, ч</text>
          <polyline fill="none" stroke="#4A3A9A" stroke-width="2" points="$pts"/>
        </svg>
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

    private fun alcoholLabel(v: Int): String =
        MoodScales.ALCOHOL_LABELS.getOrElse(v) { v.toString() }

    private fun substanceLabel(v: Int): String =
        MoodScales.SUBSTANCE_LABELS.getOrElse(v) { v.toString() }
}
