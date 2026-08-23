package com.jobradar.app.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jobradar.app.R
import com.jobradar.app.messaging.NotificationChannels

class DeadlineReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val company = inputData.getString(KEY_COMPANY).orEmpty()
        val url = inputData.getString(KEY_URL).orEmpty()

        if (!hasNotificationPermission(applicationContext)) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.DEADLINE_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Deadline today: $title")
            .setContentText(company.ifBlank { "Don't miss the application window." })
            .setAutoCancel(true)
            .apply {
                if (url.isNotBlank()) {
                    setContentIntent(
                        android.app.PendingIntent.getActivity(
                            applicationContext,
                            jobId.hashCode(),
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                        )
                    )
                }
            }
            .build()

        NotificationManagerCompat.from(applicationContext).notify(jobId.hashCode(), notification)
        return Result.success()
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_TITLE = "title"
        const val KEY_COMPANY = "company"
        const val KEY_URL = "url"
    }
}
