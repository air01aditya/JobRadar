package com.jobradar.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.jobradar.app.data.AppDatabase
import com.jobradar.app.data.firestore.FeedRepository
import com.jobradar.app.data.tracker.TrackerRepository

class JobRadarApp : Application() {
    val feedRepository: FeedRepository by lazy { FeedRepository(FirebaseFirestore.getInstance()) }
    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val trackerRepository: TrackerRepository by lazy { TrackerRepository(database.trackedJobDao()) }
}
