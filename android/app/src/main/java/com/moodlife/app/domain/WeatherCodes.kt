package com.moodlife.app.domain

data class WeatherCodeInfo(
    val description: String,
    val icon: String,
    val type: String,
)

object WeatherCodes {
    private val map = mapOf(
        0 to WeatherCodeInfo("Ясно", "☀️", "none"),
        1 to WeatherCodeInfo("Преимущественно ясно", "🌤️", "none"),
        2 to WeatherCodeInfo("Переменная облачность", "⛅", "none"),
        3 to WeatherCodeInfo("Пасмурно", "☁️", "none"),
        45 to WeatherCodeInfo("Туман", "🌫️", "none"),
        48 to WeatherCodeInfo("Изморозь", "🌫️", "none"),
        51 to WeatherCodeInfo("Морось слабая", "🌦️", "rain"),
        53 to WeatherCodeInfo("Морось умеренная", "🌦️", "rain"),
        55 to WeatherCodeInfo("Морось сильная", "🌧️", "rain"),
        61 to WeatherCodeInfo("Дождь слабый", "🌦️", "rain"),
        63 to WeatherCodeInfo("Дождь умеренный", "🌧️", "rain"),
        65 to WeatherCodeInfo("Дождь сильный", "🌧️", "rain"),
        71 to WeatherCodeInfo("Снег слабый", "🌨️", "snow"),
        73 to WeatherCodeInfo("Снег умеренный", "❄️", "snow"),
        75 to WeatherCodeInfo("Снег сильный", "❄️", "snow"),
        80 to WeatherCodeInfo("Ливень слабый", "🌦️", "rain"),
        81 to WeatherCodeInfo("Ливень умеренный", "🌧️", "rain"),
        82 to WeatherCodeInfo("Ливень сильный", "⛈️", "rain"),
        95 to WeatherCodeInfo("Гроза", "⛈️", "rain"),
        96 to WeatherCodeInfo("Гроза с градом", "⛈️", "rain"),
        99 to WeatherCodeInfo("Сильная гроза с градом", "⛈️", "rain"),
    )

    fun fromCode(code: Int): WeatherCodeInfo =
        map[code] ?: WeatherCodeInfo("Погода", "🌡️", "none")
}
