package com.jobradar.app.data.discovery.sources

import android.content.Context
import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import com.jobradar.app.data.discovery.parseWorkdayPostedOn
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
 * Used twice: the hand-checked [PriorityEmployers] (every 15 min) and the broad
 * [CompanyDataset] (hourly).
 */
class CompanyAtsSource(
    private val context: Context,
    override val name: String,
    private val loadBoards: suspend (Context) -> List<CompanyBoard>,
) : JobSource {

    override suspend fun fetch(): List<RawJob> = coroutineScope {
        loadBoards(context)
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
                // updated_at changes on any edit, making months-old postings look new; first_published is the real post date.
                postedAtEpochMillis = parseIsoDateMillis(item.optString("first_published").ifBlank { item.optString("updated_at") }),
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
        // Without these the API returns its default 10 postings worldwide — Bosch has 500+ in India.
        val body = get("https://api.smartrecruiters.com/v1/companies/${company.slug}/postings?country=in&limit=100")
            ?: return emptyList()
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
    // Without an India filter, a global company's newest 20 jobs are mostly outside India.
    private fun fetchWorkday(company: CompanyBoard): List<RawJob> {
        val parts = company.slug.split("|")
        if (parts.size != 3) return emptyList()
        val (tenant, wdInstance, site) = parts
        val base = "https://$tenant.$wdInstance.myworkdayjobs.com"
        val endpoint = "$base/wday/cxs/$tenant/$site/jobs"
        val indiaFacet = workdayIndiaFacet(endpoint, "$tenant|$site")

        val request = JSONObject()
            .put("appliedFacets", JSONObject().apply { indiaFacet?.let { put(it.first, JSONArray().put(it.second)) } })
            .put("limit", 20)
            .put("offset", 0)
            // Sites with no country filter still narrow well on a text search for "India".
            .put("searchText", if (indiaFacet == null) "India" else "")
        val body = postJson(endpoint, request.toString()) ?: return emptyList()
        val jobs = JSONObject(body).optJSONArray("jobPostings") ?: return emptyList()
        val now = System.currentTimeMillis()
        return (0 until jobs.length()).map { i ->
            val item = jobs.getJSONObject(i)
            val rawLocation = item.optString("locationsText")
            // "2 Locations" says nothing about the country, but the India filter already guaranteed it.
            val location = if (indiaFacet != null && !rawLocation.contains("india", ignoreCase = true)) {
                "$rawLocation, India"
            } else {
                rawLocation
            }
            val externalPath = item.optString("externalPath")
            RawJob(
                source = company.companyName,
                sourceId = externalPath,
                title = item.optString("title"),
                company = company.companyName,
                location = location,
                url = "$base/$site$externalPath",
                description = "",
                isRemote = rawLocation.contains("remote", ignoreCase = true),
                postedAtEpochMillis = parseWorkdayPostedOn(item.optString("postedOn"), now),
            )
        }
    }

    /**
     * Each Workday site names its country filter differently ("locationCountry", "Location_Country",
     * "locationHierarchy1", ...), so ask the site once which filter has an "India" option and cache
     * the answer for a week. Returns (filterName, optionId), or null if the site has no such filter.
     */
    private fun workdayIndiaFacet(endpoint: String, cacheKey: String): Pair<String, String>? {
        val prefs = context.getSharedPreferences(WORKDAY_FACET_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(cacheKey, null)?.split("|")
        val now = System.currentTimeMillis()
        if (cached != null && cached.size == 3 && now - (cached[2].toLongOrNull() ?: 0) < WORKDAY_FACET_TTL) {
            return if (cached[0].isEmpty()) null else cached[0] to cached[1]
        }

        val body = postJson(endpoint, """{"appliedFacets":{},"limit":1,"offset":0,"searchText":""}""") ?: return null
        val facet = runCatching { findIndiaFacet(JSONObject(body).optJSONArray("facets"), parentParameter = null) }.getOrNull()
        prefs.edit().putString(cacheKey, "${facet?.first.orEmpty()}|${facet?.second.orEmpty()}|$now").apply()
        return facet
    }

    // Facets can nest: a "Locations" group whose values are themselves facets with their own options.
    private fun findIndiaFacet(facets: JSONArray?, parentParameter: String?): Pair<String, String>? {
        if (facets == null) return null
        for (i in 0 until facets.length()) {
            val facet = facets.optJSONObject(i) ?: continue
            val parameter = facet.optString("facetParameter").ifBlank { parentParameter } ?: continue
            val values = facet.optJSONArray("values") ?: continue
            for (j in 0 until values.length()) {
                val value = values.optJSONObject(j) ?: continue
                if (value.optString("descriptor").trim().equals("India", ignoreCase = true) && value.optString("id").isNotBlank()) {
                    return parameter to value.optString("id")
                }
                if (value.has("values")) findIndiaFacet(JSONArray().put(value), parameter)?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val WORKDAY_FACET_PREFS = "workday_india_facets"
        val WORKDAY_FACET_TTL = java.util.concurrent.TimeUnit.DAYS.toMillis(7)
    }
}
