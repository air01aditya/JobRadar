package com.jobradar.app.data.discovery.sources

import com.jobradar.app.BuildConfig
import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object AdzunaSource : JobSource {
    override val name = "adzuna"
    private const val BASE_URL = "https://api.adzuna.com/v1/api/jobs/in/search"

    // Adzuna paginates via the page number in the URL path (.../search/1, /search/2, ...).
    // Fetching only page 1 silently drops any matching job that scrolled past the newest 50 —
    // pulling a few pages keeps that window from being permanently invisible.
    private const val PAGE_COUNT = 3

    override suspend fun fetch(): List<RawJob> = coroutineScope {
        val appId = BuildConfig.ADZUNA_APP_ID
        val appKey = BuildConfig.ADZUNA_APP_KEY
        if (appId.isBlank() || appKey.isBlank()) return@coroutineScope emptyList()

        (1..PAGE_COUNT)
            .map { page -> async(Dispatchers.IO) { fetchPage(page, appId, appKey) } }
            .awaitAll()
            .flatten()
    }

    private fun fetchPage(page: Int, appId: String, appKey: String): List<RawJob> {
        // Adzuna's "what" param takes one query string, not a keyword OR-list, so we
        // pull the whole IT category sorted by newest and filter client-side instead.
        val url = "$BASE_URL/$page".toHttpUrl().newBuilder()
            .addQueryParameter("app_id", appId)
            .addQueryParameter("app_key", appKey)
            .addQueryParameter("results_per_page", "50")
            .addQueryParameter("content-type", "application/json")
            .addQueryParameter("category", "it-jobs")
            .addQueryParameter("sort_by", "date")
            .build()

        val request = Request.Builder().url(url).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return parseResults(results)
        }
    }

    private fun parseResults(results: JSONArray): List<RawJob> =
        (0 until results.length()).map { i ->
            val item = results.getJSONObject(i)
            val company = item.optJSONObject("company")?.optString("display_name").orEmpty()
            val location = item.optJSONObject("location")?.optString("display_name").orEmpty()
            RawJob(
                source = name,
                sourceId = item.optString("id"),
                title = item.optString("title"),
                company = company,
                location = location,
                url = item.optString("redirect_url"),
                description = item.optString("description"),
                isRemote = false,
                postedAtEpochMillis = parseIsoDateMillis(item.optString("created")),
            )
        }
}
