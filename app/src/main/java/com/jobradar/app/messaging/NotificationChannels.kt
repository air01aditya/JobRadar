package com.jobradar.app.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val NEW_JOBS = "new_jobs"
    const val DEADLINE_REMINDERS = "deadline_reminders"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(NEW_JOBS, "New job matches", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when JobRadar finds a new matching job posting"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(DEADLINE_REMINDERS, "Deadline reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for deadlines you set on tracked jobs"
            }
        )
    }
}
