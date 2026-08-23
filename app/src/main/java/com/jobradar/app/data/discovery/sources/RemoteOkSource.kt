package com.jobradar.app.data.discovery.sources

import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

object RemoteOkSource : JobSource {
    override val name = "remoteok"
    private const val URL = "https://remoteok.com/api"

    override suspend fun fetch(): List<RawJob> = withContext(Dispatchers.IO) {
        // RemoteOK 403s requests without a browser-like User-Agent.
        val request = Request.Builder().url(URL).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                // Index 0 is a legal notice, not a job — it has no "id" field.
                if (!item.has("id")) return@mapNotNull null
                val id = item.optString("id")
                RawJob(
                    source = name,
                    sourceId = id,
                    title = item.optString("position").ifBlank { item.optString("title") },
                    company = item.optString("company"),
                    location = item.optString("location").ifBlank { "Remote" },
                    url = item.optString("url").ifBlank { "https://remoteok.com/remote-jobs/$id" },
                    description = item.optString("description"),
                    isRemote = true,
                    postedAtEpochMillis = parseIsoDateMillis(item.optString("date")),
                )
            }
        }
    }
}
