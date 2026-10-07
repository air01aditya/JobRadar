package com.jobradar.app.messaging

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jobradar.app.MainActivity
import com.jobradar.app.R
import com.jobradar.app.data.discovery.JobDiscoveryEntity

private const val GROUP_THRESHOLD = 5
private const val GROUPED_NOTIFICATION_ID = 1
private const val INBOX_MAX_LINES = 6

object JobNotifier {

    fun notifyNewJobs(context: Context, newJobs: List<JobDiscoveryEntity>) {
        if (newJobs.isEmpty()) return
        if (!hasNotificationPermission(context)) return

        val manager = NotificationManagerCompat.from(context)
        if (newJobs.size > GROUP_THRESHOLD) {
            manager.notify(GROUPED_NOTIFICATION_ID, buildGroupedNotification(context, newJobs))
        } else {
            newJobs.forEach { job -> manager.notify(job.id.hashCode(), buildSingleNotification(context, job)) }
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun buildSingleNotification(context: Context, job: JobDiscoveryEntity) =
        NotificationCompat.Builder(context, NotificationChannels.NEW_JOBS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New: ${job.title}")
            .setContentText(listOf(job.company, job.location).filter { it.isNotBlank() }.joinToString(" · "))
            .setAutoCancel(true)
            .setContentIntent(openJobIntent(context, job.url) ?: openAppIntent(context))
            .build()

    private fun buildGroupedNotification(context: Context, jobs: List<JobDiscoveryEntity>): android.app.Notification {
        val inbox = NotificationCompat.InboxStyle()
        jobs.take(INBOX_MAX_LINES).forEach { job ->
            inbox.addLine(listOf(job.title, job.company).filter { it.isNotBlank() }.joinToString(" — "))
        }
        if (jobs.size > INBOX_MAX_LINES) inbox.setSummaryText("+${jobs.size - INBOX_MAX_LINES} more")

        return NotificationCompat.Builder(context, NotificationChannels.NEW_JOBS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${jobs.size} new fresher jobs")
            .setContentText(jobs.first().title)
            .setStyle(inbox)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    // Job URLs come from third-party sources; only ever hand a web link to the system,
    // never an arbitrary scheme that could launch some other app.
    private fun openJobIntent(context: Context, url: String): PendingIntent? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" && uri.scheme != "http") return null
        return PendingIntent.getActivity(
            context,
            url.hashCode(),
            Intent(Intent.ACTION_VIEW, uri),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
