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
import com.jobradar.app.data.discovery.RefreshGroup
import com.jobradar.app.messaging.JobNotifier
import java.util.concurrent.TimeUnit

class JobCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val includeCompanyScan = inputData.getBoolean(KEY_INCLUDE_COMPANY_SCAN, false)
        val group = RefreshGroup.valueOf(inputData.getString(KEY_GROUP) ?: RefreshGroup.ALL.name)
        val repository = (applicationContext as JobRadarApp).jobDiscoveryRepository
        val newJobs = repository.runCheck(includeCompanyScan, group)
        JobNotifier.notifyNewJobs(applicationContext, newJobs)
        return Result.success()
    }

    companion object {
        private const val KEY_INCLUDE_COMPANY_SCAN = "include_company_scan"
        private const val KEY_GROUP = "group"
        private const val QUICK_PERIODIC_WORK_NAME = "job_check_quick_periodic"
        private const val COMPANY_PERIODIC_WORK_NAME = "job_check_company_periodic"

        // Exposed so the UI can observe each specific run's WorkInfo and know when it's done.
        const val BOARDS_ONE_TIME_WORK_NAME = "job_check_boards_once"
        const val TELEGRAM_ONE_TIME_WORK_NAME = "job_check_telegram_once"

        /** Everything, every 15 minutes (WorkManager's minimum interval) — the background heartbeat. */
        fun scheduleQuickPeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<JobCheckWorker>(15, TimeUnit.MINUTES)
                .setInputData(inputFor(includeCompanyScan = false, group = RefreshGroup.ALL))
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
                .setInputData(inputFor(includeCompanyScan = true, group = RefreshGroup.ALL))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                COMPANY_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Pull-to-refresh on the India/Remote tabs — just the 4 job-board aggregators. */
        fun runBoardsNow(context: Context) = enqueueOnce(context, BOARDS_ONE_TIME_WORK_NAME, RefreshGroup.BOARDS)

        /** Pull-to-refresh on the Telegram tab — just the Telegram channels. */
        fun runTelegramNow(context: Context) = enqueueOnce(context, TELEGRAM_ONE_TIME_WORK_NAME, RefreshGroup.TELEGRAM)

        private fun enqueueOnce(context: Context, workName: String, group: RefreshGroup) {
            val request = OneTimeWorkRequestBuilder<JobCheckWorker>()
                .setInputData(inputFor(includeCompanyScan = false, group = group))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        }

        private fun inputFor(includeCompanyScan: Boolean, group: RefreshGroup): Data =
            Data.Builder()
                .putBoolean(KEY_INCLUDE_COMPANY_SCAN, includeCompanyScan)
                .putString(KEY_GROUP, group.name)
                .build()
    }
}
