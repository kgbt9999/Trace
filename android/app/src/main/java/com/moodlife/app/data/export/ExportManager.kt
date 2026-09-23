package com.moodlife.app.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.moodlife.app.R
import com.moodlife.app.data.backup.JsonBackupExporter
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.util.ExportCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val mime: String, val ext: String, val labelRes: Int) {
    JSON("application/json", "json", R.string.export_format_json),
    HTML("text/html", "html", R.string.export_format_html),
    CSV("text/csv", "csv", R.string.export_format_csv),
    PDF("application/pdf", "pdf", R.string.export_format_pdf),
    PATIENT_HTML("text/html", "html", R.string.export_format_patient_html),
}

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonBackupExporter: JsonBackupExporter,
    private val doctorExportGenerator: DoctorExportGenerator,
) {
    suspend fun exportFullBackup(): ExportFile {
        val exported = jsonBackupExporter.exportToCache()
        return ExportFile(exported.file, ExportFormat.JSON)
    }

    suspend fun exportMonth(year: Int, month: Int, format: ExportFormat): ExportFile {
        val label = "${year}-${(month + 1).toString().padStart(2, '0')}"
        return when (format) {
            // Month JSON must stay month-scoped — never dump the full diary DB.
            ExportFormat.JSON -> {
                val exported = jsonBackupExporter.exportMonthToCache(year, month)
                ExportFile(exported.file, ExportFormat.JSON)
            }
            ExportFormat.HTML -> {
                val html = doctorExportGenerator.generateHtml(year, month)
                writeCache("Trace-doctor-$label.html", html, ExportFormat.HTML)
            }
            ExportFormat.PATIENT_HTML -> {
                val html = doctorExportGenerator.generatePatientHtml(year, month)
                writeCache("Trace-patient-$label.html", html, ExportFormat.PATIENT_HTML)
            }
            ExportFormat.CSV -> {
                val entries = doctorExportGenerator.loadMonthEntries(year, month)
                writeCache("Trace-report-$label.csv", buildCsv(entries), ExportFormat.CSV)
            }
            ExportFormat.PDF -> {
                val entries = doctorExportGenerator.loadMonthEntries(year, month)
                val medsByDay = doctorExportGenerator.loadMedsByDay(year, month)
                val medDoseSeries = doctorExportGenerator.loadMedDoseSeries(year, month)
                val labResults = doctorExportGenerator.loadLabResults(year, month)
                val earlySigns = doctorExportGenerator.loadEarlySignFrequency(year, month)
                val notes = doctorExportGenerator.loadNoteExcerpts(year, month)
                val medAdherence = doctorExportGenerator.loadMedAdherence(year, month)
                val caseHistory = doctorExportGenerator.loadCaseHistory(year, month)
                val crisis = doctorExportGenerator.loadCrisisPlan()
                val file = File(ExportCache.dir(context), "Trace-doctor-$label.pdf")
                PdfReportRenderer(context).write(
                    file = file,
                    year = year,
                    month = month,
                    entries = entries,
                    medsByDay = medsByDay,
                    medDoseSeries = medDoseSeries,
                    labResults = labResults,
                    earlySignFrequency = earlySigns,
                    noteExcerpts = notes,
                    medAdherence = medAdherence,
                    caseHistoryRows = caseHistory,
                    crisisDoctor = crisis.doctor,
                    crisisSupport = crisis.support,
                    crisisNotes = crisis.notes,
                    crisisWishes = crisis.wishes,
                    crisisAvoid = crisis.avoid,
                    crisisContacts = crisis.contacts,
                )
                ExportFile(file, ExportFormat.PDF)
            }
        }
    }

    fun shareIntent(file: File, format: ExportFormat): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val subject = context.getString(R.string.export_share_subject)
        return Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, subject)
            putExtra(Intent.EXTRA_TEXT, subject)
            clipData = android.content.ClipData.newUri(context.contentResolver, subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeCache(name: String, text: String, format: ExportFormat): ExportFile {
        val file = File(ExportCache.dir(context), name)
        file.writeText(text)
        return ExportFile(file, format)
    }

    private fun buildCsv(entries: List<MoodEntryEntity>): String {
        // Clinician-safe default: no alcohol/substance columns; no episode-phase diagnostic key.
        val header = listOf(
            "date", "depressed", "elevated", "anxious", "irritable",
            "energy", "concentration", "appetite", "sociability",
            "sleep_hours", "sleep_quality", "sleep_time", "wake_time",
            "functioning", "safety", "routine", "note",
        ).joinToString(",")
        val rows = entries.joinToString("\n") { e ->
            listOf(
                e.date, e.depressed, e.elevated, e.anxious, e.irritable,
                e.energy, e.concentration, e.appetite, e.sociability,
                e.sleepHours ?: "", e.sleepQuality, e.sleepTime.orEmpty(), e.wakeTime.orEmpty(),
                e.functioning, e.safetyCheck, e.routineScore,
                csvEscape(e.note.orEmpty()),
            ).joinToString(",")
        }
        return "$header\n$rows"
    }

    private fun csvEscape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    data class ExportFile(val file: File, val format: ExportFormat)
}
