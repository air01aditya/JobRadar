package com.jobradar.app

import android.app.Application
import com.jobradar.app.data.AppDatabase
import com.jobradar.app.data.discovery.JobDiscoveryRepository
import com.jobradar.app.data.tracker.TrackerRepository
import com.jobradar.app.messaging.NotificationChannels
import com.jobradar.app.work.JobCheckWorker

class JobRadarApp : Application() {
    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val trackerRepository: TrackerRepository by lazy { TrackerRepository(database.trackedJobDao()) }
    val jobDiscoveryRepository: JobDiscoveryRepository by lazy { JobDiscoveryRepository(database.jobDiscoveryDao(), this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        JobCheckWorker.scheduleQuickPeriodic(this)
        JobCheckWorker.scheduleCompanyPeriodic(this)
    }
}
