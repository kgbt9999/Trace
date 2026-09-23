package com.moodlife.app.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.moodlife.app.data.local.MoodLifeDatabase
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_1_2
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_2_3
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_3_4
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_4_5
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_5_6
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_6_7
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
import com.moodlife.app.data.secure.DatabaseEncryptionMigrator
import com.moodlife.app.data.secure.DatabasePassphraseStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "DatabaseModule"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseStore: DatabasePassphraseStore,
    ): MoodLifeDatabase {
        val loaded = try {
            SQLiteDatabase.loadLibs(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SQLCipher native libs failed to load; using plaintext", e)
            false
        }
        if (!loaded) {
            return openPlaintext(context)
        }

        val passphrase = try {
            passphraseStore.getOrCreatePassphrase()
        } catch (e: Exception) {
            Log.e(TAG, "Passphrase store failed; opening plaintext Room DB", e)
            return openPlaintext(context)
        }

        val useEncryption = DatabaseEncryptionMigrator.migrateIfNeeded(context, passphrase)
        val plaintext = DatabaseEncryptionMigrator.isPlaintextDatabase(context)

        if (!useEncryption || plaintext) {
            Log.w(TAG, "Opening Room database without SQLCipher (plaintext fallback)")
            return openPlaintext(context)
        }

        return try {
            openEncrypted(context, passphrase)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted DB open failed", e)
            if (DatabaseEncryptionMigrator.restorePlaintextBackup(context)) {
                Log.w(TAG, "Restored plaintext backup after encrypted open failure")
                openPlaintext(context)
            } else {
                // Last resort: try plaintext only if the file is readable as SQLite.
                if (DatabaseEncryptionMigrator.isPlaintextDatabase(context)) {
                    openPlaintext(context)
                } else {
                    throw e
                }
            }
        }
    }

    private fun openEncrypted(context: Context, passphrase: CharArray): MoodLifeDatabase {
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase), null, false)
        val db = baseBuilder(context)
            .openHelperFactory(factory)
            .build()
        // Force openHelper init now so failures fall into provideDatabase catch,
        // not a later random first-query crash on the main thread.
        db.openHelper.writableDatabase
        return db
    }

    private fun openPlaintext(context: Context): MoodLifeDatabase {
        val db = baseBuilder(context).build()
        db.openHelper.writableDatabase
        return db
    }

    private fun baseBuilder(context: Context): RoomDatabase.Builder<MoodLifeDatabase> {
        return Room.databaseBuilder(
            context,
            MoodLifeDatabase::class.java,
            DatabaseEncryptionMigrator.DB_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
    }

    @Provides fun provideMoodEntryDao(db: MoodLifeDatabase): MoodEntryDao = db.moodEntryDao()
    @Provides fun provideMedicationDao(db: MoodLifeDatabase): MedicationDao = db.medicationDao()
    @Provides fun provideMedicationLogDao(db: MoodLifeDatabase): MedicationLogDao = db.medicationLogDao()
    @Provides fun provideSymptomDao(db: MoodLifeDatabase): SymptomDao = db.symptomDao()
    @Provides fun provideSymptomLogDao(db: MoodLifeDatabase): SymptomLogDao = db.symptomLogDao()
    @Provides fun provideDayNoteDao(db: MoodLifeDatabase): DayNoteDao = db.dayNoteDao()
    @Provides fun providePeriodDao(db: MoodLifeDatabase): PeriodDao = db.periodDao()
    @Provides fun provideSettingDao(db: MoodLifeDatabase): SettingDao = db.settingDao()
    @Provides fun provideMoodCheckInDao(db: MoodLifeDatabase): MoodCheckInDao = db.moodCheckInDao()
    @Provides fun provideWeatherDayDao(db: MoodLifeDatabase): WeatherDayDao = db.weatherDayDao()
    @Provides fun provideExternalHealthDayDao(db: MoodLifeDatabase): ExternalHealthDayDao = db.externalHealthDayDao()
    @Provides fun provideFactorDao(db: MoodLifeDatabase): FactorDao = db.factorDao()
    @Provides fun provideFactorLogDao(db: MoodLifeDatabase): FactorLogDao = db.factorLogDao()
    @Provides fun provideWarningSignDao(db: MoodLifeDatabase): WarningSignDao = db.warningSignDao()
    @Provides fun provideWarningTriggerDao(db: MoodLifeDatabase): WarningTriggerDao = db.warningTriggerDao()
    @Provides fun provideFloLogDao(db: MoodLifeDatabase): FloLogDao = db.floLogDao()
    @Provides fun provideBodyMeasurementDao(db: MoodLifeDatabase): BodyMeasurementDao = db.bodyMeasurementDao()
    @Provides fun provideLabResultDao(db: MoodLifeDatabase): LabResultDao = db.labResultDao()
}
