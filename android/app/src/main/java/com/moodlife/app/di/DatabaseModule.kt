package com.moodlife.app.di

import android.content.Context
import androidx.room.Room
import com.moodlife.app.data.local.MoodLifeDatabase
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_1_2
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_2_3
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_3_4
import com.moodlife.app.data.local.MoodLifeDatabase.Companion.MIGRATION_4_5
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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseStore: DatabasePassphraseStore,
    ): MoodLifeDatabase {
        SQLiteDatabase.loadLibs(context)
        val passphrase = passphraseStore.getOrCreatePassphrase()
        DatabaseEncryptionMigrator.migrateIfNeeded(context, passphrase)
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase), null, false)
        return Room.databaseBuilder(context, MoodLifeDatabase::class.java, DatabaseEncryptionMigrator.DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
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
}
