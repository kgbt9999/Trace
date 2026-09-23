package com.moodlife.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodlife.app.data.local.dao.BodyMeasurementDao
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.ExternalHealthDayDao
import com.moodlife.app.data.local.dao.FactorDao
import com.moodlife.app.data.local.dao.FactorLogDao
import com.moodlife.app.data.local.dao.FloLogDao
import com.moodlife.app.data.local.dao.LabResultDao
import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodCheckInDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.dao.PeriodDao
import com.moodlife.app.data.local.dao.SettingDao
import com.moodlife.app.data.local.dao.SymptomDao
import com.moodlife.app.data.local.dao.SymptomLogDao
import com.moodlife.app.data.local.dao.WarningSignDao
import com.moodlife.app.data.local.dao.WarningTriggerDao
import com.moodlife.app.data.local.dao.WeatherDayDao
import com.moodlife.app.data.local.entity.BodyMeasurementEntity
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.FactorLogEntity
import com.moodlife.app.data.local.entity.FloLogEntity
import com.moodlife.app.data.local.entity.LabResultEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.data.local.entity.MoodCheckInEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodLogEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.data.local.entity.SettingEntity
import com.moodlife.app.data.local.entity.SymptomEntity
import com.moodlife.app.data.local.entity.SymptomLogEntity
import com.moodlife.app.data.local.entity.WarningTriggerEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity

@Database(
    entities = [
        MoodEntryEntity::class,
        MoodCheckInEntity::class,
        DayNoteEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        SymptomEntity::class,
        SymptomLogEntity::class,
        FactorEntity::class,
        FactorLogEntity::class,
        EarlyWarningSignEntity::class,
        WarningTriggerEntity::class,
        PeriodLogEntity::class,
        PeriodSettingEntity::class,
        FloLogEntity::class,
        WeatherDayEntity::class,
        ExternalHealthDayEntity::class,
        SettingEntity::class,
        BodyMeasurementEntity::class,
        LabResultEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class MoodLifeDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun symptomDao(): SymptomDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun periodDao(): PeriodDao
    abstract fun settingDao(): SettingDao
    abstract fun moodCheckInDao(): MoodCheckInDao
    abstract fun weatherDayDao(): WeatherDayDao
    abstract fun externalHealthDayDao(): ExternalHealthDayDao
    abstract fun factorDao(): FactorDao
    abstract fun factorLogDao(): FactorLogDao
    abstract fun warningSignDao(): WarningSignDao
    abstract fun warningTriggerDao(): WarningTriggerDao
    abstract fun floLogDao(): FloLogDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun labResultDao(): LabResultDao

    companion object {
        /**
         * v1 → v2: add nullable [MoodCheckInEntity.valuesJson] only.
         * Preserves all existing tables and rows (no drop/recreate).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE mood_check_ins ADD COLUMN valuesJson TEXT DEFAULT NULL",
                )
            }
        }

        /**
         * v2 → v3: add non-null [FactorEntity.scaleType] with default matching entity.
         * Preserves all existing factor rows (no drop/recreate).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE factors ADD COLUMN scaleType TEXT NOT NULL DEFAULT '0-5'",
                )
            }
        }

        /**
         * v3 → v4: per-day medication dosage override on logs.
         * Catalog [MedicationEntity.dosage] stays the default; day edit writes override only.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medication_logs ADD COLUMN dosageOverride TEXT DEFAULT NULL",
                )
            }
        }

        /**
         * v4 → v5: freeze name + intake times on each day log so catalog scheme edits
         * do not rewrite historical dosage / slots / labels. Backfill from catalog.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medication_logs ADD COLUMN nameSnapshot TEXT DEFAULT NULL",
                )
                db.execSQL(
                    "ALTER TABLE medication_logs ADD COLUMN intakeTimesSnapshot TEXT DEFAULT NULL",
                )
                db.execSQL(
                    """
                    UPDATE medication_logs
                    SET dosageOverride = (
                        SELECT dosage FROM medications WHERE medications.id = medication_logs.medicationId
                    )
                    WHERE dosageOverride IS NULL
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE medication_logs
                    SET nameSnapshot = (
                        SELECT name FROM medications WHERE medications.id = medication_logs.medicationId
                    )
                    WHERE nameSnapshot IS NULL
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE medication_logs
                    SET intakeTimesSnapshot = (
                        SELECT intakeTimes FROM medications WHERE medications.id = medication_logs.medicationId
                    )
                    WHERE intakeTimesSnapshot IS NULL
                    """.trimIndent(),
                )
            }
        }

        /**
         * v5 → v6: optional body measurements + lab results (one row per date).
         * Additive only — no existing tables changed.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_measurements (
                        id TEXT NOT NULL,
                        date TEXT NOT NULL,
                        shoulderWidthCm REAL,
                        bicepsCm REAL,
                        chestCm REAL,
                        underBustCm REAL,
                        waistCm REAL,
                        hipsCm REAL,
                        thighCm REAL,
                        weightKg REAL,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_body_measurements_date ON body_measurements(date)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lab_results (
                        id TEXT NOT NULL,
                        date TEXT NOT NULL,
                        tsh REAL,
                        creatinine REAL,
                        calcium REAL,
                        lithium REAL,
                        ferritin REAL,
                        vitaminD25Oh REAL,
                        freeT4 REAL,
                        vitaminB12 REAL,
                        urineAnalysisNote TEXT,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_lab_results_date ON lab_results(date)",
                )
            }
        }

        /**
         * v6 → v7: lab_results become flexible entries
         * (name, value, unit, date, clinic) — many rows per date.
         * Migrates old wide columns into individual rows.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lab_results_v7 (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        valueText TEXT NOT NULL,
                        valueNumeric REAL,
                        unit TEXT,
                        date TEXT NOT NULL,
                        clinic TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_results_v7_date ON lab_results_v7(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_results_v7_name ON lab_results_v7(name)")

                val cursor = db.query("SELECT * FROM lab_results")
                fun col(name: String): Int = cursor.getColumnIndex(name)
                while (cursor.moveToNext()) {
                    val idBase = cursor.getString(col("id")) ?: continue
                    val date = cursor.getString(col("date")) ?: continue
                    val createdAt = if (col("createdAt") >= 0 && !cursor.isNull(col("createdAt"))) {
                        cursor.getLong(col("createdAt"))
                    } else {
                        System.currentTimeMillis()
                    }
                    val updatedAt = if (col("updatedAt") >= 0 && !cursor.isNull(col("updatedAt"))) {
                        cursor.getLong(col("updatedAt"))
                    } else {
                        createdAt
                    }
                    fun insert(suffix: String, name: String, value: Double?, unit: String) {
                        if (value == null) return
                        val vText = if (value == value.toLong().toDouble()) {
                            value.toLong().toString()
                        } else {
                            value.toString()
                        }
                        db.execSQL(
                            """
                            INSERT INTO lab_results_v7
                            (id, name, valueText, valueNumeric, unit, date, clinic, createdAt, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)
                            """.trimIndent(),
                            arrayOf(
                                "$idBase-$suffix",
                                name,
                                vText,
                                value,
                                unit,
                                date,
                                createdAt,
                                updatedAt,
                            ),
                        )
                    }
                    fun realOrNull(c: String): Double? {
                        val i = col(c)
                        return if (i >= 0 && !cursor.isNull(i)) cursor.getDouble(i) else null
                    }
                    insert("tsh", "ТТГ", realOrNull("tsh"), "мЕд/л")
                    insert("creatinine", "Креатинин", realOrNull("creatinine"), "мкмоль/л")
                    insert("calcium", "Кальций в крови", realOrNull("calcium"), "ммоль/л")
                    insert("lithium", "Литий в крови", realOrNull("lithium"), "ммоль/л")
                    insert("ferritin", "Ферритин", realOrNull("ferritin"), "нг/мл")
                    insert("vitd", "25-ОН витамин D", realOrNull("vitaminD25Oh"), "нг/мл")
                    insert("freet4", "Т4 свободный", realOrNull("freeT4"), "пмоль/л")
                    insert("b12", "Витамин B12", realOrNull("vitaminB12"), "пг/мл")
                    val urineIdx = col("urineAnalysisNote")
                    if (urineIdx >= 0 && !cursor.isNull(urineIdx)) {
                        val urine = cursor.getString(urineIdx)?.trim().orEmpty()
                        if (urine.isNotEmpty()) {
                            db.execSQL(
                                """
                                INSERT INTO lab_results_v7
                                (id, name, valueText, valueNumeric, unit, date, clinic, createdAt, updatedAt)
                                VALUES (?, ?, ?, NULL, '', ?, NULL, ?, ?)
                                """.trimIndent(),
                                arrayOf(
                                    "$idBase-oam",
                                    "Общий анализ мочи",
                                    urine,
                                    date,
                                    createdAt,
                                    updatedAt,
                                ),
                            )
                        }
                    }
                    val noteIdx = col("note")
                    if (noteIdx >= 0 && !cursor.isNull(noteIdx)) {
                        val note = cursor.getString(noteIdx)?.trim().orEmpty()
                        if (note.isNotEmpty()) {
                            db.execSQL(
                                """
                                INSERT INTO lab_results_v7
                                (id, name, valueText, valueNumeric, unit, date, clinic, createdAt, updatedAt)
                                VALUES (?, ?, ?, NULL, '', ?, NULL, ?, ?)
                                """.trimIndent(),
                                arrayOf(
                                    "$idBase-note",
                                    "Заметка к анализам",
                                    note,
                                    date,
                                    createdAt,
                                    updatedAt,
                                ),
                            )
                        }
                    }
                }
                cursor.close()
                db.execSQL("DROP TABLE lab_results")
                db.execSQL("ALTER TABLE lab_results_v7 RENAME TO lab_results")
            }
        }
    }
}
