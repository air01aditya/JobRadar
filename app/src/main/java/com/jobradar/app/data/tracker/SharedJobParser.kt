package com.jobradar.app.data.tracker

/** What we could work out from a job shared into JobRadar from another app. Every field is editable before saving. */
data class SharedJob(val title: String, val company: String, val url: String, val sourceLabel: String)

/**
 * Job apps share free text — "Check out this job at Infosys: Java Developer https://…" — with no
 * structured fields, and each app words it differently. This is best-effort: the user always sees
 * and can correct the result before it's saved.
 */
object SharedJobParser {

    private val URL = Regex("https?://\\S+")

    private val KNOWN_SOURCES = listOf(
        "naukri" to "Naukri", "indeed" to "Indeed", "linkedin" to "LinkedIn", "lnkd.in" to "LinkedIn",
        "apna" to "Apna", "internshala" to "Internshala", "foundit" to "foundit", "glassdoor" to "Glassdoor",
    )

    // Sharing boilerplate that is never part of a title or company name.
    private val NOISE = listOf(
        Regex("(?i)check out this job( opening)?"),   // keeps a following "at Company:" for COMPANY_THEN_TITLE
        Regex("(?i)i (found|came across) (this|a) job( on \\w+)?[:!.]?"),
        Regex("(?i)(have a look at|take a look at|look at) this job[:!.]?"),
        Regex("(?i)\\b(apply|view|see)( now| the job| job)?( here)?[:!.]?$"),
        Regex("(?i)\\bshared (via|from) \\w+"),
        Regex("(?i)\\s*[|–—-]\\s*(naukri(\\.com)?|indeed(\\.com)?|linkedin|apna|glassdoor)\\s*$"),
    )

    // "at Infosys: Java Developer" (LinkedIn's wording) — company first, then title.
    private val COMPANY_THEN_TITLE = Regex("^at\\s+(.+?):\\s*(.+)$", RegexOption.IGNORE_CASE)

    // "Java Developer job at Infosys in Pune" / "Java Developer at Infosys".
    private val TITLE_AT_COMPANY = Regex("^(.+?)\\s+(?:job\\s+)?at\\s+(.+?)(?:\\s+in\\s+.+)?$", RegexOption.IGNORE_CASE)

    // "Java Developer - Infosys - Pune" / "Java Developer | Infosys".
    private val TITLE_SEP_COMPANY = Regex("^(.+?)\\s+[|–—-]\\s+(.+?)(?:\\s+[|–—-]\\s+.+)?$")

    fun parse(text: String?, subject: String?): SharedJob? {
        val body = text.orEmpty()
        val url = URL.find(body)?.value?.trimEnd('.', ',', ')', ']')
            ?: URL.find(subject.orEmpty())?.value
            ?: return null
        val source = KNOWN_SOURCES.firstOrNull { url.contains(it.first, ignoreCase = true) }?.second ?: "a link"

        val lines = (body.replace(URL, "\n").lines() + subject.orEmpty().replace(URL, "\n").lines())
            .map(::clean)
            .filter { it.length > 2 }

        for (line in lines) {
            COMPANY_THEN_TITLE.find(line)?.let { return SharedJob(it.groupValues[2].trim(), it.groupValues[1].trim(), url, source) }
        }
        for (line in lines) {
            TITLE_AT_COMPANY.find(line)?.let { return SharedJob(it.groupValues[1].trim(), it.groupValues[2].trim(), url, source) }
            TITLE_SEP_COMPANY.find(line)?.let { return SharedJob(it.groupValues[1].trim(), it.groupValues[2].trim(), url, source) }
        }
        return SharedJob(lines.firstOrNull().orEmpty(), "", url, source)
    }

    private fun clean(line: String): String {
        var result = line.trim()
        NOISE.forEach { result = result.replace(it, "").trim() }
        return result.trim(' ', ':', '-', '|', '!', '.', '–', '—')
    }
}
