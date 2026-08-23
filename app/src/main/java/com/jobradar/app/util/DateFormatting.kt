package com.jobradar.app.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun relativeTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val diffMs = System.currentTimeMillis() - timestamp.toDate().time
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

fun formatDeadline(epochMillis: Long): String = deadlineFormat.format(Date(epochMillis))
