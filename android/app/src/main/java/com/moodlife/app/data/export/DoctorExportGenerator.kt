package com.moodlife.app.data.export

import android.content.Context
import com.moodlife.app.R
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.domain.MoodScales
import com.moodlife.app.util.DateUtils
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
    private val dayNoteDao: DayNoteDao,
) {
    suspend fun generateHtml(year: Int, month: Int): String {
        val entries = loadMonthEntries(year, month)
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val dayNotes = dayNoteDao.listRange(from, to)
        val allMeds = medicationDao.observeAll().first()
        val medNames = allMeds.associateBy { it.id }

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

        return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="utf-8"/>
          <title>${esc(context.getString(R.string.export_html_title, monthLabel))}</title>
          <style>
            body { font-family: system-ui, sans-serif; max-width: 960px; margin: 2rem auto; color: #111; line-height: 1.45; }
            h1 { font-size: 1.35rem; margin-bottom: 0.25rem; }
            .meta { color: #555; font-size: 0.85rem; margin-bottom: 1.5rem; }
            .disclaimer { background: #f5f5f5; border-left: 4px solid #888; padding: 0.75rem 1rem; margin: 1rem 0; font-size: 0.85rem; }
            table { width: 100%; border-collapse: collapse; font-size: 0.8rem; margin: 1rem 0; }
            th, td { border: 1px solid #ccc; padding: 0.35rem 0.5rem; text-align: left; vertical-align: top; }
            th { background: #eee; }
            .summary { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 0.75rem; margin: 1rem 0; }
            .summary div { border: 1px solid #ddd; border-radius: 8px; padding: 0.5rem 0.75rem; }
            .summary strong { display: block; font-size: 1.1rem; }
            @media print { body { margin: 0.5in; } }
          </style>
        </head>
        <body>
          <h1>${esc(context.getString(R.string.export_html_heading))}</h1>
          <p class="meta">${esc(monthLabel)} · экспорт ${esc(exportedAt)} · записей: $totalDays</p>
          <div class="disclaimer">
            Self-tracker, не медицинский диагноз. Подъём настроения ≠ «хорошо». Данные введены пользователем.
            Обсуждайте изменения терапии только с лечащим врачом.
          </div>
          <h2>Средние за месяц</h2>
          <div class="summary">
            <div><span>Подавленность</span><strong>${avg { it.depressed }}</strong></div>
            <div><span>Подъём</span><strong>${avg { it.elevated }}</strong></div>
            <div><span>Тревога</span><strong>${avg { it.anxious }}</strong></div>
            <div><span>Раздражение</span><strong>${avg { it.irritable }}</strong></div>
            <div><span>Сон, ч</span><strong>$avgSleep</strong></div>
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
          ${if (medRows.isNotBlank()) """
          <h2>Приём препаратов</h2>
          <table><thead><tr><th>Препарат</th><th>Принято/запланировано</th><th>%</th></tr></thead><tbody>$medRows</tbody></table>
          """ else ""}
          <h2>Ежедневный журнал</h2>
          <table>
            <thead><tr>
              <th>Дата</th><th>Д</th><th>П</th><th>Т</th><th>Р</th><th>Сон</th><th>Алк.</th><th>ПАВ</th><th>Фаза</th><th>Симптомы</th><th>Заметки</th>
            </tr></thead>
            <tbody>${rows.ifBlank { "<tr><td colspan=\"11\">Нет записей</td></tr>" }}</tbody>
          </table>
          <p class="meta">Д — подавленность · П — подъём · Т — тревога · Р — раздражение</p>
        </body>
        </html>
        """.trimIndent()
    }

    suspend fun loadMonthEntries(year: Int, month: Int): List<MoodEntryEntity> {
        val (from, to) = DateUtils.monthRange(year, month)
        return moodEntryDao.observeRange(from, to).first()
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
