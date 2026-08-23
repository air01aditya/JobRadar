package com.jobradar.app.data.discovery.sources

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jobradar.app.R
import com.jobradar.app.messaging.NotificationChannels

/**
 * Telegram is the one source that reads a raw webpage instead of a proper data feed — if
 * Telegram ever changes that page's layout, our parser could silently start returning zero
 * posts. This tracks consecutive empty results and raises one heads-up notification (not a
 * repeat every 15 min) so the problem gets noticed within an hour, not discovered by accident.
 */
object TelegramHealthMonitor {
    private const val PREFS = "telegram_health"
    private const val KEY_EMPTY_STREAK = "empty_streak"
    private const val KEY_WARNED = "warned"
    private const val EMPTY_STREAK_THRESHOLD = 3 // ~3 quick-checks, ~45 min of zero results

    fun recordResult(context: Context, jobsFetched: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (jobsFetched > 0) {
            prefs.edit().putInt(KEY_EMPTY_STREAK, 0).putBoolean(KEY_WARNED, false).apply()
            return
        }

        val streak = prefs.getInt(KEY_EMPTY_STREAK, 0) + 1
        prefs.edit().putInt(KEY_EMPTY_STREAK, streak).apply()

        if (streak >= EMPTY_STREAK_THRESHOLD && !prefs.getBoolean(KEY_WARNED, false)) {
            prefs.edit().putBoolean(KEY_WARNED, true).apply()
            notifyBroken(context)
        }
    }

    private fun notifyBroken(context: Context) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.NEW_JOBS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Telegram source may be broken")
            .setContentText("No posts found across all Telegram channels for a while — Telegram may have changed its page layout.")
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(TELEGRAM_HEALTH_NOTIFICATION_ID, notification) }
    }
}

private const val TELEGRAM_HEALTH_NOTIFICATION_ID = 918273
