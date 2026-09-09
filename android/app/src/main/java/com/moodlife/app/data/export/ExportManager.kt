package com.moodlife.app.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.moodlife.app.R
import com.moodlife.app.data.backup.JsonBackupExporter
import com.moodlife.app.data.local.entity.MoodEntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val mime: String, val ext: String, val labelRes: Int) {
    JSON("application/json", "json", R.string.export_format_json),
    HTML("text/html", "html", R.string.export_format_html),
    CSV("text/csv", "csv", R.string.export_format_csv),
    PDF("application/pdf", "pdf", R.string.export_format_pdf),
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
            ExportFormat.JSON -> exportFullBackup()
            ExportFormat.HTML -> {
                val html = doctorExportGenerator.generateHtml(year, month)
                writeCache("Trace-report-$label.html", html, ExportFormat.HTML)
            }
            ExportFormat.CSV -> {
                val entries = doctorExportGenerator.loadMonthEntries(year, month)
                writeCache("Trace-report-$label.csv", buildCsv(entries), ExportFormat.CSV)
            }
            ExportFormat.PDF -> {
                val entries = doctorExportGenerator.loadMonthEntries(year, month)
                val file = File(context.cacheDir, "Trace-report-$label.pdf")
                PdfReportRenderer(context).write(file, year, month, entries)
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
        val file = File(context.cacheDir, name)
        file.writeText(text)
        return ExportFile(file, format)
    }

    private fun buildCsv(entries: List<MoodEntryEntity>): String {
        val header = listOf(
            "date", "depressed", "elevated", "anxious", "irritable",
            "energy", "concentration", "appetite", "sociability",
            "sleep_hours", "sleep_quality", "sleep_time", "wake_time",
            "functioning", "safety", "alcohol", "substance", "routine",
            "episode", "note",
        ).joinToString(",")
        val rows = entries.joinToString("\n") { e ->
            listOf(
                e.date, e.depressed, e.elevated, e.anxious, e.irritable,
                e.energy, e.concentration, e.appetite, e.sociability,
                e.sleepHours ?: "", e.sleepQuality, e.sleepTime.orEmpty(), e.wakeTime.orEmpty(),
                e.functioning, e.safetyCheck, e.alcoholUse, e.substanceUse, e.routineScore,
                csvEscape(e.episodePhase.orEmpty()), csvEscape(e.note.orEmpty()),
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
