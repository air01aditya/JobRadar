package com.jobradar.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedJobDao {
    @Query("SELECT * FROM tracked_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrackedJobEntity>>

    @Query("SELECT * FROM tracked_jobs WHERE id = :id")
    suspend fun getById(id: String): TrackedJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: TrackedJobEntity)

    @Query("UPDATE tracked_jobs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE tracked_jobs SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String)

    @Query("UPDATE tracked_jobs SET deadlineAt = :deadlineAt WHERE id = :id")
    suspend fun updateDeadline(id: String, deadlineAt: Long?)

    @Delete
    suspend fun delete(job: TrackedJobEntity)
}
