package com.moodlife.app.data.backup

import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.repository.DayNoteRepository
import com.moodlife.app.data.repository.FloRepository
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.domain.FloNativeExport
import com.moodlife.app.domain.ForeignImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One entry point for every import: picks the right reader per file (Trace backup, Flo, eMoods,
 * Daylio, Bearable, any CSV/JSON with dates). Foreign data only *adds*: a scale already marked
 * for a day is never overwritten and identical notes are not duplicated, so re-importing the
 * same file is harmless.
 */
@Singleton
class UniversalImporter @Inject constructor(
    private val backupImporter: JsonBackupImporter,
    private val floRepository: FloRepository,
    private val moodRepository: MoodRepository,
    private val dayNoteRepository: DayNoteRepository,
    private val dayNoteDao: DayNoteDao,
) {
    data class Summary(
        val formats: List<String>,
        val days: Int,
        val notes: Int,
        val error: String? = null,
        /** Dates found in the file and how many got sleep — so "nothing changed" is explainable. */
        val firstDate: String? = null,
        val lastDate: String? = null,
        val sleepFilled: Int = 0,
        val sleepKept: Int = 0,
    )

    suspend fun import(stream: InputStream, name: String): Summary = withContext(Dispatchers.IO) {
        val files = ImportFileReader.read(stream, name)
        if (files.isEmpty()) return@withContext Summary(emptyList(), 0, 0, "в архиве нет файлов JSON или CSV")
        val formats = mutableListOf<String>()
        var days = 0
        var notes = 0
        val dates = mutableListOf<String>()
        var sleepFilled = 0
        var sleepKept = 0
        for (file in files) {
            val text = file.text
            if (text.isBlank()) continue
            when {
                isTraceBackup(text) -> {
                    val r = backupImporter.importText(text)
                    if (!r.success) return@withContext Summary(formats, days, notes, r.message)
                    formats += "резервная копия Trace"
                    days += r.rows
                }
                isFlo(text) -> {
                    val r = floRepository.importJson(text)
                    if (r.detected > 0) {
                        formats += "Flo"
                        days += r.imported
                    }
                }
                else -> {
                    val parsed = ForeignImport.parse(file.name, text) ?: continue
                    dates += parsed.days.map { it.date }
                    val (d, n) = merge(parsed.days) { filled -> if (filled) sleepFilled++ else sleepKept++ }
                    formats += parsed.format
                    days += d
                    notes += n
                }
            }
        }
        if (formats.isEmpty()) {
            Summary(emptyList(), 0, 0, "не найдено дат: формат файла не распознан")
        } else {
            Summary(formats.distinct(), days, notes, firstDate = dates.minOrNull(), lastDate = dates.maxOrNull(), sleepFilled = sleepFilled, sleepKept = sleepKept)
        }
    }

    private suspend fun merge(
        incoming: List<ForeignImport.Day>,
        onSleep: (filled: Boolean) -> Unit = {},
    ): Pair<Int, Int> {
        var daysChanged = 0
        var notesAdded = 0
        incoming.groupBy { it.date }.forEach { (date, list) ->
            val values = list.flatMap { it.values.entries }.associate { it.key to it.value }
            val sleep = list.firstNotNullOfOrNull { it.sleepHours }
            val existing = moodRepository.getEntry(date)
            var fields = existing?.let(MoodRepository.MoodFields::fromEntity) ?: MoodRepository.MoodFields()
            var changed = false
            values.forEach { (key, v) ->
                if (key !in fields.marked) {
                    fields = fields.withAxis(key, v)
                    changed = true
                }
            }
            if (sleep != null) {
                if (fields.sleepHours == null) {
                    fields = fields.copy(sleepHours = sleep)
                    changed = true
                    onSleep(true)
                } else {
                    // Already entered for that day — kept, but reported.
                    onSleep(false)
                }
            }
            val bed = list.firstNotNullOfOrNull { it.bedTime }
            val wake = list.firstNotNullOfOrNull { it.wakeTime }
            if (bed != null && fields.sleepTime.isNullOrBlank()) {
                fields = fields.copy(sleepTime = bed)
                changed = true
            }
            if (wake != null && fields.wakeTime.isNullOrBlank()) {
                fields = fields.copy(wakeTime = wake)
                changed = true
            }
            if (changed) {
                moodRepository.saveToday(date, fields)
                daysChanged++
            }
            val have = dayNoteDao.listRange(date, date).map { it.content }.toSet()
            list.flatMap { it.notes }.distinct().filter { it !in have }.forEach {
                dayNoteRepository.addNote(date, it)
                notesAdded++
            }
        }
        return daysChanged to notesAdded
    }

    private fun isTraceBackup(text: String): Boolean =
        text.startsWith("{") && (text.contains("\"mood_entries\"") || text.contains("\"app\":\"trace-android\""))

    private fun isFlo(text: String): Boolean {
        if (!text.startsWith("{") && !text.startsWith("[")) return false
        val root = runCatching { com.google.gson.JsonParser.parseString(text) }.getOrNull() ?: return false
        if (root.isJsonObject && FloNativeExport.matches(root.asJsonObject)) return true
        // Older/simplified Flo exports used by the previous importer.
        return text.contains("\"flow\"") || text.contains("\"periods\"") || text.contains("\"period_entries\"")
    }
}
