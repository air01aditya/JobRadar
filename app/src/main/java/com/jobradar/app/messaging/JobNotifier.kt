package com.jobradar.app.messaging

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jobradar.app.R
import com.jobradar.app.data.discovery.JobDiscoveryEntity

private const val GROUP_THRESHOLD = 5

object JobNotifier {

    fun notifyNewJobs(context: Context, newJobs: List<JobDiscoveryEntity>) {
        if (newJobs.isEmpty()) return
        if (!hasNotificationPermission(context)) return

        val manager = NotificationManagerCompat.from(context)
        if (newJobs.size > GROUP_THRESHOLD) {
            manager.notify(0, buildGroupedNotification(context, newJobs.size))
        } else {
            newJobs.forEachIndexed { index, job ->
                manager.notify(job.id.hashCode(), buildSingleNotification(context, job))
            }
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
            .setContentText("${job.company} · ${job.location}")
            .setAutoCancel(true)
            .setContentIntent(openUrlIntent(context, job.url))
            .build()

    private fun buildGroupedNotification(context: Context, count: Int) =
        NotificationCompat.Builder(context, NotificationChannels.NEW_JOBS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New job matches")
            .setContentText("$count new postings match your filters — open JobRadar to see them.")
            .setAutoCancel(true)
            .build()

    private fun openUrlIntent(context: Context, url: String) =
        android.app.PendingIntent.getActivity(
            context,
            url.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
}
