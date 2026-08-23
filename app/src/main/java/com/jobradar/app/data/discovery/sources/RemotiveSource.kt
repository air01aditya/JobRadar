package com.jobradar.app.data.discovery.sources

import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object RemotiveSource : JobSource {
    override val name = "remotive"
    private const val URL = "https://remotive.com/api/remote-jobs"

    override suspend fun fetch(): List<RawJob> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(URL).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val jobsArray = JSONObject(body).optJSONArray("jobs") ?: return@withContext emptyList()
            (0 until jobsArray.length()).map { i ->
                val item = jobsArray.getJSONObject(i)
                RawJob(
                    source = name,
                    sourceId = item.optString("id"),
                    title = item.optString("title"),
                    company = item.optString("company_name"),
                    location = item.optString("candidate_required_location"),
                    url = item.optString("url"),
                    description = item.optString("description"),
                    isRemote = true,
                    postedAtEpochMillis = parseIsoDateMillis(item.optString("publication_date")),
                )
            }
        }
    }
}
