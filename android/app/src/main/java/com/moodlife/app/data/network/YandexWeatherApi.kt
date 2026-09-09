package com.moodlife.app.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface YandexWeatherApi {
    @GET("v2/forecast")
    suspend fun forecast(
        @Header("X-Yandex-API-Key") apiKey: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 10,
        @Query("hours") hours: Boolean = false,
        @Query("extra") extra: Boolean = true,
    ): YandexForecastResponse
}

data class YandexForecastResponse(
    val forecasts: List<YandexDay>? = null,
)

data class YandexDay(
    val date: String? = null,
    val parts: YandexParts? = null,
)

data class YandexParts(
    val day: YandexPart? = null,
    val night: YandexPart? = null,
)

data class YandexPart(
    val temp: Int? = null,
    @SerializedName("temp_avg") val tempAvg: Int? = null,
    @SerializedName("pressure_mm") val pressureMm: Int? = null,
    val humidity: Int? = null,
    @SerializedName("wind_speed") val windSpeed: Double? = null,
    @SerializedName("wind_dir") val windDir: String? = null,
    val condition: String? = null,
    @SerializedName("prec_mm") val precMm: Double? = null,
)
