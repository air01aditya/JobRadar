package com.jobradar.app.data.discovery.sources

import com.jobradar.app.BuildConfig
import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object AdzunaSource : JobSource {
    override val name = "adzuna"
    private const val BASE_URL = "https://api.adzuna.com/v1/api/jobs/in/search/1"

    override suspend fun fetch(): List<RawJob> = withContext(Dispatchers.IO) {
        val appId = BuildConfig.ADZUNA_APP_ID
        val appKey = BuildConfig.ADZUNA_APP_KEY
        if (appId.isBlank() || appKey.isBlank()) return@withContext emptyList()

        // Adzuna's "what" param takes one query string, not a keyword OR-list, so we
        // pull the whole IT category sorted by newest and filter client-side instead.
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("app_id", appId)
            .addQueryParameter("app_key", appKey)
            .addQueryParameter("results_per_page", "50")
            .addQueryParameter("content-type", "application/json")
            .addQueryParameter("category", "it-jobs")
            .addQueryParameter("sort_by", "date")
            .build()

        val request = Request.Builder().url(url).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList<RawJob>()
            parseResults(results)
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
