package com.jobradar.app.data.tracker

import com.jobradar.app.data.TrackedJobDao
import com.jobradar.app.data.TrackedJobEntity
import com.jobradar.app.data.TrackedStatus
import com.jobradar.app.data.discovery.JobDiscoveryEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class TrackerRepository(private val dao: TrackedJobDao) {

    fun observeTrackedJobs(): Flow<List<TrackedJobEntity>> = dao.observeAll()

    suspend fun addFromFeed(job: JobDiscoveryEntity) {
        dao.upsert(
            TrackedJobEntity(
                id = job.id,
                title = job.title,
                company = job.company,
                url = job.url,
                status = TrackedStatus.SAVED.name,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun addManual(title: String, company: String, url: String) {
        dao.upsert(
            TrackedJobEntity(
                id = manualJobId(url),
                title = title,
                company = company,
                url = url,
                status = TrackedStatus.SAVED.name,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateStatus(id: String, status: TrackedStatus) = dao.updateStatus(id, status.name)

    suspend fun updateNotes(id: String, notes: String) = dao.updateNotes(id, notes)

    suspend fun updateDeadline(id: String, deadlineAt: Long?) = dao.updateDeadline(id, deadlineAt)

    suspend fun delete(job: TrackedJobEntity) = dao.delete(job)

    suspend fun getById(id: String): TrackedJobEntity? = dao.getById(id)

    private fun manualJobId(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("manual:$url".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
