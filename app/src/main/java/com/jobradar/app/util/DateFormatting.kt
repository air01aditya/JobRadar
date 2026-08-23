package com.jobradar.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun relativeTime(epochMillis: Long?): String {
    if (epochMillis == null) return ""
    val diffMs = System.currentTimeMillis() - epochMillis
    if (diffMs < 0) return "just now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

private val deadlineFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
private val savedAtFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

fun formatDeadline(epochMillis: Long): String = deadlineFormat.format(Date(epochMillis))

fun formatSavedAt(epochMillis: Long): String = savedAtFormat.format(Date(epochMillis))
