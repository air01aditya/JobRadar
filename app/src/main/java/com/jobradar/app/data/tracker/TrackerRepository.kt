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
        addIfAbsent(TrackedJobEntity(job.id, job.title, job.company, job.url, TrackedStatus.SAVED.name, createdAt = now()))
    }

    suspend fun addManual(title: String, company: String, url: String, notes: String = "") {
        addIfAbsent(TrackedJobEntity(manualJobId(url), title, company, url, TrackedStatus.SAVED.name, notes, createdAt = now()))
    }

    // Re-adding a job (shared twice, or "Yes, track it" on one already tracked) must not reset
    // its status, notes or deadline back to a fresh "Saved".
    private suspend fun addIfAbsent(job: TrackedJobEntity) {
        if (dao.getById(job.id) == null) dao.upsert(job)
    }

    private fun now() = System.currentTimeMillis()

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
