package com.moodlife.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.ExternalHealthDayDao
import com.moodlife.app.data.local.dao.FactorDao
import com.moodlife.app.data.local.dao.FactorLogDao
import com.moodlife.app.data.local.dao.FloLogDao
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
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.FactorLogEntity
import com.moodlife.app.data.local.entity.FloLogEntity
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
    ],
    version = 4,
    exportSchema = false,
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
    }
}
