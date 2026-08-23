package com.jobradar.app.data.discovery.sources

import android.content.Context
import com.jobradar.app.data.discovery.JobHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Loads the open, MIT-licensed company->ATS dataset from outscal/OpenJobs (a fork of
 * santifer/career-ops): https://github.com/outscal/OpenJobs — 12k+ companies tagged with
 * the countries they hire in and links to their public Greenhouse/Lever/Ashby job boards.
 * This is what lets us watch companies directly instead of waiting on aggregator lag,
 * without maintaining a manual company list ourselves.
 */
object CompanyDataset {
    private const val DATASET_URL =
        "https://raw.githubusercontent.com/outscal/OpenJobs/main/data/companies_v2.json"
    private const val CACHE_FILE_NAME = "openjobs_companies.json"
    private val CACHE_TTL = TimeUnit.HOURS.toMillis(24)

    suspend fun loadIndiaCompanies(context: Context): List<CompanyBoard> = withContext(Dispatchers.IO) {
        val json = loadDatasetJson(context) ?: return@withContext emptyList()
        runCatching { parseIndiaCompanies(json) }.getOrElse { emptyList() }
    }

    private fun loadDatasetJson(context: Context): String? {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        val cacheFresh = cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) < CACHE_TTL
        if (cacheFresh) return cacheFile.readText()

        val fetched = fetchDataset()
        if (fetched != null) {
            runCatching { cacheFile.writeText(fetched) }
            return fetched
        }
        // Refetch failed — fall back to a stale cache rather than nothing.
        return if (cacheFile.exists()) cacheFile.readText() else null
    }

    private fun fetchDataset(): String? {
        val request = Request.Builder().url(DATASET_URL).header("User-Agent", JobHttpClient.USER_AGENT).build()
        return runCatching {
            JobHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    private fun parseIndiaCompanies(json: String): List<CompanyBoard> {
        val array = JSONArray(json)
        val result = mutableListOf<CompanyBoard>()
        for (i in 0 until array.length()) {
            val company = array.optJSONObject(i) ?: continue
            val countries = company.optJSONArray("countries") ?: continue
            var hasIndia = false
            for (j in 0 until countries.length()) {
                if (countries.optString(j) == "India") { hasIndia = true; break }
            }
            if (!hasIndia) continue

            val name = company.optString("name")
            if (name.isBlank()) continue
            val atsLinks = company.optJSONArray("ats_links") ?: continue
            for (j in 0 until atsLinks.length()) {
                val board = extractBoard(name, atsLinks.optString(j))
                if (board != null) {
                    result.add(board)
                    break
                }
            }
        }
        return result
    }

    private fun extractBoard(name: String, link: String): CompanyBoard? {
        if (link.isBlank()) return null
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        return when {
            link.contains("greenhouse.io") -> extractGreenhouseSlug(uri)?.let { CompanyBoard(name, "greenhouse", it) }
            link.contains("lever.co") -> firstPathSegment(uri)?.let { CompanyBoard(name, "lever", it) }
            link.contains("ashbyhq.com") -> firstPathSegment(uri)?.let { CompanyBoard(name, "ashby", it) }
            link.contains("workable.com") -> firstPathSegment(uri)?.let { CompanyBoard(name, "workable", it) }
            link.contains("smartrecruiters.com") -> firstPathSegment(uri)?.let { CompanyBoard(name, "smartrecruiters", it) }
            link.contains("bamboohr.com") -> subdomain(uri)?.let { CompanyBoard(name, "bamboohr", it) }
            link.contains("recruitee.com") -> subdomain(uri)?.let { CompanyBoard(name, "recruitee", it) }
            link.contains("breezy.hr") -> subdomain(uri)?.let { CompanyBoard(name, "breezy", it) }
            link.contains("myworkdayjobs.com") -> extractWorkdaySlug(uri)?.let { CompanyBoard(name, "workday", it) }
            else -> null
        }
    }

    private fun extractGreenhouseSlug(uri: URI): String? {
        val path = uri.path ?: return null
        // Embed-widget variant: .../embed/job_board?for=slug
        if (path.contains("/embed/job_board")) {
            val query = uri.query ?: return null
            return query.split("&")
                .firstOrNull { it.startsWith("for=") }
                ?.substringAfter("for=")
                ?.takeIf { it.isNotBlank() }
        }
        return firstPathSegment(uri)
    }

    // Workday's public postings API needs the tenant + data-center id (from the host,
    // e.g. "nvidia.wd5.myworkdayjobs.com") and the career-site name (from the first path
    // segment, e.g. "NVIDIAExternalCareerSite") — packed together as "tenant|wdX|site".
    private fun extractWorkdaySlug(uri: URI): String? {
        val hostParts = (uri.host ?: return null).split(".")
        if (hostParts.size < 2) return null
        val tenant = hostParts[0]
        val wdInstance = hostParts[1]
        val site = firstPathSegment(uri) ?: return null
        return "$tenant|$wdInstance|$site"
    }

    private fun firstPathSegment(uri: URI): String? {
        val path = uri.path ?: return null
        return path.trim('/').split("/").firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun subdomain(uri: URI): String? = uri.host?.substringBefore(".")?.takeIf { it.isNotBlank() }
}
