package com.moodlife.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo geocoding — search cities by name (population filter client-side). */
interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 20,
        @Query("language") language: String = "ru",
        @Query("format") format: String = "json",
    ): GeocodingResponse
}

data class GeocodingResponse(
    val results: List<GeocodingResult>? = null,
)

data class GeocodingResult(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val admin1: String? = null,
    val population: Int? = null,
) {
    val label: String
        get() = buildString {
            append(name.orEmpty())
            admin1?.takeIf { it.isNotBlank() }?.let { append(", $it") }
            country?.takeIf { it.isNotBlank() }?.let { append(", $it") }
        }
}
