package com.moodlife.app.data.backup

import android.content.Context
import com.moodlife.app.BuildConfig
import com.moodlife.app.data.local.MoodLifeDatabase
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.ExportCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonBackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MoodLifeDatabase,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun exportToCache(): BackupFile = withContext(Dispatchers.IO) {
        pruneOldCacheFiles()
        val root = dumpTables(dateFrom = null, dateTo = null)
        writeRoot(root, "Trace-backup")
    }

    /**
     * Month-scoped JSON for Reports export — never dumps the whole database.
     * Includes catalog tables needed to interpret logs (meds/symptoms/factors/signs).
     */
    suspend fun exportMonthToCache(year: Int, month: Int): BackupFile = withContext(Dispatchers.IO) {
        pruneOldCacheFiles()
        val (from, to) = DateUtils.monthRange(year, month)
        val root = dumpTables(dateFrom = from, dateTo = to)
        root.put("scope", "month")
        root.put("from", from)
        root.put("to", to)
        val label = "${year}-${(month + 1).toString().padStart(2, '0')}"
        writeRoot(root, "Trace-report-$label")
    }

    private fun dumpTables(dateFrom: String?, dateTo: String?): JSONObject {
        val db = database.openHelper.readableDatabase
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "trace-android")
        root.put("version", BuildConfig.VERSION_NAME)
        val monthScoped = dateFrom != null && dateTo != null
        val entryIds = if (monthScoped) {
            queryIds(db, "SELECT id FROM mood_entries WHERE date >= ? AND date <= ?", dateFrom!!, dateTo!!)
        } else {
            emptySet()
        }
        for (table in BackupSchema.TABLES) {
            if (monthScoped && table == "settings") {
                // Month report must not attach crisis contacts / prefs dump.
                root.put(table, JSONArray())
                continue
            }
            val arr = JSONArray()
            val sql = when {
                !monthScoped -> "SELECT * FROM $table"
                table in DATE_FILTERED_TABLES ->
                    "SELECT * FROM $table WHERE date >= ? AND date <= ?"
                table in ENTRY_FILTERED_TABLES -> {
                    if (entryIds.isEmpty()) {
                        root.put(table, arr)
                        continue
                    }
                    val placeholders = entryIds.joinToString(",") { "?" }
                    "SELECT * FROM $table WHERE moodEntryId IN ($placeholders)"
                }
                table in CATALOG_ALWAYS -> "SELECT * FROM $table"
                table == "period_settings" -> "SELECT * FROM $table"
                else -> "SELECT * FROM $table"
            }
            val args: Array<out Any?> = when {
                !monthScoped -> emptyArray()
                table in DATE_FILTERED_TABLES -> arrayOf(dateFrom!!, dateTo!!)
                table in ENTRY_FILTERED_TABLES -> entryIds.toTypedArray()
                else -> emptyArray()
            }
            db.query(sql, args).use { cursor ->
                val cols = cursor.columnNames
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (col in cols) {
                        when (cursor.getType(cursor.getColumnIndexOrThrow(col))) {
                            android.database.Cursor.FIELD_TYPE_NULL -> row.put(col, JSONObject.NULL)
                            android.database.Cursor.FIELD_TYPE_INTEGER ->
                                row.put(col, cursor.getLong(cursor.getColumnIndexOrThrow(col)))
                            android.database.Cursor.FIELD_TYPE_FLOAT ->
                                row.put(col, cursor.getDouble(cursor.getColumnIndexOrThrow(col)))
                            android.database.Cursor.FIELD_TYPE_STRING ->
                                row.put(col, cursor.getString(cursor.getColumnIndexOrThrow(col)))
                            android.database.Cursor.FIELD_TYPE_BLOB -> row.put(col, "<blob>")
                        }
                    }
                    if (table == "settings") {
                        val key = row.optString("key")
                        if (key in BackupSchema.REDACTED_SETTING_KEYS) {
                            row.put("value", "")
                        }
                    }
                    arr.put(row)
                }
            }
            root.put(table, arr)
        }
        return root
    }

    private fun queryIds(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
        vararg args: String,
    ): Set<String> {
        val out = linkedSetOf<String>()
        db.query(sql, args).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    private suspend fun writeRoot(root: JSONObject, namePrefix: String): BackupFile {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = ExportCache.dir(context)
        val file = File(dir, "$namePrefix-$stamp.json")
        file.writeText(root.toString(2))
        // Do not persist absolute cache path into Room (leaks device layout; not user-facing).
        settingsRepository.set(SettingsRepository.KEY_BACKUP_LAST, namePrefix)
        return BackupFile(file, file.length())
    }

    private fun pruneOldCacheFiles() {
        val dir = ExportCache.dir(context)
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("Trace-") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    data class BackupFile(val file: File, val bytes: Long)

    companion object {
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

        private val DATE_FILTERED_TABLES = setOf(
            "mood_entries",
            "mood_check_ins",
            "day_notes",
            "medication_logs",
            "weather_days",
            "external_health_days",
            "flo_logs",
        )
        private val ENTRY_FILTERED_TABLES = setOf(
            "symptom_logs",
            "factor_logs",
            "warning_triggers",
            "period_logs",
        )
        private val CATALOG_ALWAYS = setOf(
            "medications",
            "symptoms",
            "factors",
            "early_warning_signs",
        )
    }
}
