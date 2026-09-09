package com.moodlife.app.data.backup

import android.content.Context
import com.moodlife.app.data.local.MoodLifeDatabase
import com.moodlife.app.data.repository.SettingsRepository
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
        val db = database.openHelper.readableDatabase
        val tables = listOf(
            "mood_entries", "mood_check_ins", "day_notes", "medications", "medication_logs",
            "symptoms", "symptom_logs", "weather_days", "external_health_days", "settings",
            "period_logs", "period_settings",
        )
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "moodlife-android")
        root.put("version", "0.1.0")
        for (table in tables) {
            val arr = JSONArray()
            db.query("SELECT * FROM $table").use { cursor ->
                val cols = cursor.columnNames
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (col in cols) {
                        when (cursor.getType(cursor.getColumnIndexOrThrow(col))) {
                            android.database.Cursor.FIELD_TYPE_NULL -> row.put(col, JSONObject.NULL)
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(col, cursor.getLong(cursor.getColumnIndexOrThrow(col)))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(col, cursor.getDouble(cursor.getColumnIndexOrThrow(col)))
                            android.database.Cursor.FIELD_TYPE_STRING -> row.put(col, cursor.getString(cursor.getColumnIndexOrThrow(col)))
                            android.database.Cursor.FIELD_TYPE_BLOB -> row.put(col, "<blob>")
                        }
                    }
                    arr.put(row)
                }
            }
            root.put(table, arr)
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "moodlife-backup-$stamp.json")
        file.writeText(root.toString(2))
        settingsRepository.set(SettingsRepository.KEY_BACKUP_LAST, file.absolutePath)
        BackupFile(file, file.length())
    }

    data class BackupFile(val file: File, val bytes: Long)
}
