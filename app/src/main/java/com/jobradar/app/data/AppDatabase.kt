package com.jobradar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jobradar.app.data.discovery.JobDiscoveryDao
import com.jobradar.app.data.discovery.JobDiscoveryEntity

@Database(entities = [TrackedJobEntity::class, JobDiscoveryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackedJobDao(): TrackedJobDao
    abstract fun jobDiscoveryDao(): JobDiscoveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jobradar.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
