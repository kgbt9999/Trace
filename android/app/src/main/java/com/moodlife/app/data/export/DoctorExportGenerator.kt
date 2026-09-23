package com.moodlife.app.data.export

import android.content.Context
import com.moodlife.app.R
import androidx.compose.ui.graphics.toArgb
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.ExternalHealthDayDao
import com.moodlife.app.data.local.dao.LabResultDao
import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.dao.PeriodDao
import com.moodlife.app.data.local.dao.WarningSignDao
import com.moodlife.app.data.local.dao.WarningTriggerDao
import com.moodlife.app.data.local.entity.LabResultEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.repository.MedDoseSeries
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.data.repository.ReportsRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.domain.CaseHistorySummary
import com.moodlife.app.domain.CrisisContacts
import com.moodlife.app.domain.LabMarkerCatalog
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clinician-facing monthly HTML/PDF data (monitoring themes: sleep, mood axes,
 * med adherence, early-sign FREQUENCY counts). Never diagnosis language.
 * Alcohol/substance self-report is intentionally omitted from clinician export.
 */
@Singleton
class DoctorExportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moodEntryDao: MoodEntryDao,
    private val medicationLogDao: MedicationLogDao,
    private val medicationDao: MedicationDao,
    private val medicationRepository: MedicationRepository,
    private val reportsRepository: ReportsRepository,
    private val labResultDao: LabResultDao,
    private val dayNoteDao: DayNoteDao,
    private val externalHealthDayDao: ExternalHealthDayDao,
    private val warningTriggerDao: WarningTriggerDao,
    private val warningSignDao: WarningSignDao,
    private val periodDao: PeriodDao,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        /** Same palette as Reports med-dose chart. */
        val MED_DOSE_PALETTE_HEX = listOf(
            "#2BBFA0", "#5B8DEF", "#E8A838", "#BA68C8",
            "#E57373", "#66BB6A", "#64B5F6", "#FFB74D",
        )
        private val DISCLAIMER = """
            Самоотчёт пользователя. Не диагноз и не клиническая оценка.
            Не рекомендация начинать, прекращать или менять терапию.
            Формулировки мониторинга (сон, оси настроения, приём лекарств, частота ранних признаков)
            — только наблюдательные, в духе психообразовательного отслеживания.
            Алкоголь и ПАВ в отчёт для врача не включаются.
        """.trimIndent()
    }

    suspend fun generateHtml(year: Int, month: Int): String {
        val entries = loadMonthEntries(year, month)
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val dayNotes = dayNoteDao.listRange(from, to)
        val allMeds = medicationDao.observeVisibleInRange(from, to).first()
        val medById = allMeds.associateBy { it.id }
        val signs = warningSignDao.observeAll().first().associateBy { it.id }
        val triggers = if (entries.isEmpty()) {
            emptyList()
        } else {
            warningTriggerDao.listForEntries(entries.map { it.id })
        }

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

        val earlySignFreq = buildEarlySignFrequency(triggers, signs)

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

        val monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year"
        val exportedAt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())

        val dayRows = entries.joinToString("") { e ->
            """
            <tr>
              <td>${esc(formatDateRu(e.date))}</td>
              <td>${e.depressed}</td><td>${e.elevated}</td><td>${e.anxious}</td><td>${e.irritable}</td>
              <td>${e.sleepHours ?: "—"}</td>
            </tr>
            """.trimIndent()
        }

        val medsByDay = buildMedsByDayHtml(from, to, medLogs, allMeds, includeUntaken = true)
        val medDoseSeries = reportsRepository.observeMonthMedDoseSeries(year, month).first()
        val labResults = labResultDao.observeRange(from, to).first()
        val medDoseSvg = buildMedDoseSvg(medDoseSeries)
        val labsBlock = buildLabsHtml(labResults)
        val notesBlock = buildNotesExcerptsHtml(dayNotes, entries)
        val moodSvg = buildPolaritySvg(entries)
        val heatSvg = buildMoodHeatmapSvg(entries)
        val sleepMoodSvg = buildSleepMoodDualSvg(entries)
        val sleepSvg = buildSleepSvg(entries)

        val polarityVals = entries.map { (it.elevated - it.depressed).toFloat() }
        val avgPolarity = if (polarityVals.isEmpty()) "—"
        else String.format(Locale("ru"), "%+.1f", polarityVals.average())
        val polMin = polarityVals.minOrNull()?.let { String.format(Locale("ru"), "%+.0f", it) } ?: "—"
        val polMax = polarityVals.maxOrNull()?.let { String.format(Locale("ru"), "%+.0f", it) } ?: "—"
        val sleepBadDays = entries.count { (it.sleepHours ?: 8f) < 6f }
        val sleepBadPct = if (totalDays > 0) sleepBadDays * 100 / totalDays else 0
        val adherenceOverall = if (medAdherence.isEmpty()) null else {
            val sched = medAdherence.values.sumOf { it.first }
            val taken = medAdherence.values.sumOf { it.second }
            if (sched > 0) taken * 100 / sched else null
        }
        val adherenceLabel = adherenceOverall?.let { "$it%" } ?: "—"
        val missedOverall = medAdherence.values.sumOf { (s, t) -> (s - t).coerceAtLeast(0) }
        val moodCardClass = when {
            polarityVals.isEmpty() -> ""
            (polarityVals.average()) >= 1.5 -> "warn"
            (polarityVals.average()) <= -1.5 -> "warn"
            else -> "ok"
        }
        val sleepCardClass = when {
            sleepBadDays == 0 -> "ok"
            sleepBadPct >= 20 -> "bad"
            else -> "warn"
        }
        val medCardClass = when {
            adherenceOverall == null -> ""
            adherenceOverall >= 90 -> "ok"
            adherenceOverall >= 70 -> "warn"
            else -> "bad"
        }

        val caseHistory = loadCaseHistory(year, month)
        val caseHistoryRows = caseHistory.joinToString("") { row ->
            val bandLabel = CaseHistorySummary.bandLabel(row.band)
            val sevLabel = CaseHistorySummary.severityLabel(row.severity)
            val badgeClass = when (row.band) {
                CaseHistorySummary.Band.DIP -> "badge-dep"
                CaseHistorySummary.Band.RISE -> "badge-man"
                CaseHistorySummary.Band.MILD_RISE -> "badge-hyp"
                CaseHistorySummary.Band.EVEN -> "badge-eut"
                CaseHistorySummary.Band.MIXED -> "badge-mix"
            }
            """
            <tr>
              <td style="white-space:nowrap;font-variant-numeric:tabular-nums;font-size:11px">${esc(row.periodLabel)}</td>
              <td><span class="badge $badgeClass">${esc(bandLabel)}</span></td>
              <td>${esc(sevLabel)}</td>
              <td>${esc(row.medications)}</td>
              <td>${esc(row.routine)}</td>
              <td>${esc(row.keyEvents)}</td>
            </tr>
            """.trimIndent()
        }

        val earlyBars = earlySignFreq.take(8).joinToString("") { (name, count) ->
            val pct = if (totalDays > 0) (count * 100 / totalDays).coerceIn(0, 100) else 0
            val color = when {
                pct >= 60 -> "var(--red)"
                pct >= 35 -> "var(--orange)"
                else -> "var(--yellow)"
            }
            """
            <div class="prodrome-item">
              <div class="prodrome-label"><span>${esc(name)}</span><span style="color:$color;font-weight:600">$pct%</span></div>
              <div class="prodrome-bar-bg"><div class="prodrome-bar-fill" style="width:$pct%;background:$color"></div></div>
            </div>
            """.trimIndent()
        }

        val medMeta = linkedMapOf<String, Pair<String, String>>()
        for (log in medLogs) {
            val med = medById[log.medicationId] ?: continue
            val name = medicationRepository.effectiveName(med, log)
            if (name !in medMeta) {
                val dose = medicationRepository.effectiveDosage(med, log)?.ifBlank { null }
                    ?: med.dosage?.ifBlank { null }
                    ?: "—"
                val slots = MedsUtils.parseIntakeTimes(medicationRepository.effectiveIntakeTimes(med, log))
                    .filter { it != "by-scheme" }
                val freq = when {
                    slots.isEmpty() -> if (med.isRegular) "по схеме" else "по необходимости"
                    slots.size == 1 -> "1 раз в день"
                    else -> "${slots.size} раза в день"
                }
                medMeta[name] = dose to freq
            }
        }
        val medTableRows = medAdherence.entries.joinToString("") { (name, pair) ->
            val (scheduled, taken) = pair
            val pct = if (scheduled > 0) (taken * 100 / scheduled) else 0
            val missed = (scheduled - taken).coerceAtLeast(0)
            val color = when {
                pct >= 90 -> "var(--green)"
                pct >= 70 -> "var(--yellow)"
                else -> "var(--red)"
            }
            val (dose, freq) = medMeta[name] ?: ("—" to "—")
            """
            <tr>
              <td><strong>${esc(name)}</strong></td>
              <td>${esc(dose)}</td>
              <td>${esc(freq)}</td>
              <td>$taken / $scheduled</td>
              <td>$missed</td>
              <td style="color:$color;font-weight:600">$pct%</td>
            </tr>
            """.trimIndent()
        }

        val last7 = entries.takeLast(7)
        val radarSvg = buildStateProfileSvg(last7.ifEmpty { entries })
        val heatHtml = buildPolarityCalendarHeatmap(entries, from, to)
        val triggersHtml = buildObservedTriggersHtml(entries, earlySignFreq, missedOverall)
        val doctorName = settingsRepository.get(SettingsRepository.KEY_CRISIS_DOCTOR)?.trim().orEmpty().ifBlank { "—" }
        val crisisHtml = buildCrisisLevelsHtml(
            doctor = doctorName,
            support = settingsRepository.get(SettingsRepository.KEY_CRISIS_SUPPORT).orEmpty(),
            notes = settingsRepository.get(SettingsRepository.KEY_CRISIS_NOTES).orEmpty(),
            wishes = settingsRepository.get(SettingsRepository.KEY_CRISIS_WISHES).orEmpty(),
            avoid = settingsRepository.get(SettingsRepository.KEY_CRISIS_AVOID).orEmpty(),
            contacts = CrisisContacts.parse(settingsRepository.get(SettingsRepository.KEY_CRISIS_CONTACTS)),
        )

        val (riskLevel, riskTitle, riskDesc) = observationalRisk(
            sleepBadDays = sleepBadDays,
            earlySignCount = earlySignFreq.sumOf { it.second },
            avgPol = polarityVals.average().takeIf { polarityVals.isNotEmpty() },
            totalDays = totalDays,
        )
        val moodNote = observationalMoodNote(polarityVals, entries)
        val sleepNote = observationalSleepNote(entries, sleepBadDays)
        val radarNote = if (last7.isEmpty()) {
            "Недостаточно данных за последние 7 дней."
        } else {
            "Средние значения шкал дневника за последние ${last7.size} дн. Не клинический профиль."
        }
        val periodRange = "${formatDateRu(from)} — ${formatDateRu(to)}"
        val adherenceColor = when {
            adherenceOverall == null -> "var(--text)"
            adherenceOverall >= 90 -> "var(--green)"
            adherenceOverall >= 70 -> "var(--yellow)"
            else -> "var(--red)"
        }

        return DoctorClinicalReportHtml.render(
            DoctorClinicalReportHtml.Model(
                title = context.getString(R.string.export_html_title, monthLabel),
                subtitle = "Мониторинг самоотчёта · дневник настроения и симптомов",
                periodLabel = periodRange,
                exportedAt = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date()),
                patientName = "—",
                patientDob = "—",
                diagnosis = "— (не заполняется приложением)",
                doctorName = doctorName,
                nextVisit = "—",
                adherenceLabel = adherenceLabel,
                adherenceColor = adherenceColor,
                disclaimer = DISCLAIMER,
                avgPolarity = avgPolarity,
                polarityRange = "$polMin до $polMax",
                moodCardClass = moodCardClass,
                sleepBadDays = sleepBadDays,
                sleepBadPct = sleepBadPct,
                sleepCardClass = sleepCardClass,
                totalDays = totalDays,
                medCardClass = medCardClass,
                medMissesSub = if (missedOverall > 0) "$missedOverall пропусков слотов" else "по отметкам дневника",
                riskLevel = riskLevel,
                riskTitle = riskTitle,
                riskDesc = riskDesc,
                polaritySvg = moodSvg,
                moodNote = moodNote,
                sleepMoodSvg = sleepMoodSvg.ifBlank { sleepSvg },
                sleepNote = sleepNote,
                radarSvg = radarSvg,
                radarNote = radarNote,
                heatmapHtml = heatHtml,
                heatmapNote = "Цвет — полярность (подъём − спад) по дням. Самоотчёт.",
                triggersHtml = triggersHtml,
                prodromeHtml = earlyBars,
                medTableRows = medTableRows,
                medNote = if (missedOverall > 0) {
                    "Пропуски слотов: $missedOverall. Самоотчёт пользователя, не оценка терапии."
                } else {
                    "Данные из ежедневного трекера. Не рекомендация менять схему."
                },
                medDoseSvg = medDoseSvg,
                historyRows = caseHistoryRows,
                crisisHtml = crisisHtml,
            ),
        )
    }

    /** Plain-language monthly summary for the patient (HTML only). */
    suspend fun generatePatientHtml(year: Int, month: Int): String {
        val entries = loadMonthEntries(year, month)
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val labResults = labResultDao.observeRange(from, to).first()
        val allMeds = medicationDao.observeVisibleInRange(from, to).first()
        val medById = allMeds.associateBy { it.id }
        val totalDays = entries.size
        val avgSleep = entries.mapNotNull { it.sleepHours?.toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.let { String.format(Locale("ru"), "%.1f", it) }
            ?: "—"
        val polarityVals = entries.map { (it.elevated - it.depressed).toFloat() }
        val avgPolarity = if (polarityVals.isEmpty()) "—"
        else String.format(Locale("ru"), "%+.1f", polarityVals.average())
        val sleepBadDays = entries.count { (it.sleepHours ?: 8f) < 6f }
        var scheduled = 0
        var taken = 0
        for (log in medLogs) {
            val med = medById[log.medicationId] ?: continue
            val raw = medicationRepository.effectiveIntakeTimes(med, log)
            val slots = MedsUtils.parseIntakeTimes(raw).filter { it != "by-scheme" }
            val s = if (slots.isEmpty()) 1 else slots.size
            val t = if (slots.isEmpty()) {
                if (log.taken) 1 else 0
            } else {
                slots.count { MedsUtils.isSlotTaken(log.taken, log.slotsTaken, it, slots) }
            }
            scheduled += s
            taken += t
        }
        val adherence = if (scheduled > 0) "${taken * 100 / scheduled}%" else "—"
        val labsCount = labResults.size
        val monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year"
        val exportedAt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())
        val moodSvg = buildPolaritySvg(entries)
        val sleepMoodSvg = buildSleepMoodDualSvg(entries)

        return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1"/>
          <title>${esc(context.getString(R.string.export_patient_html_title, monthLabel))}</title>
          <style>
            :root {
              --bg: #f7f8fa; --surface: #ffffff; --surface-2: #f0f2f5; --border: #e2e6ed;
              --text: #1a1d23; --text-2: #5a6070; --text-3: #8b919f;
              --green: #1a9e6e; --yellow: #e8a020; --blue: #2a78d6;
              --r-lg: 12px; --shadow: 0 1px 4px rgba(0,0,0,.07), 0 4px 16px rgba(0,0,0,.05);
            }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: system-ui, -apple-system, "Segoe UI", sans-serif; background: var(--bg); color: var(--text); font-size: 13px; line-height: 1.55; }
            .report-header { background: var(--surface); border-bottom: 2px solid var(--border); padding: 24px 28px 18px; }
            .report-header h1 { font-size: 17px; font-weight: 600; }
            .subtitle { font-size: 12px; color: var(--text-2); margin-top: 2px; }
            .page { max-width: 900px; margin: 0 auto; padding: 24px 20px 48px; }
            .section { margin-bottom: 28px; }
            .section-title { font-size: 11px; font-weight: 600; letter-spacing: .6px; color: var(--text-3); text-transform: uppercase; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
            .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); box-shadow: var(--shadow); padding: 16px 18px; margin-top: 12px; }
            .card-title { font-size: 12px; font-weight: 600; margin-bottom: 12px; }
            .card-note { font-size: 11px; color: var(--text-2); margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border); font-style: italic; }
            .grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
            .metric-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); padding: 14px 16px; box-shadow: var(--shadow); }
            .m-label { font-size: 10.5px; color: var(--text-2); margin-bottom: 4px; }
            .m-value { font-size: 24px; font-weight: 600; line-height: 1; margin-bottom: 3px; font-variant-numeric: tabular-nums; }
            .m-sub { font-size: 11px; color: var(--text-2); }
            .note { border-left: 3px solid var(--blue); background: var(--surface); padding: 12px 14px; margin: 14px 0; font-size: 12px; color: var(--text-2); }
            .chart { overflow-x: auto; }
            ul { padding-left: 1.2rem; margin-top: 8px; }
            li { margin: 0.35rem 0; color: var(--text-2); }
            .footer { border-top: 1px solid var(--border); margin-top: 24px; padding-top: 12px; font-size: 11px; color: var(--text-3); }
            @media (max-width: 640px) { .grid-3 { grid-template-columns: 1fr; } }
          </style>
        </head>
        <body>
          <div class="report-header">
            <h1>${esc(context.getString(R.string.export_patient_html_heading))}</h1>
            <div class="subtitle">${esc(monthLabel)} · ${esc(exportedAt)}</div>
          </div>
          <div class="page">
            <div class="note">
              Это ваш личный обзор того, что вы отмечали в Trace.
              Приложение не ставит диагнозов и не советует менять лекарства.
            </div>

            <div class="section">
              <div class="section-title">Ключевые показатели месяца</div>
              <div class="grid-3">
                <div class="metric-card"><div class="m-label">Дней с записью</div><div class="m-value">$totalDays</div><div class="m-sub">ваш дневник</div></div>
                <div class="metric-card"><div class="m-label">Средняя полярность</div><div class="m-value">$avgPolarity</div><div class="m-sub">шкала −5…+5</div></div>
                <div class="metric-card"><div class="m-label">Средний сон</div><div class="m-value">$avgSleep</div><div class="m-sub">часов · дней &lt;6 ч: $sleepBadDays</div></div>
              </div>
              <div class="grid-3" style="margin-top:12px">
                <div class="metric-card"><div class="m-label">Приём лекарств</div><div class="m-value">$adherence</div><div class="m-sub">по вашим отметкам</div></div>
                <div class="metric-card"><div class="m-label">Анализы</div><div class="m-value">$labsCount</div><div class="m-sub">записей</div></div>
                <div class="metric-card"><div class="m-label">Отметок приёма</div><div class="m-value">${medLogs.size}</div><div class="m-sub">записей логов</div></div>
              </div>
            </div>

            ${if (moodSvg.isNotBlank()) """
            <div class="section">
              <div class="section-title">Динамика настроения</div>
              <div class="card">
                <div class="card-title">Полярность по дням (−5…+5)</div>
                <div class="chart">$moodSvg</div>
                <div class="card-note">Линия и точки — ваши шкалы спада/подъёма. Не диагноз.</div>
              </div>
            </div>
            """ else ""}

            ${if (sleepMoodSvg.isNotBlank()) """
            <div class="section">
              <div class="section-title">Сон и настроение</div>
              <div class="card">
                <div class="card-title">Часы сна и полярность</div>
                <div class="chart">$sleepMoodSvg</div>
              </div>
            </div>
            """ else ""}

            <div class="section">
              <div class="section-title">Что можно обсудить с врачом</div>
              <div class="card">
                <ul>
                  <li>Сон: сколько часов в среднем и как менялся режим.</li>
                  <li>Настроение: дни, когда шкалы спада или подъёма были выше обычного.</li>
                  <li>Лекарства: насколько регулярно отмечали приём.</li>
                  <li>Анализы: какие показатели вы внесли и когда.</li>
                </ul>
              </div>
            </div>

            <div class="footer">Самоотчёт. Не диагноз. Не рекомендация менять терапию.</div>
          </div>
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

    suspend fun loadMedDoseSeries(year: Int, month: Int): List<MedDoseSeries> =
        reportsRepository.observeMonthMedDoseSeries(year, month).first()

    suspend fun loadLabResults(year: Int, month: Int): List<LabResultEntity> {
        val (from, to) = DateUtils.monthRange(year, month)
        return labResultDao.getRange(from, to)
    }

    suspend fun loadEarlySignFrequency(year: Int, month: Int): List<Pair<String, Int>> {
        val entries = loadMonthEntries(year, month)
        if (entries.isEmpty()) return emptyList()
        val signs = warningSignDao.observeAll().first().associateBy { it.id }
        val triggers = warningTriggerDao.listForEntries(entries.map { it.id })
        return buildEarlySignFrequency(triggers, signs)
    }

    suspend fun loadNoteExcerpts(year: Int, month: Int, maxLen: Int = 120): List<Pair<String, String>> {
        val (from, to) = DateUtils.monthRange(year, month)
        val dayNotes = dayNoteDao.listRange(from, to)
        val entries = loadMonthEntries(year, month)
        return buildNoteExcerpts(dayNotes, entries, maxLen)
    }

    suspend fun loadMedAdherence(year: Int, month: Int): List<Triple<String, String, Int>> {
        val (from, to) = DateUtils.monthRange(year, month)
        val medLogs = medicationLogDao.listRange(from, to)
        val allMeds = medicationDao.observeVisibleInRange(from, to).first()
        val medById = allMeds.associateBy { it.id }
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
        return medAdherence.map { (name, pair) ->
            val (scheduled, taken) = pair
            val pct = if (scheduled > 0) taken * 100 / scheduled else 0
            Triple(name, "$taken/$scheduled", pct)
        }
    }

    data class CrisisPlanSnapshot(
        val doctor: String,
        val support: String,
        val notes: String,
        val wishes: String,
        val avoid: String,
        val contacts: List<Pair<String, String>>,
    )

    suspend fun loadCrisisPlan(): CrisisPlanSnapshot {
        val contacts = CrisisContacts.parse(settingsRepository.get(SettingsRepository.KEY_CRISIS_CONTACTS))
            .map { it.label to it.phone }
        return CrisisPlanSnapshot(
            doctor = settingsRepository.get(SettingsRepository.KEY_CRISIS_DOCTOR)?.trim().orEmpty(),
            support = settingsRepository.get(SettingsRepository.KEY_CRISIS_SUPPORT)?.trim().orEmpty(),
            notes = settingsRepository.get(SettingsRepository.KEY_CRISIS_NOTES)?.trim().orEmpty(),
            wishes = settingsRepository.get(SettingsRepository.KEY_CRISIS_WISHES)?.trim().orEmpty(),
            avoid = settingsRepository.get(SettingsRepository.KEY_CRISIS_AVOID)?.trim().orEmpty(),
            contacts = contacts,
        )
    }

    /**
     * Lookback summary for doctor PDF case-history table (diary bands, not diagnosis).
     */
    suspend fun loadCaseHistory(year: Int, month: Int, lookbackMonths: Int = 11): List<com.moodlife.app.domain.CaseHistorySummary.Row> {
        val (_, toMonthTo) = DateUtils.monthRange(year, month)
        val end = java.time.LocalDate.parse(toMonthTo)
        val start = end.minusMonths(lookbackMonths.toLong()).withDayOfMonth(1)
        val from = start.toString()
        val to = end.toString()
        val entries = moodEntryDao.observeRange(from, to).first()
        val medLines = buildMedsByDayLines(
            from,
            to,
            medicationLogDao.listRange(from, to),
            medicationDao.observeVisibleInRange(from, to).first(),
            includeUntaken = false,
        ).toMap()
        val notes = dayNoteDao.listRange(from, to)
            .groupBy { it.date }
            .mapValues { (_, list) ->
                list.map { it.content.trim() }.filter { it.isNotEmpty() }.joinToString("; ")
            }
        val entryNotes = entries.mapNotNull { e ->
            e.note?.trim()?.takeIf { it.isNotEmpty() }?.let { e.date to it }
        }.toMap()
        val mergedNotes = (entryNotes.keys + notes.keys).associateWith { d ->
            listOfNotNull(entryNotes[d], notes[d]).filter { it.isNotBlank() }.joinToString("; ")
        }
        return com.moodlife.app.domain.CaseHistorySummary.build(
            com.moodlife.app.domain.CaseHistorySummary.Inputs(
                entries = entries,
                medsByDate = medLines,
                notesByDate = mergedNotes,
            ),
            minDays = 4,
        )
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

    private fun buildMedDoseSvg(series: List<MedDoseSeries>): String {
        val nonEmpty = series.filter { it.points.isNotEmpty() }
        if (nonEmpty.isEmpty()) return ""
        val xLabels = nonEmpty
            .flatMap { s -> s.points.map { it.first } }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        val w = 720
        val h = 220
        val padL = 36
        val padR = 16
        val padT = 36
        val padB = 28
        val maxY = (nonEmpty.flatMap { it.points.map { p -> p.second } }.maxOrNull() ?: 1f)
            .coerceAtLeast(1f) * 1.1f
        fun x(i: Int) = padL + (w - padL - padR) * i / (xLabels.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Float) = (h - padB) - (h - padT - padB) * (v / maxY).coerceIn(0f, 1f)
        val grid = (0..4).joinToString("") { i ->
            val v = maxY * i / 4f
            val gy = y(v)
            val lab = formatLabNumber(v)
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#D5DCE6"/>
               <text x="4" y="${gy + 3}" font-size="10" fill="#5A6A80">$lab</text>"""
        }
        val allNames = nonEmpty.map { it.name }
        val paths = nonEmpty.mapIndexed { si, s ->
            val color = com.moodlife.app.domain.MedAccentColors.accentHexForName(s.name, allNames)
            val byX = s.points.associate { it.first to it.second }
            val indexed = xLabels.mapIndexedNotNull { i, lab ->
                val v = byX[lab] ?: return@mapIndexedNotNull null
                i to (x(i) to y(v))
            }
            val lines = indexed.zipWithNext().mapNotNull { (a, b) ->
                if (b.first != a.first + 1) return@mapNotNull null
                """<line x1="${a.second.first}" y1="${a.second.second}" x2="${b.second.first}" y2="${b.second.second}" stroke="$color" stroke-width="2"/>"""
            }.joinToString("")
            val dots = indexed.joinToString("") { (_, pt) ->
                """<circle cx="${pt.first}" cy="${pt.second}" r="3.2" fill="$color"/>"""
            }
            lines + dots
        }.joinToString("")
        val legend = nonEmpty.map { s ->
            val color = com.moodlife.app.domain.MedAccentColors.accentHexForName(s.name, allNames)
            val i = nonEmpty.indexOf(s)
            val lx = padL + i * 110
            """<circle cx="$lx" cy="12" r="4" fill="$color"/>
               <text x="${lx + 8}" y="15" font-size="10" fill="#0B1C3D">${esc(s.name.take(14))}</text>"""
        }.joinToString("")
        val xTick = xLabels.mapIndexed { i, lab ->
            if (i % ((xLabels.size / 8).coerceAtLeast(1)) == 0 || i == xLabels.lastIndex) {
                """<text x="${x(i)}" y="${h - 6}" font-size="9" fill="#5A6A80" text-anchor="middle">${esc(lab)}</text>"""
            } else ""
        }.joinToString("")
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Дозы препаратов">
          $legend
          $grid
          $paths
          $xTick
        </svg>
        """.trimIndent()
    }

    private fun buildLabsHtml(labs: List<LabResultEntity>): String {
        if (labs.isEmpty()) return ""
        val rows = labs.sortedBy { it.date }.joinToString("") { row ->
            """
            <tr>
              <td>${esc(row.name)}</td>
              <td>${esc(row.valueText)}</td>
              <td>${esc(row.unit.orEmpty().ifBlank { "—" })}</td>
              <td>${esc(formatDateRu(row.date))}</td>
              <td>${esc(row.clinic.orEmpty().ifBlank { "—" })}</td>
            </tr>
            """.trimIndent()
        }
        val chartGroups = labs
            .filter { it.valueNumeric != null }
            .groupBy { it.name.trim().lowercase() }
        val charts = chartGroups.entries
            .sortedBy { it.value.firstOrNull()?.name.orEmpty() }
            .mapNotNull { (_, rowsForName) ->
                val label = rowsForName.first().name
                val unit = rowsForName.mapNotNull { it.unit }.firstOrNull().orEmpty()
                val points = rowsForName
                    .sortedBy { it.date }
                    .mapNotNull { r ->
                        val v = r.valueNumeric ?: return@mapNotNull null
                        labDayLabel(r.date) to v
                    }
                if (points.isEmpty()) return@mapNotNull null
                val colorInt = LabMarkerCatalog.colorForName(label).toArgb()
                val color = String.format("#%06X", colorInt and 0xFFFFFF)
                val title = if (unit.isNotEmpty()) "$label ($unit)" else label
                val svg = buildSparseLineSvg(title = title, points = points, color = color)
                """<h3>${esc(title)}</h3><div class="chart">$svg</div>"""
            }.joinToString("\n")
        return """
          <h2 style="font-size:11px;font-weight:600;letter-spacing:.6px;color:#8b919f;text-transform:uppercase;margin:28px 0 14px;padding-bottom:8px;border-bottom:1px solid #e2e6ed">Лабораторные показатели</h2>
          <p style="font-size:11px;color:#5a6070;margin-bottom:10px">Значения внесены пользователем. Не интерпретация, не референсные диапазоны и не диагноз.</p>
          <div class="card" style="padding:0;overflow:hidden">
          <table>
            <thead><tr>
              <th>Название</th><th>Значение</th><th>Ед.</th><th>Дата</th><th>Клиника</th>
            </tr></thead>
            <tbody>$rows</tbody>
          </table>
          </div>
          <h3 style="font-size:12px;font-weight:600;margin:18px 0 10px">Динамика по названию</h3>
          <p style="font-size:11px;color:#5a6070;margin-bottom:10px">Графики по числовым значениям с одинаковым названием. Самоотчёт.</p>
          $charts
        """.trimIndent()
    }

    private fun buildSparseLineSvg(
        title: String,
        points: List<Pair<String, Float>>,
        color: String,
    ): String {
        if (points.isEmpty()) return ""
        val w = 720
        val h = 160
        val padL = 40
        val padR = 16
        val padT = 28
        val padB = 28
        val maxY = (points.maxOf { it.second } * 1.15f).coerceAtLeast(0.1f)
        fun x(i: Int) = padL + (w - padL - padR) * i / (points.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Float) = (h - padB) - (h - padT - padB) * (v / maxY).coerceIn(0f, 1f)
        val grid = (0..4).joinToString("") { i ->
            val v = maxY * i / 4f
            val gy = y(v)
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#D5DCE6"/>
               <text x="2" y="${gy + 3}" font-size="9" fill="#5A6A80">${formatLabNumber(v)}</text>"""
        }
        val ptsAttr = points.mapIndexed { i, p -> "${x(i)},${y(p.second)}" }.joinToString(" ")
        val dots = points.mapIndexed { i, p ->
            """<circle cx="${x(i)}" cy="${y(p.second)}" r="3" fill="$color"/>"""
        }.joinToString("")
        val xLabs = points.mapIndexed { i, p ->
            if (i % ((points.size / 8).coerceAtLeast(1)) == 0 || i == points.lastIndex) {
                """<text x="${x(i)}" y="${h - 6}" font-size="9" fill="#5A6A80" text-anchor="middle">${esc(p.first)}</text>"""
            } else ""
        }.joinToString("")
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="${esc(title)}">
          <text x="$padL" y="14" font-size="12" fill="#0B1C3D">${esc(title)}</text>
          $grid
          <polyline fill="none" stroke="$color" stroke-width="2" points="$ptsAttr"/>
          $dots
          $xLabs
        </svg>
        """.trimIndent()
    }

    private fun labDayLabel(iso: String): String =
        iso.takeLast(2).trimStart('0').ifEmpty { iso.takeLast(2) }

    private fun formatLabNumber(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString()
        else String.format(Locale("ru"), "%.2f", v)

    private fun buildPolaritySvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return ""
        val w = 720
        val h = 240
        val padL = 40
        val padR = 16
        val padT = 24
        val padB = 28
        val plotW = (w - padL - padR).toFloat()
        val plotH = (h - padT - padB).toFloat()
        fun x(i: Int) = padL + plotW * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun y(v: Float) = padT + plotH * (1f - (v.coerceIn(-5f, 5f) + 5f) / 10f)
        val elevTop = y(5f)
        val elevBot = y(2f)
        val lowTop = y(-2f)
        val lowBot = y(-5f)
        val grid = (-5..5).joinToString("") { tick ->
            val gy = y(tick.toFloat())
            val lab = if (tick > 0) "+$tick" else "$tick"
            """<line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#E2E6ED" stroke-width="1"/>
               <text x="${padL - 6}" y="${gy + 3}" font-size="10" fill="#5A6070" text-anchor="end">$lab</text>"""
        }
        val zeroY = y(0f)
        val pts = entries.mapIndexed { i, e ->
            val v = (e.elevated - e.depressed).toFloat().coerceIn(-5f, 5f)
            x(i) to v
        }
        val line = pts.joinToString(" ") { (px, v) -> "$px,${y(v)}" }
        val dots = pts.mapIndexed { i, (px, v) ->
            val fill = when {
                v >= 2f -> "#E34948"
                v <= -2f -> "#1A4F8A"
                else -> "#7EB3E8"
            }
            """<circle cx="$px" cy="${y(v)}" r="4" fill="$fill"/>"""
        }.joinToString("")
        val xLabels = entries.mapIndexed { i, e ->
            if (i % ((entries.size / 8).coerceAtLeast(1)) == 0 || i == entries.lastIndex) {
                """<text x="${x(i)}" y="${h - 6}" font-size="9" fill="#5A6070" text-anchor="middle">${e.date.takeLast(2)}</text>"""
            } else ""
        }.joinToString("")
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Полярность настроения">
          <rect x="$padL" y="$elevTop" width="$plotW" height="${elevBot - elevTop}" fill="#E34948" opacity="0.12"/>
          <rect x="$padL" y="$lowTop" width="$plotW" height="${lowBot - lowTop}" fill="#2A78D6" opacity="0.12"/>
          $grid
          <line x1="$padL" y1="$zeroY" x2="${w - padR}" y2="$zeroY" stroke="#1A1D23" stroke-width="1.2" opacity="0.45"/>
          <polyline fill="none" stroke="#2A78D6" stroke-width="2.5" points="$line"/>
          $dots
          $xLabels
        </svg>
        """.trimIndent()
    }

    private fun buildSleepMoodDualSvg(entries: List<MoodEntryEntity>): String {
        if (entries.isEmpty()) return ""
        val hasSleep = entries.any { it.sleepHours != null }
        if (!hasSleep) return ""
        val w = 720
        val h = 240
        val padL = 40
        val padR = 40
        val padT = 24
        val padB = 28
        val plotW = (w - padL - padR).toFloat()
        val plotH = (h - padT - padB).toFloat()
        fun x(i: Int) = padL + plotW * i / (entries.size - 1).coerceAtLeast(1).toFloat()
        fun ySleep(hours: Float) = padT + plotH * (1f - hours.coerceIn(0f, 12f) / 12f)
        fun yMood(v: Float) = padT + plotH * (1f - (v.coerceIn(-5f, 5f) + 5f) / 10f)
        val bandTop = ySleep(9f)
        val bandBot = ySleep(7f)
        val barW = (plotW / entries.size.coerceAtLeast(1) * 0.55f).coerceIn(4f, 14f)
        val bars = entries.mapIndexed { i, e ->
            val hours = e.sleepHours ?: return@mapIndexed ""
            val top = ySleep(hours)
            val base = ySleep(0f)
            val fill = if (hours < 6f) "#E34948" else "#1BAF7A"
            val cx = x(i)
            """<rect x="${cx - barW / 2}" y="$top" width="$barW" height="${(base - top).coerceAtLeast(1f)}" fill="$fill" opacity="0.55" rx="2"/>"""
        }.joinToString("")
        val moodLine = entries.mapIndexed { i, e ->
            val v = (e.elevated - e.depressed).toFloat()
            "${x(i)},${yMood(v)}"
        }.joinToString(" ")
        val sleepTicks = listOf(0, 3, 6, 9, 12).joinToString("") { t ->
            val gy = ySleep(t.toFloat())
            """<text x="${padL - 6}" y="${gy + 3}" font-size="9" fill="#5A6070" text-anchor="end">$t</text>
               <line x1="$padL" y1="$gy" x2="${w - padR}" y2="$gy" stroke="#E2E6ED"/>"""
        }
        val moodTicks = listOf(-5, 0, 5).joinToString("") { t ->
            val gy = yMood(t.toFloat())
            val lab = if (t > 0) "+$t" else "$t"
            """<text x="${w - padR + 6}" y="${gy + 3}" font-size="9" fill="#EB6834" text-anchor="start">$lab</text>"""
        }
        val xLabels = entries.mapIndexed { i, e ->
            if (i % ((entries.size / 8).coerceAtLeast(1)) == 0 || i == entries.lastIndex) {
                """<text x="${x(i)}" y="${h - 6}" font-size="9" fill="#5A6070" text-anchor="middle">${e.date.takeLast(2)}</text>"""
            } else ""
        }.joinToString("")
        return """
        <svg viewBox="0 0 $w $h" width="100%" role="img" aria-label="Сон и полярность">
          <rect x="$padL" y="$bandTop" width="$plotW" height="${bandBot - bandTop}" fill="#1BAF7A" opacity="0.12"/>
          $sleepTicks
          $moodTicks
          $bars
          <polyline fill="none" stroke="#EB6834" stroke-width="2.2" points="$moodLine"/>
          $xLabels
          <text x="$padL" y="14" font-size="11" fill="#1BAF7A">Сон, ч</text>
          <text x="${w - padR - 80}" y="14" font-size="11" fill="#EB6834">Полярность</text>
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

    private fun buildEarlySignFrequency(
        triggers: List<com.moodlife.app.data.local.entity.WarningTriggerEntity>,
        signs: Map<String, com.moodlife.app.data.local.entity.EarlyWarningSignEntity>,
    ): List<Pair<String, Int>> {
        if (triggers.isEmpty()) return emptyList()
        val counts = linkedMapOf<String, Int>()
        for (t in triggers) {
            val name = signs[t.warningSignId]?.name ?: t.warningSignId
            counts[name] = (counts[name] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    private fun buildEarlySignFrequencyHtml(freq: List<Pair<String, Int>>): String {
        if (freq.isEmpty()) {
            return """
              <h2>4. Ранние признаки — частота</h2>
              <p class="meta">Нет отмеченных ранних признаков за месяц. Частота — не эпизод и не диагноз.</p>
            """.trimIndent()
        }
        val rows = freq.joinToString("") { (name, count) ->
            "<tr><td>${esc(name)}</td><td>$count</td></tr>"
        }
        return """
          <h2>4. Ранние признаки — частота</h2>
          <p class="meta">Сколько раз признак отмечен в дневнике. Наблюдательный подсчёт · не эпизод и не диагноз.</p>
          <table><thead><tr><th>Признак</th><th>Раз</th></tr></thead><tbody>$rows</tbody></table>
        """.trimIndent()
    }

    private fun buildNoteExcerpts(
        dayNotes: List<com.moodlife.app.data.local.entity.DayNoteEntity>,
        entries: List<MoodEntryEntity>,
        maxLen: Int,
    ): List<Pair<String, String>> {
        val fromNotes = dayNotes.map { n ->
            n.date to n.content.trim().take(maxLen)
        }
        val fromEntry = entries.mapNotNull { e ->
            val note = e.note?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            e.date to note.take(maxLen)
        }
        return (fromNotes + fromEntry)
            .filter { it.second.isNotBlank() }
            .sortedBy { it.first }
            .take(40)
    }

    private fun buildNotesExcerptsHtml(
        dayNotes: List<com.moodlife.app.data.local.entity.DayNoteEntity>,
        entries: List<MoodEntryEntity>,
    ): String {
        val excerpts = buildNoteExcerpts(dayNotes, entries, maxLen = 120)
        if (excerpts.isEmpty()) {
            return """
              <h2>6. Заметки</h2>
              <p class="meta">Коротких заметок за месяц нет (длинный текст опущен).</p>
            """.trimIndent()
        }
        val rows = excerpts.joinToString("") { (date, text) ->
            "<tr><td>${esc(formatDateRu(date))}</td><td>${esc(text)}</td></tr>"
        }
        return """
          <h2>6. Заметки (краткие выдержки)</h2>
          <p class="meta">До 120 символов на запись. Полный текст в приложении.</p>
          <table><thead><tr><th>Дата</th><th>Текст</th></tr></thead><tbody>$rows</tbody></table>
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

    private fun polarityColor(v: Float): String = when {
        v >= 4.5f -> "#8B2020"
        v >= 3.5f -> "#c0392b"
        v >= 2.5f -> "#e55d3c"
        v >= 1.5f -> "#f0944a"
        v >= 0.5f -> "#f5c09a"
        v > -0.5f -> "#b0b8c8"
        v > -1.5f -> "#a8c8e8"
        v > -2.5f -> "#6aa3d0"
        v > -3.5f -> "#3a7fba"
        v > -4.5f -> "#1e5a9a"
        else -> "#0f3060"
    }

    private fun buildPolarityCalendarHeatmap(
        entries: List<MoodEntryEntity>,
        from: String,
        to: String,
    ): String {
        if (entries.isEmpty()) {
            return "<p style=\"color:var(--text-2)\">Нет данных.</p>"
        }
        val byDate = entries.associateBy { it.date }
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        // Align to Monday of first week
        val gridStart = start.minusDays(((start.dayOfWeek.value + 6) % 7).toLong())
        val gridEnd = end.plusDays((7 - end.dayOfWeek.value).toLong() % 7L)
        val days = mutableListOf<LocalDate>()
        var d = gridStart
        while (!d.isAfter(gridEnd)) {
            days += d
            d = d.plusDays(1)
        }
        val weeks = (days.size / 7).coerceAtLeast(1)
        val cells = days.joinToString("") { day ->
            val iso = day.toString()
            val e = byDate[iso]
            if (e == null || day.isBefore(start) || day.isAfter(end)) {
                """<div class="heatmap-day" style="background:#e8eaed;opacity:.3"></div>"""
            } else {
                val v = (e.elevated - e.depressed).toFloat()
                val tip = "${formatDateRu(iso)}: ${if (v >= 0) "+" else ""}${String.format(Locale("ru"), "%.0f", v)}"
                """<div class="heatmap-day" data-tip="${esc(tip)}" style="background:${polarityColor(v)}"></div>"""
            }
        }
        return """
        <div class="heatmap-wrap">
          <div class="heatmap" style="grid-auto-flow: column; grid-template-rows: repeat(7, 15px); grid-template-columns: repeat($weeks, 15px);">
            $cells
          </div>
        </div>
        <div class="hm-legend">
          <span>−5</span>
          <div class="hm-swatch" style="background:#0f3060"></div>
          <div class="hm-swatch" style="background:#3a7fba"></div>
          <div class="hm-swatch" style="background:#a8c8e8"></div>
          <div class="hm-swatch" style="background:#b0b8c8"></div>
          <div class="hm-swatch" style="background:#f5c09a"></div>
          <div class="hm-swatch" style="background:#c0392b"></div>
          <div class="hm-swatch" style="background:#8B2020"></div>
          <span>+5</span>
          <span style="margin-left:8px;color:#b0b8c8">□ нет данных</span>
        </div>
        """.trimIndent()
    }

    private fun buildObservedTriggersHtml(
        entries: List<MoodEntryEntity>,
        earlySignFreq: List<Pair<String, Int>>,
        missedSlots: Int,
    ): String {
        val items = mutableListOf<Triple<String, String, String>>() // color, name, date
        val sleepBad = entries.filter { (it.sleepHours ?: 8f) < 6f }
        if (sleepBad.isNotEmpty()) {
            val first = formatDateRu(sleepBad.first().date)
            val last = formatDateRu(sleepBad.last().date)
            items += Triple("var(--red)", "Сон &lt; 6 ч (${sleepBad.size} дн.)", "$first – $last")
        }
        if (missedSlots > 0) {
            items += Triple("var(--yellow)", "Пропуски приёма лекарств (слоты)", "$missedSlots")
        }
        earlySignFreq.take(4).forEach { (name, count) ->
            val color = when {
                count >= 5 -> "var(--red)"
                count >= 3 -> "var(--orange)"
                else -> "var(--yellow)"
            }
            items += Triple(color, "Ранний признак: ${esc(name)}", "$count отм.")
        }
        if (items.isEmpty()) return ""
        return items.joinToString("") { (color, name, date) ->
            """
            <div class="trigger-item">
              <div class="trig-dot" style="background:$color"></div>
              <div class="trig-name">$name</div>
              <div class="trig-date">${esc(date)}</div>
            </div>
            """.trimIndent()
        }
    }

    private fun observationalRisk(
        sleepBadDays: Int,
        earlySignCount: Int,
        avgPol: Double?,
        totalDays: Int,
    ): Triple<String, String, String> {
        val load = sleepBadDays + earlySignCount + when {
            avgPol == null -> 0
            kotlin.math.abs(avgPol) >= 2.0 -> 3
            kotlin.math.abs(avgPol) >= 1.0 -> 1
            else -> 0
        }
        return when {
            totalDays == 0 -> Triple(
                "low",
                "Наблюдение: нет записей",
                "За период нет дневниковых отметок. Заполните дни для мониторинга.",
            )
            load >= 10 -> Triple(
                "high",
                "Наблюдение: высокая нагрузка отметок",
                "Много дней с коротким сном и/или частые отметки ранних признаков. Это самоотчёт, не диагноз. Имеет смысл обсудить динамику с врачом.",
            )
            load >= 4 -> Triple(
                "mid",
                "Наблюдение: умеренная нагрузка отметок",
                "Есть дни с коротким сном и/или ранние признаки. Эпизод приложением не определяется — нужна клиническая оценка врача.",
            )
            else -> Triple(
                "low",
                "Наблюдение: низкая нагрузка отметок",
                "По дневнику меньше сигналов короткого сна и ранних признаков. Это не клиническое заключение.",
            )
        }
    }

    private fun observationalMoodNote(polarityVals: List<Float>, entries: List<MoodEntryEntity>): String {
        if (polarityVals.isEmpty()) return "Нет данных о настроении за период."
        val max = polarityVals.maxOrNull() ?: 0f
        val min = polarityVals.minOrNull() ?: 0f
        val maxIdx = polarityVals.indexOf(max).coerceAtLeast(0)
        val maxDate = entries.getOrNull(maxIdx)?.date?.let { formatDateRu(it) } ?: "—"
        return "Диапазон ${String.format(Locale("ru"), "%+.0f", min)}…${String.format(Locale("ru"), "%+.0f", max)}. " +
            "Максимум ${String.format(Locale("ru"), "%+.0f", max)} ($maxDate). Визуализация шкал дневника, не диагноз."
    }

    private fun observationalSleepNote(entries: List<MoodEntryEntity>, sleepBadDays: Int): String {
        val withSleep = entries.mapNotNull { it.sleepHours }
        if (withSleep.isEmpty()) return "Нет отметок часов сна."
        val avg = withSleep.average()
        return "Средний сон ${String.format(Locale("ru"), "%.1f", avg)} ч. Дней с сном <6 ч: $sleepBadDays. " +
            "Полоса 7–9 ч — ориентир гигиены сна, не норма от приложения."
    }

    private fun buildCrisisLevelsHtml(
        doctor: String,
        support: String,
        notes: String,
        wishes: String,
        avoid: String,
        contacts: List<com.moodlife.app.domain.CrisisContact>,
    ): String {
        fun bullets(vararg lines: String?) = lines
            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() && s != "—" } }
            .joinToString("") { "<li>${esc(it)}</li>" }
            .ifBlank { "<li>Заполните кризисный план в настройках Trace</li>" }

        val contactLines = contacts.take(3).map { "${it.label}: ${it.phone}" }
        val green = bullets(
            "Продолжать плановый приём лекарств по назначению врача",
            "Вести ежедневный трекер",
            "Соблюдать режим сна/бодрствования",
            notes.takeIf { it.isNotBlank() },
        )
        val yellow = bullets(
            "Связаться с лечащим врачом в ближайшее время",
            support.takeIf { it.isNotBlank() }?.let { "Опора: $it" },
            wishes.takeIf { it.isNotBlank() },
            "Усилить гигиену сна; попросить помощи близких",
        )
        val redContacts = buildList {
            if (doctor.isNotBlank() && doctor != "—") add("Врач: $doctor")
            addAll(contactLines)
            add("Кризисная линия: 8-800-2000-122")
            add("Экстренно: 112")
            avoid.takeIf { it.isNotBlank() }?.let { add("Избегать: $it") }
        }
        val red = bullets(*redContacts.toTypedArray())
        return """
        <div class="crisis-level green">
          <div class="cl-title"><div class="cl-dot"></div> Зелёный уровень</div>
          <div style="font-size:11px;color:var(--text-2);margin-bottom:8px">Настроение около −1…+1, сон 7–9 ч (ориентир)</div>
          <ul>$green</ul>
        </div>
        <div class="crisis-level yellow">
          <div class="cl-title"><div class="cl-dot"></div> Жёлтый уровень</div>
          <div style="font-size:11px;color:var(--text-2);margin-bottom:8px">Настроение ±2…±3 или сон &lt;6 ч ≥2 дней</div>
          <ul>$yellow</ul>
        </div>
        <div class="crisis-level red">
          <div class="cl-title"><div class="cl-dot"></div> Красный уровень</div>
          <div style="font-size:11px;color:var(--text-2);margin-bottom:8px">Сильный подъём/спад или мысли о самоповреждении</div>
          <ul>$red</ul>
        </div>
        """.trimIndent()
    }
}

