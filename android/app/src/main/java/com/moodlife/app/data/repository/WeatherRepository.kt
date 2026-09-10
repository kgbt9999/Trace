package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.WeatherDayDao
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.data.network.GeocodingResult
import com.moodlife.app.data.network.OpenMeteoApi
import com.moodlife.app.data.network.OpenMeteoGeocodingApi
import com.moodlife.app.data.network.OpenMeteoDaily
import com.moodlife.app.data.network.YandexWeatherApi
import com.moodlife.app.data.secure.SecureSecretsStore
import com.moodlife.app.domain.WeatherCodeInfo
import com.moodlife.app.domain.WeatherCodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherDayDao: WeatherDayDao,
    private val settingsRepository: SettingsRepository,
    private val secureSecretsStore: SecureSecretsStore,
    private val openMeteoApi: OpenMeteoApi,
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val yandexWeatherApi: YandexWeatherApi,
) {
    fun observeForDate(date: String): Flow<WeatherDayEntity?> = weatherDayDao.observeByDate(date)

    suspend fun searchCities(query: String, minPopulation: Int = 10_000): List<GeocodingResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return try {
            val results = geocodingApi.search(q).results.orEmpty()
            results.filter { (it.population ?: 0) >= minPopulation || it.population == null }
                .filter { it.name != null && it.latitude != null && it.longitude != null }
                .distinctBy { "${it.name}|${it.admin1}|${it.country}" }
                .take(15)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getLocation(): Pair<Double, Double>? {
        val raw = settingsRepository.get(SettingsRepository.KEY_WEATHER_LOCATION) ?: return null
        val parts = raw.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        return lat to lon
    }

    suspend fun setLocation(lat: Double, lon: Double, cityName: String? = null) {
        settingsRepository.set(SettingsRepository.KEY_WEATHER_LOCATION, "$lat,$lon")
        if (cityName != null) {
            settingsRepository.set(SettingsRepository.KEY_WEATHER_CITY, cityName)
        }
    }

    suspend fun refreshForecast(): RefreshResult {
        val (lat, lon) = getLocation() ?: return RefreshResult(
            success = false,
            message = "Укажите координаты в настройках (широта, долгота)",
        )
        return try {
            val openResult = fetchOpenMeteo(lat, lon)
            if (openResult.success) return openResult
            fetchYandex(lat, lon) ?: openResult
        } catch (e: Exception) {
            fetchYandex(lat, lon) ?: RefreshResult(success = false, message = e.message ?: "Ошибка сети")
        }
    }

    private suspend fun fetchOpenMeteo(lat: Double, lon: Double): RefreshResult {
        return try {
            val response = openMeteoApi.forecast(lat, lon)
            val daily = response.daily ?: return RefreshResult(false, "Пустой ответ Open-Meteo")
            val entities = buildFromOpenMeteo(daily, lat, lon)
            weatherDayDao.upsertAll(entities)
            settingsRepository.set(SettingsRepository.KEY_WEATHER_LAST_FETCH, System.currentTimeMillis().toString())
            RefreshResult(success = true, saved = entities.size, source = "open-meteo")
        } catch (e: Exception) {
            RefreshResult(success = false, message = e.message)
        }
    }

    private suspend fun fetchYandex(lat: Double, lon: Double): RefreshResult? {
        val key = secureSecretsStore.getYandexWeatherApiKey()
        if (key.isBlank()) return null
        return try {
            val response = yandexWeatherApi.forecast(key, lat, lon)
            val city = settingsRepository.get(SettingsRepository.KEY_WEATHER_CITY)
            val location = "$lat,$lon"
            val now = System.currentTimeMillis()
            val entities = response.forecasts.orEmpty().mapNotNull { day ->
                val date = day.date ?: return@mapNotNull null
                val dayPart = day.parts?.day
                val nightPart = day.parts?.night
                val tempAvg = (dayPart?.tempAvg ?: dayPart?.temp ?: 0).toFloat()
                val tempMin = minOf(dayPart?.temp ?: 0, nightPart?.temp ?: 0).toFloat()
                val tempMax = maxOf(dayPart?.temp ?: 0, nightPart?.temp ?: 0).toFloat()
                val condition = dayPart?.condition ?: "clear"
                val info = yandexCondition(condition)
                WeatherDayEntity(
                    id = "weather-$date",
                    date = date,
                    tempAvg = tempAvg,
                    tempMin = tempMin,
                    tempMax = tempMax,
                    pressure = (dayPart?.pressureMm ?: 750).toFloat(),
                    humidity = dayPart?.humidity ?: 50,
                    windSpeed = (dayPart?.windSpeed ?: 0.0).toFloat(),
                    windDir = 0,
                    precipitation = (dayPart?.precMm ?: 0.0).toFloat(),
                    precipitationType = info.type,
                    uvIndex = 0f,
                    cloudness = 0,
                    visibility = null,
                    description = info.description,
                    icon = info.icon,
                    location = location,
                    cityName = city,
                    fetchedAt = now,
                )
            }
            if (entities.isEmpty()) return RefreshResult(false, "Пустой ответ Yandex")
            weatherDayDao.upsertAll(entities)
            settingsRepository.set(SettingsRepository.KEY_WEATHER_LAST_FETCH, now.toString())
            RefreshResult(success = true, saved = entities.size, source = "yandex")
        } catch (e: Exception) {
            RefreshResult(success = false, message = e.message)
        }
    }

    private suspend fun buildFromOpenMeteo(daily: OpenMeteoDaily, lat: Double, lon: Double): List<WeatherDayEntity> {
        val days = daily.time.orEmpty()
        val city = settingsRepository.get(SettingsRepository.KEY_WEATHER_CITY)
        val location = "$lat,$lon"
        val now = System.currentTimeMillis()
        return days.mapIndexedNotNull { i, dayDate ->
            if (dayDate.isBlank()) return@mapIndexedNotNull null
            val code = daily.weather_code?.getOrNull(i) ?: 0
            val info = WeatherCodes.fromCode(code)
            WeatherDayEntity(
                id = "weather-$dayDate",
                date = dayDate,
                tempAvg = daily.temperature_2m_mean?.getOrNull(i)?.toFloat() ?: 0f,
                tempMin = daily.temperature_2m_min?.getOrNull(i)?.toFloat() ?: 0f,
                tempMax = daily.temperature_2m_max?.getOrNull(i)?.toFloat() ?: 0f,
                pressure = daily.pressure_msl_mean?.getOrNull(i)?.toFloat()?.times(0.750062f) ?: 0f,
                humidity = daily.relative_humidity_2m_mean?.getOrNull(i) ?: 0,
                windSpeed = daily.wind_speed_10m_max?.getOrNull(i)?.toFloat() ?: 0f,
                windDir = daily.wind_direction_10m_dominant?.getOrNull(i) ?: 0,
                precipitation = daily.precipitation_sum?.getOrNull(i)?.toFloat() ?: 0f,
                precipitationType = info.type,
                uvIndex = daily.uv_index_max?.getOrNull(i)?.toFloat() ?: 0f,
                cloudness = daily.cloud_cover_mean?.getOrNull(i) ?: 0,
                visibility = null,
                description = info.description,
                icon = info.icon,
                location = location,
                cityName = city,
                fetchedAt = now,
            )
        }
    }

    private fun yandexCondition(cond: String): WeatherCodeInfo {
        val lower = cond.lowercase()
        return when {
            lower.contains("snow") -> WeatherCodeInfo("Снег", "❄️", "snow")
            lower.contains("rain") || lower.contains("drizzle") -> WeatherCodeInfo("Дождь", "🌧️", "rain")
            lower.contains("cloud") -> WeatherCodeInfo("Облачно", "☁️", "none")
            else -> WeatherCodeInfo("Ясно", "☀️", "none")
        }
    }

    suspend fun ensureTodayWeather(): WeatherDayEntity? {
        val today = com.moodlife.app.util.DateUtils.todayIso()
        val cached = weatherDayDao.observeByDate(today).first()
        if (cached != null) {
            val lastFetch = settingsRepository.get(SettingsRepository.KEY_WEATHER_LAST_FETCH)?.toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - lastFetch < 3_600_000) return cached
        }
        if (getLocation() == null) return cached
        refreshForecast()
        return weatherDayDao.observeByDate(today).first()
    }

    data class RefreshResult(
        val success: Boolean,
        val message: String? = null,
        val saved: Int = 0,
        val source: String? = null,
    )
}
