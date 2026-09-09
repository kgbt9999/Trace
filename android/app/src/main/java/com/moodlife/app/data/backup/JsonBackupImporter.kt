package com.moodlife.app.data.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodlife.app.data.local.MoodLifeDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonBackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MoodLifeDatabase,
) {
    private val tables = listOf(
        "mood_entries", "mood_check_ins", "day_notes", "medications", "medication_logs",
        "symptoms", "symptom_logs", "weather_days", "external_health_days", "settings",
        "period_logs", "period_settings", "factors", "factor_logs", "early_warning_signs",
        "warning_triggers", "flo_logs",
    )

    suspend fun importFromStream(stream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val text = stream.bufferedReader().readText().trim().removePrefix("\uFEFF")
        if (text.isEmpty()) return@withContext ImportResult(false, "Файл пустой")
        if (text.startsWith("{") || text.startsWith("[")) {
            importJson(text)
        } else {
            importCsv(text)
        }
    }

    private fun importJson(text: String): ImportResult {
        val root = JSONObject(text)
        val appId = root.optString("app")
        val knownApp = appId == "trace-android" || appId == "moodlife-android"
        if (!knownApp && !root.has("mood_entries")) {
            return ImportResult(false, "Неверный формат JSON")
        }
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        return try {
            var rows = 0
            for (table in tables) {
                val arr = root.optJSONArray(table) ?: continue
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    insertRow(db, table, row)
                    rows++
                }
            }
            db.setTransactionSuccessful()
            ImportResult(true, "Импортировано строк: $rows")
        } catch (e: Exception) {
            ImportResult(false, e.message ?: "Ошибка импорта")
        } finally {
            db.endTransaction()
        }
    }

    private fun importCsv(text: String): ImportResult {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return ImportResult(false, "В CSV нет строк данных")
        val delim = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val header = parseCsvLine(lines[0], delim).map { it.trim().lowercase().replace(' ', '_') }
        val dateIdx = header.indexOfFirst { it == "date" || it == "дата" }
        if (dateIdx < 0) return ImportResult(false, "В CSV нет колонки date")
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        return try {
            var rows = 0
            val now = System.currentTimeMillis()
            for (line in lines.drop(1)) {
                val cols = parseCsvLine(line, delim)
                val date = cols.getOrNull(dateIdx)?.trim().orEmpty()
                if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) continue
                fun intAt(vararg names: String): Int {
                    val i = idx(*names)
                    return cols.getOrNull(i)?.toIntOrNull() ?: 0
                }
                fun strAt(vararg names: String): String? {
                    val i = idx(*names)
                    return cols.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
                }
                val existingId = queryIdByDate(db, date)
                val id = existingId ?: java.util.UUID.randomUUID().toString()
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO mood_entries (
                        id, date, depressed, elevated, anxious, irritable,
                        energy, concentration, appetite, sociability,
                        sleepHours, sleepQuality, sleepTime, wakeTime,
                        functioning, safetyCheck, alcoholUse, substanceUse,
                        episodePhase, routineScore, note, createdAt, updatedAt
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent(),
                    arrayOf(
                        id, date,
                        intAt("depressed", "д"),
                        intAt("elevated", "п"),
                        intAt("anxious", "т"),
                        intAt("irritable", "р"),
                        intAt("energy"),
                        intAt("concentration"),
                        intAt("appetite"),
                        intAt("sociability"),
                        strAt("sleep_hours", "sleephours")?.toFloatOrNull(),
                        intAt("sleep_quality", "sleepquality"),
                        strAt("sleep_time", "sleeptime"),
                        strAt("wake_time", "waketime"),
                        intAt("functioning"),
                        intAt("safety", "safetycheck"),
                        intAt("alcohol", "alcoholuse"),
                        intAt("substance", "substanceuse"),
                        strAt("episode", "episodephase") ?: "euthymic",
                        intAt("routine", "routinescore"),
                        strAt("note"),
                        now,
                        now,
                    ),
                )
                rows++
            }
            db.setTransactionSuccessful()
            if (rows == 0) ImportResult(false, "Не удалось прочитать строки CSV")
            else ImportResult(true, "Импортировано дней: $rows")
        } catch (e: Exception) {
            ImportResult(false, e.message ?: "Ошибка CSV")
        } finally {
            db.endTransaction()
        }
    }

    private fun queryIdByDate(db: SupportSQLiteDatabase, date: String): String? {
        val cur = db.query("SELECT id FROM mood_entries WHERE date = ? LIMIT 1", arrayOf(date))
        cur.use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun parseCsvLine(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        buf.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delim && !inQuotes -> {
                    out.add(buf.toString())
                    buf.clear()
                }
                else -> buf.append(ch)
            }
            i++
        }
        out.add(buf.toString())
        return out
    }

    suspend fun clearAllUserData(): ImportResult = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            tables.forEach { db.execSQL("DELETE FROM $it") }
            db.setTransactionSuccessful()
            ImportResult(true, "Данные удалены")
        } catch (e: Exception) {
            ImportResult(false, e.message ?: "Ошибка")
        } finally {
            db.endTransaction()
        }
    }

    private fun insertRow(db: SupportSQLiteDatabase, table: String, row: JSONObject) {
        val cols = row.keys().asSequence().toList()
        if (cols.isEmpty()) return
        val placeholders = cols.joinToString(",") { "?" }
        val sql = "INSERT OR REPLACE INTO $table (${cols.joinToString(",")}) VALUES ($placeholders)"
        val args = cols.map { col ->
            when (val v = row.opt(col)) {
                JSONObject.NULL, null -> null
                is Number -> v
                else -> v.toString()
            }
        }.toTypedArray()
        db.execSQL(sql, args)
    }

    data class ImportResult(val success: Boolean, val message: String)
}
