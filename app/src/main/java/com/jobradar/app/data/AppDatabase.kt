package com.jobradar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jobradar.app.data.discovery.JobDiscoveryDao
import com.jobradar.app.data.discovery.JobDiscoveryEntity

@Database(entities = [TrackedJobEntity::class, JobDiscoveryEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackedJobDao(): TrackedJobDao
    abstract fun jobDiscoveryDao(): JobDiscoveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE discovered_jobs ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        // No destructive fallback: a missing migration should fail loudly in development,
        // not silently wipe the user's tracked applications on their phone.
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jobradar.db",
                ).addMigrations(MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
