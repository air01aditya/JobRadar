package com.jobradar.app.data.discovery.sources

import android.util.Xml
import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseRfc822DateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** No JSON API — parses the public per-category RSS feeds instead (no auth needed). */
object WeWorkRemotelySource : JobSource {
    override val name = "weworkremotely"

    private val feeds = listOf(
        "https://weworkremotely.com/categories/remote-programming-jobs.rss",
        "https://weworkremotely.com/categories/remote-full-stack-programming-jobs.rss",
        "https://weworkremotely.com/categories/remote-back-end-programming-jobs.rss",
        "https://weworkremotely.com/categories/remote-front-end-programming-jobs.rss",
    )

    override suspend fun fetch(): List<RawJob> = coroutineScope {
        feeds
            .map { url -> async(Dispatchers.IO) { runCatching { fetchFeed(url) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
    }

    private fun fetchFeed(url: String): List<RawJob> {
        val request = Request.Builder().url(url).header("User-Agent", JobHttpClient.USER_AGENT).build()
        JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parseRss(body)
        }
    }

    private fun parseRss(xml: String): List<RawJob> {
        val jobs = mutableListOf<RawJob>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inItem = false
        var currentTag: String? = null
        var title = StringBuilder()
        var link = StringBuilder()
        var description = StringBuilder()
        var pubDate = StringBuilder()
        var guid = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        inItem = true
                        title = StringBuilder(); link = StringBuilder(); description = StringBuilder()
                        pubDate = StringBuilder(); guid = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text ?: ""
                        when (currentTag) {
                            "title" -> title.append(text)
                            "link" -> link.append(text)
                            "description" -> description.append(text)
                            "pubDate" -> pubDate.append(text)
                            "guid" -> guid.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        inItem = false
                        val rawTitle = title.toString().trim()
                        val separatorIndex = rawTitle.indexOf(':')
                        val company = if (separatorIndex >= 0) rawTitle.substring(0, separatorIndex).trim() else ""
                        val jobTitle = if (separatorIndex >= 0) rawTitle.substring(separatorIndex + 1).trim() else rawTitle
                        val linkText = link.toString().trim()
                        val id = guid.toString().trim().ifBlank { linkText }
                        jobs.add(
                            RawJob(
                                source = name,
                                sourceId = id,
                                title = jobTitle,
                                company = company,
                                location = "Remote",
                                url = linkText,
                                description = description.toString().trim(),
                                isRemote = true,
                                postedAtEpochMillis = parseRfc822DateMillis(pubDate.toString().trim()),
                            )
                        )
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return jobs
    }
}
