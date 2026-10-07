package com.jobradar.app.data.discovery

import android.content.Context
import android.util.Log
import com.jobradar.app.data.discovery.sources.AdzunaSource
import com.jobradar.app.data.discovery.sources.CompanyAtsSource
import com.jobradar.app.data.discovery.sources.JobSource
import com.jobradar.app.data.discovery.sources.RemoteOkSource
import com.jobradar.app.data.discovery.sources.RemotiveSource
import com.jobradar.app.data.discovery.sources.WeWorkRemotelySource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class JobDiscoveryRepository(private val dao: JobDiscoveryDao, private val appContext: Context) {

    // Fast job-board aggregators — cheap, shown on the India/Remote tabs.
    private val boardSources: List<JobSource> =
        listOf(AdzunaSource(appContext), RemotiveSource, RemoteOkSource, WeWorkRemotelySource)

    // Direct company career-page scan — hundreds of requests, run on a slower cadence.
    private val companySource: JobSource by lazy { CompanyAtsSource(appContext) }

    // Periodic runs and pull-to-refresh can start at the same moment. Without this, both read
    // "which ids exist" before either writes, both decide the same job is new, and both notify.
    private val checkLock = Mutex()

    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun observeJobs(): Flow<List<JobDiscoveryEntity>> = dao.observeAll()

    /**
     * Drops jobs that the current rules wouldn't accept: everything saved under an older
     * FILTERS_VERSION, and anything past MAX_POSTING_AGE. Safe to call on every app open.
     */
    suspend fun cleanUp() = checkLock.withLock { cleanUpLocked(System.currentTimeMillis()) }

    private suspend fun cleanUpLocked(now: Long) {
        // Installs from before this flag existed already did their silent first sync.
        if (!prefs.getBoolean(KEY_INITIAL_SYNC_DONE, false) && dao.count() > 0) {
            prefs.edit().putBoolean(KEY_INITIAL_SYNC_DONE, true).apply()
        }
        if (prefs.getInt(KEY_FILTERS_VERSION, 0) != FILTERS_VERSION) {
            val rejected = dao.getAll().filter { rejectedByTitle(it.asRawJob()) }.map { it.id }
            if (rejected.isNotEmpty()) dao.deleteByIds(rejected)
            prefs.edit().putInt(KEY_FILTERS_VERSION, FILTERS_VERSION).apply()
            Log.i(TAG, "filters changed to v$FILTERS_VERSION — removed ${rejected.size} saved jobs the new rules reject")
        }
        dao.clampFuturePostedDates(now)
        val removed = dao.deleteOlderThan(now - MAX_POSTING_AGE_MILLIS)
        if (removed > 0) Log.i(TAG, "removed $removed jobs older than the freshness window")
    }

    /**
     * Fetches the board sources concurrently, filters, dedupes against storage, and returns
     * what's newly inserted. [includeCompanyScan] adds the (much heavier) direct-company-board
     * sweep — only pass true from the slower-cadence worker.
     */
    suspend fun runCheck(includeCompanyScan: Boolean): List<JobDiscoveryEntity> = checkLock.withLock {
        val now = System.currentTimeMillis()
        cleanUpLocked(now)

        val sources = buildList {
            addAll(boardSources)
            if (includeCompanyScan) add(companySource)
        }

        val fetched = coroutineScope {
            sources
                .map { source ->
                    async {
                        runCatching { source.fetch() }
                            .onSuccess { Log.i(TAG, "${source.name}: fetched ${it.size}") }
                            .onFailure { Log.e(TAG, "${source.name} failed", it) }
                            .getOrElse { emptyList() }
                    }
                }
                .flatMap { it.await() }
        }

        // A job can appear more than once within a single run (e.g. two Adzuna title searches
        // both matching "Junior Trainee Developer") — dedupe before touching the database.
        val seenInRun = HashSet<String>()
        val deduped = fetched.filter { seenInRun.add("${it.source}:${it.sourceId}") }
        val matched = deduped.filter { passesAllFilters(it, now) }

        val existing = dao.getAll()
        val existingIds = existing.mapTo(HashSet()) { it.id }
        // The same posting often comes back under a new id (reposts, or two sources listing it).
        val existingIdentities = existing.mapTo(HashSet()) { identity(it.title, it.company, it.location) }
        val newEntities = matched.mapNotNull { job ->
            val id = jobId(job)
            if (!existingIds.add(id)) return@mapNotNull null
            if (!existingIdentities.add(identity(job.title, job.company, job.location))) return@mapNotNull null
            JobDiscoveryEntity(
                id = id,
                source = job.source,
                title = job.title,
                company = job.company,
                location = job.location,
                url = job.url,
                isRemote = job.isRemote,
                // Some sources (Adzuna, observed) occasionally report a "posted" timestamp
                // clock-skewed into the future — clamp so it can't sit at the top forever.
                postedAtEpochMillis = job.postedAtEpochMillis?.coerceAtMost(now),
                firstSeenAtEpochMillis = now,
            )
        }

        if (newEntities.isNotEmpty()) dao.insertAll(newEntities)
        Log.i(
            TAG,
            "check done: fetched=${fetched.size} unique=${deduped.size} passedFilters=${matched.size} new=${newEntities.size}",
        )

        // Only the very first check after install stays silent: everything looks "new" then, and
        // those jobs were live before the app existed. An empty feed later (after a cleanup) still notifies.
        if (!prefs.getBoolean(KEY_INITIAL_SYNC_DONE, false)) {
            prefs.edit().putBoolean(KEY_INITIAL_SYNC_DONE, true).apply()
            emptyList()
        } else {
            newEntities
        }
    }

    private fun identity(title: String, company: String, location: String): String =
        listOf(title, company, location).joinToString("|") { it.lowercase().filter(Char::isLetterOrDigit) }

    private fun JobDiscoveryEntity.asRawJob() = RawJob(
        source = source, sourceId = id, title = title, company = company, location = location,
        url = url, description = "", isRemote = isRemote, postedAtEpochMillis = postedAtEpochMillis,
    )

    private fun jobId(job: RawJob): String {
        val raw = "${job.source}:${job.sourceId.ifBlank { job.url }}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private companion object {
        const val TAG = "JobDiscovery"
        const val PREFS = "job_discovery"
        const val KEY_FILTERS_VERSION = "filters_version"
        const val KEY_INITIAL_SYNC_DONE = "initial_sync_done"
    }
}
