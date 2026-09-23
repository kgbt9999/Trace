package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.SettingDao
import com.moodlife.app.data.local.entity.SettingEntity
import com.moodlife.app.domain.CalendarDayIcons
import com.moodlife.app.domain.CrisisContacts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val settingDao: SettingDao) {

    fun observe(key: String): Flow<String?> =
        settingDao.observe(key).map { it?.value }

    fun observeAllMap(): Flow<Map<String, String>> =
        settingDao.observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun get(key: String): String? = settingDao.get(key)?.value

    suspend fun set(key: String, value: String) {
        val existing = settingDao.get(key)
        val now = System.currentTimeMillis()
        settingDao.upsert(
            SettingEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                key = key,
                value = value,
                updatedAt = now,
            ),
        )
    }

    companion object {
        const val KEY_APPEARANCE = "appearance_style"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_CRISIS_DOCTOR = "crisis_doctor"
        const val KEY_CRISIS_SUPPORT = "crisis_support"
        const val KEY_CRISIS_NOTES = "crisis_notes"
        const val KEY_CRISIS_WISHES = "crisis_wishes"
        const val KEY_CRISIS_AVOID = "crisis_avoid"
        const val KEY_ONBOARDING_DISMISSED = "onboarding_dismissed"
        /** First-run wizard finished — "1" when done (preferred over dismissed card). */
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        /** basic | advanced — Today surface density. */
        const val KEY_UI_MODE = "ui_mode"
        /** Comma-separated LaunchTips ids the user dismissed. */
        const val KEY_SEEN_TIPS = "seen_tip_ids"
        /** Soft daily nudge until meds added or dismissed — "1" pending. */
        const val KEY_NUDGE_MEDS = "nudge_meds_pending"
        /** Soft daily nudge until crisis contacts filled or dismissed — "1" pending. */
        const val KEY_NUDGE_CRISIS = "nudge_crisis_pending"
        const val KEY_CRISIS_ON_WORSENING = "crisis_plan_on_worsening"
        /** JSON array of {id,label,phone} — see [com.moodlife.app.domain.CrisisContacts]. */
        const val KEY_CRISIS_CONTACTS = CrisisContacts.KEY
        const val KEY_WEATHER_LOCATION = "weather_location"
        const val KEY_WEATHER_CITY = "weather_city"
        const val KEY_WEATHER_LAST_FETCH = "weather_last_fetch_at"
        const val KEY_YANDEX_WEATHER_API_KEY = "yandex_weather_api_key"
        const val KEY_HC_LAST_SYNC = "hc_last_sync_at"
        const val KEY_HC_LAST_ROWS = "hc_last_rows"
        const val KEY_HC_LAST_ORIGINS = "hc_last_origins"
        const val KEY_BACKUP_LAST = "backup_last_path"
        /** Weekly auto-backup to user SAF folder — "true" / "false", default off. */
        const val KEY_WEEKLY_BACKUP_ENABLED = "weekly_backup_enabled"
        /** Persisted SAF tree URI for weekly backup folder. */
        const val KEY_WEEKLY_BACKUP_TREE_URI = "weekly_backup_tree_uri"
        /** Last weekly backup status label (path or error). */
        const val KEY_WEEKLY_BACKUP_LAST = "weekly_backup_last"
        /** User height in cm (optional; also filled from Health Connect). */
        const val KEY_BODY_HEIGHT_CM = "body_height_cm"
        /** Show body measurements block on Physical tab — "true" / "false", default off. */
        const val KEY_BODY_MEASUREMENTS_ENABLED = "body_measurements_enabled"
        /** Show lab results on Physical — "true"/"false"; absent defaults to on (Ornament-like). */
        const val KEY_LAB_RESULTS_ENABLED = "lab_results_enabled"
        /** Nutrition goals JSON: {"kcal":N,"protein":N,"fat":N,"carbs":N} — optional. */
        const val KEY_NUTRITION_GOALS = "nutrition_goals_json"
        /** Check-in period scheme: morning_day_night | halves | hours */
        const val KEY_CHECKIN_SCHEME = "checkin_scheme"
        const val KEY_SELFHELP_TAB = "selfhelp_tab_enabled"
        const val KEY_CALENDAR_USER_ICON = "calendar_user_icon"
        /** Per-day personal icons JSON — see [com.moodlife.app.domain.CalendarDayIcons]. */
        const val KEY_CALENDAR_DAY_ICONS = CalendarDayIcons.KEY
        /** Comma-separated check-in axis keys: depressed,elevated,anxious,irritable */
        const val KEY_CHECKIN_AXES = "checkin_axes"
        /** Full check-in config JSON (slots + axes + scale labels). */
        const val KEY_CHECKIN_CONFIG = "checkin_config_json"
        /** Comma-separated report chart ids that are visible. */
        const val KEY_REPORTS_CHARTS = "reports_charts_visible"
        /** Comma-separated chart keys for display order on Reports tab. */
        const val KEY_REPORTS_CHART_ORDER = "reports_charts_order"
        /** One-time flag: "1" after merging newly introduced default chart keys. */
        const val KEY_REPORTS_CHARTS_MIGRATED_V2 = "reports_charts_migrated_v2"
        const val KEY_NOTIF_DIARY_ENABLED = "notif_diary_enabled"
        const val KEY_NOTIF_DIARY_TIME = "notif_diary_time"
        const val KEY_NOTIF_MEDS_ENABLED = "notif_meds_enabled"
        const val KEY_NOTIF_MEDS_TIMES = "notif_meds_times"
    }
}
