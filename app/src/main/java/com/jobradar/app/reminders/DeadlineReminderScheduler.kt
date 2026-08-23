package com.jobradar.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DeadlineReminderScheduler {

    private fun workName(jobId: String) = "deadline_$jobId"

    /** Schedules a local notification to fire at [deadlineAtMillis]. Past deadlines fire immediately. */
    fun schedule(context: Context, jobId: String, title: String, company: String, url: String, deadlineAtMillis: Long) {
        val delayMillis = (deadlineAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(DeadlineReminderWorker.KEY_JOB_ID, jobId)
                    .putString(DeadlineReminderWorker.KEY_TITLE, title)
                    .putString(DeadlineReminderWorker.KEY_COMPANY, company)
                    .putString(DeadlineReminderWorker.KEY_URL, url)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(jobId), ExistingWorkPolicy.REPLACE, request)
    }

    /** Called when a deadline is cleared or the tracked job is removed. */
    fun cancel(context: Context, jobId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(jobId))
    }
}
