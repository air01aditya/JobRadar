package com.jobradar.app.data.discovery

import android.content.Context
import com.jobradar.app.data.discovery.sources.AdzunaSource
import com.jobradar.app.data.discovery.sources.CompanyAtsSource
import com.jobradar.app.data.discovery.sources.JobSource
import com.jobradar.app.data.discovery.sources.RemoteOkSource
import com.jobradar.app.data.discovery.sources.RemotiveSource
import com.jobradar.app.data.discovery.sources.TelegramChannelSource
import com.jobradar.app.data.discovery.sources.TelegramHealthMonitor
import com.jobradar.app.data.discovery.sources.WeWorkRemotelySource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

/** Which sources a check should run — lets each Feed tab refresh only what it actually shows. */
enum class RefreshGroup { BOARDS, TELEGRAM, ALL }

class JobDiscoveryRepository(private val dao: JobDiscoveryDao, private val appContext: Context) {

    // Fast job-board aggregators — cheap, shown on the India/Remote tabs.
    private val boardSources: List<JobSource> = listOf(AdzunaSource, RemotiveSource, RemoteOkSource, WeWorkRemotelySource)

    // Direct company career-page scan — hundreds of requests, run on a slower cadence.
    private val companySource: JobSource by lazy { CompanyAtsSource(appContext) }

    fun observeJobs(): Flow<List<JobDiscoveryEntity>> = dao.observeAll()

    /**
     * Fetches the requested source [group] concurrently, filters, dedupes against storage,
     * and returns what's newly inserted. [includeCompanyScan] adds the (much heavier)
     * direct-company-board sweep — only pass true from the slower-cadence worker.
     */
    suspend fun runCheck(includeCompanyScan: Boolean, group: RefreshGroup = RefreshGroup.ALL): List<JobDiscoveryEntity> = coroutineScope {
        val wasEmpty = dao.count() == 0
        val now = System.currentTimeMillis()
        dao.clampFuturePostedDates(now)

        val sources = buildList {
            if (group == RefreshGroup.BOARDS || group == RefreshGroup.ALL) addAll(boardSources)
            if (group == RefreshGroup.TELEGRAM || group == RefreshGroup.ALL) add(TelegramChannelSource)
            if (includeCompanyScan) add(companySource)
        }

        val fetchedPerSource = sources
            .map { source ->
                async {
                    val jobs = runCatching { source.fetch() }
                        .onSuccess { android.util.Log.i("JobDiscovery", "${source.name}: fetched ${it.size}") }
                        .onFailure { android.util.Log.e("JobDiscovery", "${source.name} failed", it) }
                        .getOrElse { emptyList() }
                    source.name to jobs
                }
            }
            .map { it.await() }

        fetchedPerSource.find { it.first == TelegramChannelSource.name }?.let { (_, jobs) ->
            TelegramHealthMonitor.recordResult(appContext, jobs.size)
        }

        val fetched = fetchedPerSource.flatMap { it.second }

        // A job can appear more than once within a single run (e.g. overlapping WWR
        // category feeds) — dedupe within this run before touching the database.
        val seenInRun = HashSet<String>()
        val deduped = fetched.filter { seenInRun.add("${it.source}:${it.sourceId}") }
        val matched = deduped.filter { passesAllFilters(it, now) }

        val existingIds = dao.getAllIds().toHashSet()
        val newEntities = matched.mapNotNull { job ->
            val id = jobId(job)
            if (!existingIds.add(id)) return@mapNotNull null
            JobDiscoveryEntity(
                id = id,
                source = job.source,
                title = job.title,
                company = job.company,
                location = job.location,
                url = job.url,
                isRemote = job.isRemote,
                // Some sources (Adzuna, observed) occasionally report a "posted" timestamp
                // clock-skewed into the future — clamp so a bad timestamp can't permanently
                // sort a stale/broken entry above genuinely-just-posted ones.
                postedAtEpochMillis = job.postedAtEpochMillis?.coerceAtMost(now),
                firstSeenAtEpochMillis = now,
            )
        }

        if (newEntities.isNotEmpty()) dao.insertAll(newEntities)

        // First run ever: the database was empty, so everything looks "new" — store it,
        // but don't fire a notification storm for jobs that were already live before today.
        if (wasEmpty) emptyList() else newEntities
    }

    private fun jobId(job: RawJob): String {
        val raw = "${job.source}:${job.sourceId.ifBlank { job.url }}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
