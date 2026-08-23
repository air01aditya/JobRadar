package com.jobradar.app.data.discovery.sources

import com.jobradar.app.data.discovery.JobHttpClient
import com.jobradar.app.data.discovery.RawJob
import com.jobradar.app.data.discovery.parseIsoDateMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

/**
 * Reads the public, no-login preview page (t.me/s/<channel>) of a handful of well-known
 * India fresher/off-campus job-alert channels — same legal footing as reading a public RSS
 * feed. These channels are exactly what the fast Instagram/Telegram "apply now" pages read
 * from themselves; we're just automating that same read.
 */
object TelegramChannelSource : JobSource {
    override val name = "telegram"

    // (url slug, friendly display name shown in the source badge)
    private val CHANNELS = listOf(
        "fresheroffcampus" to "FresherOffCampus",
        "offcampusjobs4u" to "OffCampusJobs4u",
        "fresherjobsadda" to "Fresher Jobs Adda",
        "freshopenings" to "Fresher Job Openings",
        "jobformorepune" to "Pune Jobs",
        "freshershunt" to "Freshershunt",
        "vibrantmindsitjobs" to "VibrantMinds IT Jobs",
        "FreshersJobsUpdates" to "Freshers Jobs Updates",
    )

    // Cross-promo links channels always include ("follow our WhatsApp/Telegram too") — never the apply link.
    private val NON_APPLY_LINK_HOSTS = listOf("t.me/", "telegram.me/", "whatsapp.com/", "chat.whatsapp.com")

    override suspend fun fetch(): List<RawJob> = coroutineScope {
        CHANNELS
            .map { (slug, displayName) ->
                async(Dispatchers.IO) { runCatching { fetchChannel(slug, displayName) }.getOrElse { emptyList() } }
            }
            .awaitAll()
            .flatten()
    }

    private fun fetchChannel(slug: String, displayName: String): List<RawJob> {
        val request = Request.Builder()
            .url("https://t.me/s/$slug")
            .header("User-Agent", JobHttpClient.USER_AGENT)
            .build()
        val body = JobHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string()
        } ?: return emptyList()

        val doc = Jsoup.parse(body, "https://t.me/s/$slug")
        val messages = doc.select("div.tgme_widget_message[data-post]")

        return messages.mapNotNull { message ->
            val postId = message.attr("data-post").ifBlank { return@mapNotNull null }
            val textDiv = message.selectFirst("div.tgme_widget_message_text") ?: return@mapNotNull null

            // Preserve line breaks (Jsoup's normal text() collapses them) so we can take a clean first line as title.
            textDiv.select("br").forEach { it.replaceWith(TextNode("\n")) }
            val fullText = textDiv.wholeText().trim()
            if (fullText.isBlank()) return@mapNotNull null

            val title = fullText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val isRemote = fullText.contains("remote", ignoreCase = true) || fullText.contains(" wfh", ignoreCase = true)

            val applyUrl = textDiv.select("a[href]")
                .map { it.attr("abs:href") }
                .firstOrNull { href -> NON_APPLY_LINK_HOSTS.none { host -> href.contains(host, ignoreCase = true) } }
                ?: "https://t.me/$postId" // fall back to the post itself only if no real link was found

            val postedAt = message.selectFirst("time[datetime]")?.attr("datetime")

            RawJob(
                source = "Telegram · $displayName",
                sourceId = postId,
                title = title,
                company = "",
                location = if (isRemote) "Remote" else "India",
                url = applyUrl,
                description = fullText,
                isRemote = isRemote,
                postedAtEpochMillis = parseIsoDateMillis(postedAt),
            )
        }
    }
}
