package com.moodlife.app.domain

/**
 * Popular apps that write into Health Connect (Guava-style hub model).
 * Trace does not invent direct APIs — HC where available, else open-app + file import.
 */
object HcSourceCatalog {

    enum class Access { HEALTH_CONNECT, OPEN_APP_MANUAL, FILE_IMPORT }

    data class AppSource(
        val id: String,
        val title: String,
        val category: String,
        val whatWrites: String,
        val howToConnect: String,
        val access: Access = Access.HEALTH_CONNECT,
        val packages: List<String> = emptyList(),
    )

    val APPS = listOf(
        AppSource(
            id = "unique_health",
            title = "Unique Health",
            category = "Вес / тело",
            whatWrites = "Вес, состав тела (если пишет в HC)",
            howToConnect = "Unique Health → настройки → Health Connect → разрешите запись веса. Затем синхронизируйте Trace.",
            packages = listOf("com.unique.health"),
        ),
        AppSource(
            id = "google_fit",
            title = "Google Fit",
            category = "Шаги / активность",
            whatWrites = "Шаги, активность, вес",
            howToConnect = "Google Fit → Профиль → Health Connect → включите запись",
            packages = listOf("com.google.android.apps.fitness"),
        ),
        AppSource(
            id = "samsung",
            title = "Samsung Health",
            category = "Шаги / активность",
            whatWrites = "Шаги, сон, вес, активность",
            howToConnect = "Samsung Health → Настройки → Health Connect → разрешите запись",
            packages = listOf("com.sec.android.app.shealth"),
        ),
        AppSource(
            id = "sleep_as_android",
            title = "Sleep as Android",
            category = "Сон",
            whatWrites = "Сон",
            howToConnect = "Sleep as Android → интеграции → Health Connect",
            packages = listOf("com.urbandroid.sleep"),
        ),
        AppSource(
            id = "sleep_cycle",
            title = "Sleep Cycle",
            category = "Сон",
            whatWrites = "Сон",
            howToConnect = "Sleep Cycle → настройки → Health Connect",
            packages = listOf("com.northcube.sleepcycle"),
        ),
        AppSource(
            id = "flo",
            title = "Flo",
            category = "Цикл",
            whatWrites = "Цикл / менструация",
            howToConnect = "Flo → настройки → Health Connect (если есть) или экспорт JSON → Импорт файла ниже",
            access = Access.FILE_IMPORT,
            packages = listOf("org.iggymedia.periodtracker", "com.glow.android.periodtracker"),
        ),
        AppSource(
            id = "clue",
            title = "Clue",
            category = "Цикл",
            whatWrites = "Цикл / менструация",
            howToConnect = "Clue → настройки → Health Connect. Если HC недоступен — откройте Clue и ведите дни вручную в Trace.",
            packages = listOf("com.clue.android"),
        ),
        AppSource(
            id = "clatch",
            title = "Clatch",
            category = "Цикл",
            whatWrites = "Цикл",
            howToConnect = "Прямого API нет. Откройте Clatch и при необходимости перенесите даты в Trace / файл.",
            access = Access.OPEN_APP_MANUAL,
            packages = listOf("com.clatch.app"),
        ),
        AppSource(
            id = "stardust",
            title = "Stardust",
            category = "Цикл",
            whatWrites = "Цикл",
            howToConnect = "Прямого API нет. Откройте Stardust; даты цикла можно внести в настройках Trace.",
            access = Access.OPEN_APP_MANUAL,
            packages = listOf("com.stardust.cycle"),
        ),
        AppSource(
            id = "cycle_patterns",
            title = "Cycle patterns",
            category = "Цикл",
            whatWrites = "Цикл",
            howToConnect = "Прямого API нет. Откройте приложение и при необходимости импортируйте файл / внесите вручную.",
            access = Access.OPEN_APP_MANUAL,
        ),
        AppSource(
            id = "lifesum",
            title = "Lifesum",
            category = "Питание",
            whatWrites = "Калории / питание (если пишет в HC)",
            howToConnect = "Lifesum → интеграции → Health Connect. Иначе откройте Lifesum и ориентируйтесь на сводку вручную.",
            packages = listOf("com.sillens.shapeupclub"),
        ),
        AppSource(
            id = "fatsecret",
            title = "FatSecret",
            category = "Питание",
            whatWrites = "Питание",
            howToConnect = "FatSecret → Health Connect (если доступно) или ручной учёт.",
            access = Access.OPEN_APP_MANUAL,
            packages = listOf("com.fatsecret.android"),
        ),
        AppSource(
            id = "yazio",
            title = "Yazio",
            category = "Питание",
            whatWrites = "Питание",
            howToConnect = "Yazio → настройки → Health Connect при наличии.",
            packages = listOf("com.yazio.android"),
        ),
        AppSource(
            id = "cronometer",
            title = "Cronometer",
            category = "Питание",
            whatWrites = "Питание",
            howToConnect = "Cronometer → настройки → Health Connect.",
            packages = listOf("com.cronometer.android.gold", "com.cronometer.android"),
        ),
        AppSource(
            id = "finch",
            title = "Finch",
            category = "Привычки",
            whatWrites = "Привычки / самочувствие",
            howToConnect = "Прямой синхронизации нет. Откройте Finch; важные привычки отмечайте триггерами в Trace.",
            access = Access.OPEN_APP_MANUAL,
            packages = listOf("com.finch.finch"),
        ),
        AppSource(
            id = "structured",
            title = "Structured",
            category = "Привычки",
            whatWrites = "Распорядок дня",
            howToConnect = "Прямого API нет. Откройте Structured и перенесите важное вручную.",
            access = Access.OPEN_APP_MANUAL,
        ),
        AppSource(
            id = "improve",
            title = "Improve",
            category = "Привычки",
            whatWrites = "Привычки",
            howToConnect = "Прямого API нет — откройте приложение и при необходимости отметьте факторы в Trace.",
            access = Access.OPEN_APP_MANUAL,
        ),
        AppSource(
            id = "habitify",
            title = "Habitify",
            category = "Привычки",
            whatWrites = "Привычки",
            howToConnect = "Прямого API нет. Откройте Habitify; для учёта в Trace используйте триггеры.",
            access = Access.OPEN_APP_MANUAL,
            packages = listOf("com.oristand.habitify"),
        ),
        AppSource(
            id = "onrise",
            title = "Onrise",
            category = "Привычки",
            whatWrites = "Привычки",
            howToConnect = "Прямого API нет — ручной перенос.",
            access = Access.OPEN_APP_MANUAL,
        ),
        AppSource(
            id = "loop",
            title = "Loop Habit Tracker",
            category = "Привычки",
            whatWrites = "Привычки",
            howToConnect = "Loop может экспортировать CSV. Импортируйте файл ниже или отмечайте в Trace вручную.",
            access = Access.FILE_IMPORT,
            packages = listOf("org.isoron.uhabits"),
        ),
        AppSource(
            id = "fitbit",
            title = "Fitbit",
            category = "Шаги / активность",
            whatWrites = "Шаги, сон, пульс",
            howToConnect = "Fitbit → настройки → Health Connect",
            packages = listOf("com.fitbit.FitbitMobile"),
        ),
        AppSource(
            id = "garmin",
            title = "Garmin Connect",
            category = "Шаги / активность",
            whatWrites = "Активность, сон",
            howToConnect = "Garmin Connect → настройки → Health Connect",
            packages = listOf("com.garmin.android.apps.connectmobile"),
        ),
    )

    fun labelForPackage(packageName: String): String {
        APPS.firstOrNull { packageName in it.packages }?.title?.let { return it }
        return when {
            packageName.contains("shealth", ignoreCase = true) -> "Samsung Health"
            packageName.contains("fitness", ignoreCase = true) -> "Google Fit"
            packageName.contains("fitbit", ignoreCase = true) -> "Fitbit"
            packageName.contains("garmin", ignoreCase = true) -> "Garmin"
            packageName.contains("sleep", ignoreCase = true) -> "Трекер сна"
            packageName.contains("withings", ignoreCase = true) -> "Withings"
            packageName.contains("oura", ignoreCase = true) -> "Oura"
            packageName.contains("healthdata", ignoreCase = true) -> "Health Connect"
            else -> packageName.substringAfterLast('.').replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }
    }
}
