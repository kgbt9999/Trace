package com.moodlife.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("past_days") pastDays: Int = 3,
    ): OpenMeteoResponse
}

data class OpenMeteoResponse(
    val daily: OpenMeteoDaily? = null,
)

data class OpenMeteoDaily(
    val time: List<String>? = null,
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
    val temperature_2m_mean: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
    val weather_code: List<Int?>? = null,
    val wind_speed_10m_max: List<Double?>? = null,
    val wind_direction_10m_dominant: List<Int?>? = null,
    val relative_humidity_2m_mean: List<Int?>? = null,
    val pressure_msl_mean: List<Double?>? = null,
    val cloud_cover_mean: List<Int?>? = null,
    val uv_index_max: List<Double?>? = null,
)

private const val DAILY_PARAMS =
    "temperature_2m_max,temperature_2m_min,temperature_2m_mean,precipitation_sum,weather_code," +
        "wind_speed_10m_max,wind_direction_10m_dominant,relative_humidity_2m_mean,pressure_msl_mean," +
        "cloud_cover_mean,uv_index_max"
