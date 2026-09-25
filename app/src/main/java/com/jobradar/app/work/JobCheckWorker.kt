package com.jobradar.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jobradar.app.JobRadarApp
import com.jobradar.app.messaging.JobNotifier
import java.util.concurrent.TimeUnit

class JobCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val includeCompanyScan = inputData.getBoolean(KEY_INCLUDE_COMPANY_SCAN, false)
        val repository = (applicationContext as JobRadarApp).jobDiscoveryRepository
        val newJobs = repository.runCheck(includeCompanyScan)
        JobNotifier.notifyNewJobs(applicationContext, newJobs)
        return Result.success()
    }

    companion object {
        private const val KEY_INCLUDE_COMPANY_SCAN = "include_company_scan"
        private const val QUICK_PERIODIC_WORK_NAME = "job_check_quick_periodic"
        private const val COMPANY_PERIODIC_WORK_NAME = "job_check_company_periodic"

        // Exposed so the UI can observe this run's WorkInfo and know when it's done.
        const val FEED_ONE_TIME_WORK_NAME = "job_check_feed_once"

        /** The 4 job-board aggregators, every 15 minutes (WorkManager's minimum interval). */
        fun scheduleQuickPeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<JobCheckWorker>(15, TimeUnit.MINUTES)
                .setInputData(inputFor(includeCompanyScan = false))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                QUICK_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Direct company-career-page scan (hundreds of requests) — hourly, not every 15 min. */
        fun scheduleCompanyPeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<JobCheckWorker>(1, TimeUnit.HOURS)
                .setInputData(inputFor(includeCompanyScan = true))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                COMPANY_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Pull-to-refresh on the India/Remote tabs. */
        fun runFeedNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<JobCheckWorker>()
                .setInputData(inputFor(includeCompanyScan = false))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(FEED_ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun inputFor(includeCompanyScan: Boolean): Data =
            Data.Builder()
                .putBoolean(KEY_INCLUDE_COMPANY_SCAN, includeCompanyScan)
                .build()
    }
}
