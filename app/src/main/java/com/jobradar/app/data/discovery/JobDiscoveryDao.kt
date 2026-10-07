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
    // Generous limit: the user's settings filter this list further before it's shown.
    @Query("SELECT * FROM discovered_jobs ORDER BY COALESCE(postedAtEpochMillis, firstSeenAtEpochMillis) DESC LIMIT 1500")
    fun observeAll(): Flow<List<JobDiscoveryEntity>>

    @Query("SELECT COUNT(*) FROM discovered_jobs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<JobDiscoveryEntity>)

    // Self-heals any already-stored rows with a clock-skewed "posted in the future" date
    // (observed from Adzuna) so a bad timestamp can't permanently sit at the top of the sort.
    @Query("UPDATE discovered_jobs SET postedAtEpochMillis = :nowMillis WHERE postedAtEpochMillis > :nowMillis")
    suspend fun clampFuturePostedDates(nowMillis: Long)

    // Saved jobs live in tracked_jobs, so clearing the feed never touches what the user is tracking.
    @Query("DELETE FROM discovered_jobs WHERE COALESCE(postedAtEpochMillis, firstSeenAtEpochMillis) < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("SELECT * FROM discovered_jobs")
    suspend fun getAll(): List<JobDiscoveryEntity>

    @Query("DELETE FROM discovered_jobs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    // Rows saved before descriptions were stored get theirs the next time the job is fetched.
    @Query("UPDATE discovered_jobs SET description = :description WHERE id = :id AND description = ''")
    suspend fun fillMissingDescription(id: String, description: String)
}
