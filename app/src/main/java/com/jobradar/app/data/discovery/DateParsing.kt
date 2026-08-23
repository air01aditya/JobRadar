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

/** RFC-822 style dates, as used in RSS <pubDate> elements. */
fun parseRfc822DateMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
