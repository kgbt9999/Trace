package com.moodlife.app.di

import com.moodlife.app.data.network.OpenMeteoApi
import com.moodlife.app.data.network.OpenMeteoGeocodingApi
import com.moodlife.app.data.network.YandexWeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient): OpenMeteoApi =
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApi::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteoGeocodingApi(client: OkHttpClient): OpenMeteoGeocodingApi =
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoGeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideYandexWeatherApi(client: OkHttpClient): YandexWeatherApi =
        Retrofit.Builder()
            .baseUrl("https://api.weather.yandex.ru/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YandexWeatherApi::class.java)
}
