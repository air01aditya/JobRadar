package com.jobradar.app.data.discovery

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Handles offset timestamps ("...Z"/"+00:00"/"-04:00") and bare local timestamps (assumed UTC). */
fun parseIsoDateMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        try {
            Instant.parse(value).toEpochMilli()
        } catch (e2: DateTimeParseException) {
            try {
                LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (e3: DateTimeParseException) {
                try {
                    // Bare "YYYY-MM-DD" (e.g. Workable's published_on) — assume start of day UTC.
                    LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (e4: Exception) {
                    null
                }
            }
        }
    }
}

private val WORKDAY_DAYS_AGO = Regex("(\\d+)\\+?\\s+days?\\s+ago", RegexOption.IGNORE_CASE)

/**
 * Workday only gives relative text: "Posted Today", "Posted Yesterday", "Posted 3 Days Ago",
 * "Posted 30+ Days Ago". "30+" becomes 31 days so the freshness filter treats it as old.
 */
fun parseWorkdayPostedOn(value: String?, nowMillis: Long): Long? {
    val text = value?.lowercase() ?: return null
    val days = when {
        "today" in text -> 0L
        "yesterday" in text -> 1L
        else -> WORKDAY_DAYS_AGO.find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { if ("+" in text) it + 1 else it }
    } ?: return null
    return nowMillis - java.util.concurrent.TimeUnit.DAYS.toMillis(days)
}

/** RFC-822 style dates, as used in RSS <pubDate> elements. */
fun parseRfc822DateMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
