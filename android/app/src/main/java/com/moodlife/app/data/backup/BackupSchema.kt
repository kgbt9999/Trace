package com.moodlife.app.data.backup

/**
 * Canonical table / column contract for JSON backup import & export.
 * Column names must stay in sync with Room entities (additive only).
 */
object BackupSchema {
    val TABLES: List<String> = listOf(
        "mood_entries",
        "mood_check_ins",
        "day_notes",
        "medications",
        "medication_logs",
        "symptoms",
        "symptom_logs",
        "weather_days",
        "external_health_days",
        "settings",
        "period_logs",
        "period_settings",
        "factors",
        "factor_logs",
        "early_warning_signs",
        "warning_triggers",
        "flo_logs",
        "body_measurements",
        "lab_results",
    )

    /** Settings keys never written into backup JSON (secrets / transient paths). */
    val REDACTED_SETTING_KEYS: Set<String> = setOf(
        "yandex_weather_api_key",
        "backup_last_path",
        "weekly_backup_tree_uri",
    )

    private val COLUMNS: Map<String, Set<String>> = mapOf(
        "mood_entries" to setOf(
            "id", "date", "depressed", "elevated", "anxious", "irritable",
            "energy", "concentration", "appetite", "sociability",
            "sleepHours", "sleepQuality", "sleepTime", "wakeTime",
            "functioning", "safetyCheck", "alcoholUse", "substanceUse",
            "episodePhase", "routineScore", "note", "createdAt", "updatedAt",
        ),
        "mood_check_ins" to setOf(
            "id", "date", "timeOfDay", "depressed", "elevated", "anxious", "irritable",
            "valuesJson", "recordedAt", "updatedAt",
        ),
        "day_notes" to setOf("id", "date", "content", "createdAt", "updatedAt"),
        "medications" to setOf(
            "id", "name", "dosage", "schedule", "intakeTimes", "isRegular",
            "doseVaries", "doseNote", "isActive", "createdAt", "updatedAt",
        ),
        "medication_logs" to setOf(
            "id", "medicationId", "moodEntryId", "date", "taken", "slotsTaken",
            "dosageOverride", "nameSnapshot", "intakeTimesSnapshot", "createdAt", "updatedAt",
        ),
        "symptoms" to setOf(
            "id", "name", "category", "color", "scaleType", "scaleMax", "hint",
            "sortOrder", "isActive", "createdAt", "updatedAt",
        ),
        "symptom_logs" to setOf(
            "id", "moodEntryId", "symptomId", "severity", "note", "createdAt", "updatedAt",
        ),
        "weather_days" to setOf(
            "id", "date", "moodEntryId", "tempAvg", "tempMin", "tempMax", "pressure",
            "humidity", "windSpeed", "windDir", "precipitation", "precipitationType",
            "uvIndex", "cloudness", "visibility", "description", "icon", "location",
            "cityName", "fetchedAt",
        ),
        "external_health_days" to setOf(
            "id", "date", "source", "kind", "sleepHours", "sleepQuality", "steps",
            "activeMinutes", "calories", "proteinG", "carbsG", "fatG", "nutritionScore",
            "weightKg", "bmi", "bodyFatPct", "flowLevel", "habitsCompleted", "habitsTotal",
            "distanceM", "payload", "note", "createdAt", "updatedAt",
        ),
        "settings" to setOf("id", "key", "value", "updatedAt"),
        "period_logs" to setOf(
            "id", "moodEntryId", "flowLevel", "cramps", "headache", "fatigue",
            "moodChanges", "note", "createdAt", "updatedAt",
        ),
        "period_settings" to setOf(
            "id", "cycleLength", "periodLength", "lastPeriodStart", "irregular",
            "createdAt", "updatedAt",
        ),
        "factors" to setOf(
            "id", "name", "category", "color", "scaleType", "isActive", "createdAt", "updatedAt",
        ),
        "factor_logs" to setOf(
            "id", "moodEntryId", "factorId", "intensity", "note", "createdAt", "updatedAt",
        ),
        "early_warning_signs" to setOf(
            "id", "name", "direction", "category", "isActive", "sortOrder", "createdAt", "updatedAt",
        ),
        "warning_triggers" to setOf(
            "id", "moodEntryId", "warningSignId", "intensity", "note", "createdAt", "updatedAt",
        ),
        "flo_logs" to setOf(
            "id", "date", "flowLevel", "symptoms", "note", "source", "createdAt", "updatedAt",
        ),
        "body_measurements" to setOf(
            "id", "date", "shoulderWidthCm", "bicepsCm", "chestCm", "underBustCm",
            "waistCm", "hipsCm", "thighCm", "weightKg", "note", "createdAt", "updatedAt",
        ),
        "lab_results" to setOf(
            "id", "name", "valueText", "valueNumeric", "unit", "date", "clinic",
            "createdAt", "updatedAt",
        ),
    )

    fun allowedColumns(table: String): Set<String> = COLUMNS[table].orEmpty()
}
