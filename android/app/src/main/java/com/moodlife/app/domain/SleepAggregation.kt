package com.moodlife.app.domain

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns Health Connect sleep sessions into one sleep figure per day.
 *
 * Previously every session of every app was summed per day, so the same night written by
 * Sleep as Android *and* another app (Samsung Health, Google Fit, a watch) counted twice, and
 * awake/out-of-bed stages inside a session counted as sleep.
 */
object SleepAggregation {

    /** Dedicated sleep trackers win over generic hubs when both wrote the night. */
    val PREFERRED_ORIGINS = listOf("com.urbandroid.sleep")

    data class Session(
        val origin: String,
        val start: Instant,
        val end: Instant,
        /** Minutes in awake / out-of-bed / awake-in-bed stages, if the app wrote stages. */
        val awakeMinutes: Long = 0,
    )

    data class Day(
        val date: String,
        val hours: Float,
        val bedTime: String,
        val wakeTime: String,
        val origin: String,
    ) {
        fun payload(): String = JSONObject()
            .put("bed", bedTime)
            .put("wake", wakeTime)
            .put("origin", origin)
            .toString()
    }

    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /** The night belongs to the day you wake up on. */
    fun byDay(sessions: List<Session>, zone: ZoneId = ZoneId.systemDefault()): List<Day> =
        sessions
            .filter { it.end.isAfter(it.start) }
            .groupBy { it.end.atZone(zone).toLocalDate().toString() }
            .map { (date, daySessions) ->
                val perOrigin = daySessions.groupBy { it.origin }.mapValues { (_, list) -> asleepMinutes(list) }
                val origin = PREFERRED_ORIGINS.firstOrNull { it in perOrigin }
                    ?: perOrigin.maxBy { it.value }.key
                // Bed/wake = the longest continuous block (overlapping sessions merged), not a nap.
                val main = mergedBlocks(daySessions.filter { it.origin == origin })
                    .maxBy { java.time.Duration.between(it.first, it.second).toMinutes() }
                Day(
                    date = date,
                    hours = perOrigin.getValue(origin) / 60f,
                    bedTime = main.first.atZone(zone).format(hm),
                    wakeTime = main.second.atZone(zone).format(hm),
                    origin = origin,
                )
            }
            .sortedBy { it.date }

    /** Union of the sessions' time (overlaps counted once) minus awake stages. */
    private fun asleepMinutes(sessions: List<Session>): Long {
        val total = mergedBlocks(sessions).sumOf { java.time.Duration.between(it.first, it.second).toMinutes() }
        return (total - sessions.sumOf { it.awakeMinutes }).coerceAtLeast(0)
    }

    private fun mergedBlocks(sessions: List<Session>): List<Pair<Instant, Instant>> {
        val blocks = mutableListOf<Pair<Instant, Instant>>()
        sessions.sortedBy { it.start }.forEach { s ->
            val last = blocks.lastOrNull()
            if (last != null && !s.start.isAfter(last.second)) {
                if (s.end.isAfter(last.second)) blocks[blocks.lastIndex] = last.first to s.end
            } else {
                blocks += s.start to s.end
            }
        }
        return blocks
    }

    fun bedTime(payload: String?): String? = field(payload, "bed")
    fun wakeTime(payload: String?): String? = field(payload, "wake")

    private fun field(payload: String?, key: String): String? = try {
        payload?.let { JSONObject(it).optString(key).takeIf { v -> v.matches(Regex("\\d{2}:\\d{2}")) } }
    } catch (_: Exception) {
        null
    }
}
