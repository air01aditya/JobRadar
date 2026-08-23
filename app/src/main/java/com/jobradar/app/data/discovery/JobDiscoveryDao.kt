package com.jobradar.app.data.discovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDiscoveryDao {
    // Sort by the job's real posted date when we know it (truest signal of "actually new"),
    // falling back to when we discovered it for sources that don't report one.
    @Query("SELECT * FROM discovered_jobs ORDER BY COALESCE(postedAtEpochMillis, firstSeenAtEpochMillis) DESC LIMIT 300")
    fun observeAll(): Flow<List<JobDiscoveryEntity>>

    @Query("SELECT id FROM discovered_jobs")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM discovered_jobs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<JobDiscoveryEntity>)

    // Self-heals any already-stored rows with a clock-skewed "posted in the future" date
    // (observed from Adzuna) so a bad timestamp can't permanently sit at the top of the sort.
    @Query("UPDATE discovered_jobs SET postedAtEpochMillis = :nowMillis WHERE postedAtEpochMillis > :nowMillis")
    suspend fun clampFuturePostedDates(nowMillis: Long)
}
