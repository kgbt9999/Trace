package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.ui.theme.LocalMoodColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard(
    weather: WeatherDayEntity?,
    modifier: Modifier = Modifier,
    date: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
            Text(stringResource(R.string.weather_title), style = MaterialTheme.typography.titleMedium)
            if (weather == null) {
                Text(stringResource(R.string.weather_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                val city = weather.cityName?.takeIf { it.isNotBlank() }
                if (city != null) {
                    Text("· $city", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weather.icon ?: "🌡️", style = MaterialTheme.typography.headlineMedium)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            "${weather.tempAvg.roundToInt()}°C",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "${weather.tempMin.roundToInt()}…${weather.tempMax.roundToInt()}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(weather.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WeatherMetric(
                        stringResource(R.string.weather_pressure),
                        "${weather.pressure.roundToInt()} мм",
                        highlight = weather.pressure < 745f || weather.pressure > 765f,
                    )
                    WeatherMetric(
                        stringResource(R.string.weather_humidity),
                        "${weather.humidity}%",
                    )
                    WeatherMetric(
                        stringResource(R.string.weather_wind),
                        String.format(Locale("ru"), "%.1f м/с", weather.windSpeed),
                        highlight = weather.windSpeed > 10f,
                    )
                    WeatherMetric(
                        stringResource(R.string.weather_uv),
                        String.format(Locale("ru"), "%.1f", weather.uvIndex),
                        highlight = weather.uvIndex >= 6f,
                    )
                    if (weather.precipitation > 0f) {
                        WeatherMetric(
                            stringResource(R.string.weather_precip),
                            String.format(Locale("ru"), "%.1f мм", weather.precipitation),
                        )
                    }
                    weather.visibility?.let { vis ->
                        WeatherMetric(
                            stringResource(R.string.weather_visibility),
                            String.format(Locale("ru"), "%.1f км", vis / 1000f),
                        )
                    }
                    WeatherMetric(
                        stringResource(R.string.weather_cloudness),
                        "${weather.cloudness}%",
                    )
                }
                if (date != null) {
                    val updated = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(weather.fetchedAt))
                    Text(
                        stringResource(R.string.weather_fetched, date, updated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, highlight: Boolean = false) {
    val mood = LocalMoodColors.current
    val bg = if (highlight) mood.warning.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) mood.warning else MaterialTheme.colorScheme.onSurface,
        )
    }
}
