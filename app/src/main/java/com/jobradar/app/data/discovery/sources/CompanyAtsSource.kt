package com.jobradar.app.data.discovery.sources

import android.content.Context
import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Watches company career pages directly via their public ATS APIs — as fast as the
 * recruiter clicking publish, instead of waiting on aggregator re-indexing lag.
 * Company universe comes from [CompanyDataset] (India-tagged companies only).
 */
class CompanyAtsSource(private val context: Context) : JobSource {
    override val name = "company-ats"

    override suspend fun fetch(): List<RawJob> = coroutineScope {
        val companies = CompanyDataset.loadIndiaCompanies(context)
        companies
            .map { company -> async { runCatching { fetchBoard(company) }.getOrElse { emptyList() } } }
            .awaitAll()
            .flatten()
    }

    private suspend fun fetchBoard(company: CompanyBoard): List<RawJob> = withContext(Dispatchers.IO) {
        when (company.atsType) {
            "greenhouse" -> fetchGreenhouse(company)
            "lever" -> fetchLever(company)
            "ashby" -> fetchAshby(company)
            "workable" -> fetchWorkable(company)
            "smartrecruiters" -> fetchSmartRecruiters(company)
            "bamboohr" -> fetchBambooHr(company)
            "recruitee" -> fetchRecruitee(company)
            "breezy" -> fetchBreezy(company)
            "workday" -> fetchWorkday(company)
            else -> emptyList()
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun postJson(url: String, jsonBody: String): String? {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun fetchGreenhouse(company: CompanyBoard): List<RawJob> {
        val body = get("https://boards-api.greenhouse.io/v1/boards/${company.slug}/jobs") ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val location = item.optJSONObject("location")?.optString("name").orEmpty()
            RawJob(
                source = company.companyName,
                sourceId = item.optString("id"),
                title = item.optString("title"),
                company = company.companyName,
                location = location,
                url = item.optString("absolute_url"),
                description = "",
                isRemote = location.contains("remote", ignoreCase = true),
                postedAtEpochMillis = parseIsoDateMillis(item.optString("updated_at")),
            )
        }
    }

    private fun fetchLever(company: CompanyBoard): List<RawJob> {
        val body = get("https://api.lever.co/v0/postings/${company.slug}?mode=json") ?: return emptyList()
        val jobs = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val location = item.optJSONObject("categories")?.optString("location").orEmpty()
            RawJob(
                source = company.companyName,
                sourceId = item.optString("id"),
                title = item.optString("text"),
                company = company.companyName,
                location = location,
                url = item.optString("hostedUrl"),
                description = item.optString("descriptionPlain"),
                isRemote = location.contains("remote", ignoreCase = true),
                postedAtEpochMillis = item.optLong("createdAt").takeIf { it > 0 },
            )
        }
    }

    private fun fetchAshby(company: CompanyBoard): List<RawJob> {
        val body = get("https://api.ashbyhq.com/posting-api/job-board/${company.slug}") ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val location = item.optString("location")
            RawJob(
                source = company.companyName,
                sourceId = item.optString("id"),
                title = item.optString("title"),
                company = company.companyName,
                location = location,
                url = item.optString("jobUrl"),
                description = "",
                isRemote = item.optBoolean("isRemote", false) || location.contains("remote", ignoreCase = true),
                postedAtEpochMillis = parseIsoDateMillis(item.optString("publishedAt")),
            )
        }
    }

    private fun fetchWorkable(company: CompanyBoard): List<RawJob> {
        val body = get("https://apply.workable.com/api/v1/widget/accounts/${company.slug}") ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val location = listOf(item.optString("city"), item.optString("state"), item.optString("country"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            RawJob(
                source = company.companyName,
                sourceId = item.optString("shortcode"),
                title = item.optString("title"),
                company = company.companyName,
                location = location,
                url = item.optString("application_url").ifBlank { item.optString("url") },
                description = "",
                isRemote = item.optBoolean("telecommuting", false) || location.contains("remote", ignoreCase = true),
                postedAtEpochMillis = parseIsoDateMillis(item.optString("published_on").ifBlank { item.optString("created_at") }),
            )
        }
    }

    private fun fetchSmartRecruiters(company: CompanyBoard): List<RawJob> {
        val body = get("https://api.smartrecruiters.com/v1/companies/${company.slug}/postings") ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("content") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val loc = item.optJSONObject("location")
            val location = listOfNotNull(loc?.optString("city"), loc?.optString("region"), loc?.optString("country"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            val id = item.optString("id")
            RawJob(
                source = company.companyName,
                sourceId = id,
                title = item.optString("name"),
                company = company.companyName,
                location = location,
                url = "https://jobs.smartrecruiters.com/${company.slug}/$id",
                description = "",
                isRemote = loc?.optBoolean("remote", false) ?: false,
                postedAtEpochMillis = parseIsoDateMillis(item.optString("releasedDate")),
            )
        }
    }

    private fun fetchBambooHr(company: CompanyBoard): List<RawJob> {
        val body = get("https://${company.slug}.bamboohr.com/careers/list") ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("result") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val loc = item.optJSONObject("location")
            val location = listOfNotNull(loc?.optString("city"), loc?.optString("state"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            val id = item.optString("id")
            RawJob(
                source = company.companyName,
                sourceId = id,
                title = item.optString("jobOpeningName"),
                company = company.companyName,
                location = location,
                url = "https://${company.slug}.bamboohr.com/careers/$id",
                description = "",
                isRemote = item.optBoolean("isRemote", false) || location.contains("remote", ignoreCase = true),
                postedAtEpochMillis = null, // BambooHR's list endpoint doesn't report a posted date
            )
        }
    }

    private fun fetchRecruitee(company: CompanyBoard): List<RawJob> {
        val body = get("https://${company.slug}.recruitee.com/api/offers/") ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("offers") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val location = listOf(item.optString("city"), item.optString("country"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            RawJob(
                source = company.companyName,
                sourceId = item.optString("id"),
                title = item.optString("title"),
                company = company.companyName,
                location = location,
                url = item.optString("careers_url"),
                description = "",
                isRemote = item.optBoolean("remote", false),
                postedAtEpochMillis = parseIsoDateMillis(item.optString("published_at")),
            )
        }
    }

    private fun fetchBreezy(company: CompanyBoard): List<RawJob> {
        val body = get("https://${company.slug}.breezy.hr/json") ?: return emptyList()
        val jobs = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val loc = item.optJSONObject("location")
            val location = listOfNotNull(loc?.optString("city"), loc?.optJSONObject("country")?.optString("name"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            RawJob(
                source = company.companyName,
                sourceId = item.optString("id"),
                title = item.optString("name"),
                company = company.companyName,
                location = location,
                url = item.optString("url"),
                description = "",
                isRemote = loc?.optBoolean("is_remote", false) ?: false,
                postedAtEpochMillis = parseIsoDateMillis(item.optString("published_date")),
            )
        }
    }

    // Workday's slug is packed as "tenant|wdInstance|siteName" (see CompanyDataset.extractWorkdaySlug).
    private fun fetchWorkday(company: CompanyBoard): List<RawJob> {
        val parts = company.slug.split("|")
        if (parts.size != 3) return emptyList()
        val (tenant, wdInstance, site) = parts
        val base = "https://$tenant.$wdInstance.myworkdayjobs.com"
        val body = postJson(
            "$base/wday/cxs/$tenant/$site/jobs",
            """{"appliedFacets":{},"limit":20,"offset":0,"searchText":""}""",
        ) ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("jobPostings") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val location = item.optString("locationsText")
            val externalPath = item.optString("externalPath")
            RawJob(
                source = company.companyName,
                sourceId = externalPath,
                title = item.optString("title"),
                company = company.companyName,
                location = location,
                url = "$base/$site$externalPath",
                description = "",
                isRemote = location.contains("remote", ignoreCase = true),
                postedAtEpochMillis = null, // Workday reports relative text ("Posted Today"), not a real timestamp
            )
        }
    }
}
