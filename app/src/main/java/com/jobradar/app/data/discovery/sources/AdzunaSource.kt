package com.jobradar.app.data.discovery.sources

import android.content.Context
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
import java.util.concurrent.TimeUnit

/**
 * Adzuna's India endpoint, searched ROLE-first: the title must name a mainstream role
 * ("developer", "software engineer") and the posting must mention a fresher word somewhere.
 * Searching fresher words alone ("trainee", "associate") pulled in niche ERP/consulting roles.
 *
 * Free tier limits: 250 requests/day, 1000/week, 2500/month. queries.size requests per run,
 * at most once per MIN_INTERVAL, keeps us at ~72/day (~2200/month) whatever triggers the run.
 */
class AdzunaSource(private val context: Context) : JobSource {
    override val name = NAME

    // [titleWords]: all must appear in the title. [anyWords]: at least one must appear anywhere.
    private data class Query(val titleWords: String, val itOnly: Boolean, val anyWords: String? = FRESHER_WORDS)

    private val queries = listOf(
        Query("developer", itOnly = false),
        Query("software engineer", itOnly = false),
        Query("analyst", itOnly = true),
        Query("test", itOnly = false),
        Query("fresher", itOnly = true, anyWords = null),
        Query("intern", itOnly = true, anyWords = null),
    )

    override suspend fun fetch(): List<RawJob> = coroutineScope {
        val appId = BuildConfig.ADZUNA_APP_ID
        val appKey = BuildConfig.ADZUNA_APP_KEY
        if (appId.isBlank() || appKey.isBlank()) return@coroutineScope emptyList()
        if (!claimRunSlot()) return@coroutineScope emptyList()

        queries
            .map { query -> async(Dispatchers.IO) { runCatching { fetchQuery(query, appId, appKey) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
    }

    // Records the attempt BEFORE fetching, so a failing or slow run still counts against the budget.
    // Locked so a periodic run and a pull-to-refresh starting together can't both claim the slot.
    private fun claimRunSlot(): Boolean = synchronized(LOCK) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_RUN, 0L)
        if (now - last < MIN_INTERVAL_MILLIS) return false
        prefs.edit().putLong(KEY_LAST_RUN, now).commit()
        true
    }

    private fun fetchQuery(query: Query, appId: String, appKey: String): List<RawJob> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("app_id", appId)
            .addQueryParameter("app_key", appKey)
            .addQueryParameter("results_per_page", "50")
            .addQueryParameter("title_only", query.titleWords)
            .addQueryParameter("max_days_old", "14")
            .addQueryParameter("sort_by", "date")
            .addQueryParameter("content-type", "application/json")
            .apply {
                if (query.itOnly) addQueryParameter("category", "it-jobs")
                query.anyWords?.let { addQueryParameter("what_or", it) }
            }
            .build()

        val request = Request.Builder().url(url).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.w("JobDiscovery", "adzuna '${query.titleWords}' HTTP ${response.code}")
                return emptyList()
            }
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

    companion object {
        const val NAME = "adzuna"
        private const val FRESHER_WORDS = "fresher freshers graduate graduates trainee junior entry intern internship"
        private const val BASE_URL = "https://api.adzuna.com/v1/api/jobs/in/search/1"
        private const val PREFS = "adzuna_budget"
        private const val KEY_LAST_RUN = "last_run_millis"
        private val MIN_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(2)
        private val LOCK = Any()
    }
}
