package com.jobradar.app.data.discovery

import java.util.concurrent.TimeUnit

/*
 * Filtering happens in two stages:
 *
 *   SAVE stage  — [acceptForStorage], fixed rules, run once when a job is fetched:
 *     role (title is a tech role), not senior by title, India or India-friendly remote,
 *     posted within MAX_STORED_AGE. Broad on purpose, so loosening a setting later has jobs to show.
 *
 *   SHOW stage  — [matchesPreferences], the user's FilterSettings, run every time the feed is drawn:
 *     experience asked, entry-level proof, freshness window, remote on/off, cities, blocked words.
 *     Changing a setting is instant because nothing has to be re-fetched.
 *
 * Bump FILTERS_VERSION when the SAVE rules change; saved jobs are then re-checked with [rejectedForStorage].
 */
const val FILTERS_VERSION = 5

/** The widest freshness window the user can pick; anything older is deleted. */
val MAX_STORED_AGE_MILLIS = TimeUnit.DAYS.toMillis(FilterSettings.MAX_AGE_OPTIONS.max().toLong())

// ---- 1. Role -------------------------------------------------------------------------------

private val ROLE_KEYWORDS = listOf(
    "developer", "programmer", "software", "sde", "sdet", "swe",
    "full stack", "full-stack", "fullstack", "frontend", "front-end", "front end",
    "backend", "back-end", "back end", "web development",
    "android", "ios", "flutter", "react native", "mobile app",
    "java", "python", ".net", "dotnet", "javascript", "node.js", "nodejs", "react", "angular", "php",
    "qa", "quality assurance", "test engineer", "tester", "testing", "test analyst", "automation engineer",
    "devops", "cloud engineer", "site reliability", "sre", "platform engineer",
    "support engineer", "technical support", "application support", "production support",
    "it support", "desktop support", "it trainee", "technical trainee", "it analyst", "it engineer",
    "implementation engineer", "solutions engineer", "solution engineer",
    "salesforce", "servicenow", "sap", "erp", "netsuite", "odoo",
    "data analyst", "business analyst", "systems analyst", "system analyst", "product analyst",
    "mis analyst", "reporting analyst", "bi analyst", "power bi",
    "soc analyst", "security analyst", "cyber security", "cybersecurity",
    "graduate engineer trainee", "engineer trainee", "associate engineer", "member of technical staff",
)

// Titles that contain a tech word but are not tech jobs we want (or are outside the agreed scope).
private val ROLE_EXCLUDE_KEYWORDS = listOf(
    "sales", "marketing", "business development", "bde", "recruit", "talent acquisition", "hr",
    "human resource", "accountant", "finance", "content writer", "copywriter", "graphic design",
    "teacher", "tutor", "trainer", "faculty", "instructor",
    "mechanical", "civil", "electrical", "chemical", "automobile", "marine", "manufacturing",
    "design", "diploma", "production engineer",
    // Paid courses dressed up as fresher jobs — a common trap for freshers in India.
    // (Not plain "training" — real jobs say "on-job training".)
    "freshers training", "training program", "training programme", "training course", "paid training",
    "course", "certification", "simulated", "placement",
    "data scientist", "machine learning", "ml engineer", "data engineer",
)

// A phone number in the title ("Fresher Developer 99.89.61.27.35") is a spam/agency listing, not a job.
private val PHONE_NUMBER_IN_TITLE = Regex("\\d[\\d\\s.\\-]{7,}\\d")

fun matchesRole(job: RawJob, extraRoles: List<String> = emptyList()): Boolean {
    val title = job.title.lowercase()
    if (PHONE_NUMBER_IN_TITLE.containsMatchIn(title)) return false
    // A role the user added explicitly wins over the default exclusions ("salesforce marketing developer").
    if (extraRoles.any { containsWord(title, it) }) return true
    if (ROLE_EXCLUDE_KEYWORDS.any { containsWord(title, it) }) return false
    return ROLE_KEYWORDS.any { containsWord(title, it) }
}

// ---- 2. Seniority ---------------------------------------------------------------------------

private val SENIOR_TITLE_KEYWORDS = listOf(
    "senior", "sr", "staff", "principal", "lead", "director", "head of", "manager", "architect",
    "vp", "vice president", "experienced", "advanced", "mid level", "mid-level", "expert",
)

// "Software Engineer II", "Engr III", "SDE 2", "QA Engineer 2", "Level 4", "L5".
private val SENIOR_LEVEL_PATTERNS = listOf(
    Regex("\\b(ii|iii|iv|v)\\b"),
    Regex("\\b(engineer|engr|developer|analyst|sde|swe|sdet|programmer|tester|consultant)[\\s-]*[2-9]\\b"),
    Regex("\\blevel[\\s-]*[3-9]\\b"),
    Regex("\\bl[5-7]\\b"),
)

private const val NUM = "(?<![\\d.])(\\d{1,2})"
private const val YEARS = "(?:years?|yrs?)\\b"

// Each pattern's first group is the LOWER bound of the experience asked for.
private val EXPERIENCE_PATTERNS = listOf(
    Regex("$NUM\\s*(?:\\+|-|–|—|to)\\s*(?:\\d{1,2})?\\s*\\+?\\s*$YEARS"),                       // 3-5 years, 4+ yrs, 2 to 4 years
    Regex("\\b(?:experience|exp)[\\s:–\\-]{0,4}(?:of\\s+)?(?:minimum\\s+|min\\.?\\s+|at least\\s+)?$NUM\\s*\\+?\\s*$YEARS"), // Experience: 3 years
    Regex("$NUM\\s*\\+?\\s*$YEARS\\s+(?:of\\s+)?(?:[a-z/#+.\\-]+\\s+){0,4}?experience"),         // 3 years of hands-on Java experience
    Regex("(?:minimum|at least|min\\.?)\\s+(?:of\\s+)?$NUM\\s*\\+?\\s*$YEARS"),                  // minimum 3 years
)

// Bigger numbers are almost always the company bragging ("25+ years of industry experience"),
// not a requirement — and genuinely senior roles are already caught by their titles.
private const val MAX_PLAUSIBLE_REQUIREMENT_YEARS = 12

private val NOT_FOR_FRESHERS = listOf(
    "no freshers", "not for freshers", "freshers need not", "freshers will not", "freshers are not",
    "not an entry level", "not an entry-level", "not entry level", "experienced candidates only", "experienced only",
)

private fun experienceLowerBounds(text: String): List<Int> =
    EXPERIENCE_PATTERNS
        .flatMap { pattern -> pattern.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() } }
        .filter { it <= MAX_PLAUSIBLE_REQUIREMENT_YEARS }

/** Senior by title alone — "Senior", "Lead", "Engineer II", "SDE 3". Not a user setting. */
fun isSeniorTitle(job: RawJob): Boolean {
    val title = job.title.lowercase()
    if (SENIOR_TITLE_KEYWORDS.any { containsWord(title, it) }) return true
    return SENIOR_LEVEL_PATTERNS.any { it.containsMatchIn(title) }
}

/** The posting asks for more than [maxYears] of experience, or says it's not for freshers. */
fun asksTooMuchExperience(job: RawJob, maxYears: Int): Boolean {
    val text = searchableText(job)
    if (maxYears <= 1 && NOT_FOR_FRESHERS.any { text.contains(it) }) return true
    return experienceLowerBounds(text).any { it > maxYears }
}

// ---- 3. Entry-level proof -------------------------------------------------------------------

private val ENTRY_TITLE_KEYWORDS = listOf(
    "fresher", "freshers", "trainee", "intern", "internship", "graduate", "grad", "junior", "jr",
    "entry level", "entry-level", "associate", "apprentice", "campus", "new grad", "early career",
    "early talent", "beginner",
)

// "Software Engineer I", "SDE 1", "SDE-I", "Analyst 1", "Level 1", "L1".
private val ENTRY_LEVEL_PATTERNS = listOf(
    Regex("\\b(engineer|engr|developer|analyst|sde|swe|sdet|programmer|tester)[\\s-]*(i|1)\\b"),
    Regex("\\blevel[\\s-]*1\\b"),
    Regex("\\bl1\\b"),
)

private val ENTRY_TEXT_KEYWORDS = listOf(
    "fresher", "freshers", "entry level", "entry-level", "no experience required", "no prior experience",
    "recent graduate", "recent graduates", "new grad", "fresh graduate", "fresh graduates", "graduates can apply",
)

private val BATCH_PATTERN = Regex("\\b20(2[3-7])\\s*(batch|pass\\s*-?outs?|graduates?)\\b")

fun hasEntryLevelProof(job: RawJob, maxYears: Int): Boolean {
    val title = job.title.lowercase()
    if (ENTRY_TITLE_KEYWORDS.any { containsWord(title, it) }) return true
    if (ENTRY_LEVEL_PATTERNS.any { it.containsMatchIn(title) }) return true

    val text = searchableText(job)
    if (ENTRY_TEXT_KEYWORDS.any { containsWord(text, it) }) return true
    if (BATCH_PATTERN.containsMatchIn(text)) return true
    val bounds = experienceLowerBounds(text)
    return bounds.isNotEmpty() && bounds.all { it <= maxYears }
}

// ---- 4. Location ----------------------------------------------------------------------------

private val INDIA_LOCATIONS = listOf(
    "india", "bangalore", "bengaluru", "hyderabad", "pune", "mumbai", "navi mumbai", "thane",
    "delhi", "new delhi", "gurgaon", "gurugram", "noida", "ghaziabad", "faridabad", "chennai", "kolkata",
    "ahmedabad", "jaipur", "kochi", "cochin", "coimbatore",
    "indore", "nagpur", "lucknow", "chandigarh", "mohali", "bhopal", "vadodara", "nashik", "surat",
    "trivandrum", "thiruvananthapuram", "vizag", "visakhapatnam", "mysore", "mysuru",
    "mangalore", "mangaluru", "bhubaneswar", "patna", "ranchi", "raipur", "dehradun", "goa",
    "karnataka", "maharashtra", "telangana", "tamil nadu", "kerala", "gujarat", "rajasthan",
    "uttar pradesh", "haryana", "west bengal", "madhya pradesh", "odisha", "punjab", "andhra pradesh",
)

// A remote job that mentions any of these is open to someone living in India.
private val INDIA_FRIENDLY_REMOTE = listOf("apac", "asia", "worldwide", "anywhere", "global", "ist")

// Words that describe remoteness itself, not where you must live.
private val REMOTE_FILLER = Regex("\\b(remote|work|from|home|wfh|fully|100%|only|first|friendly|position|job|role)\\b|[^a-z]")

// Adzuna is queried through its India endpoint, so a blank location there is still India.
private const val INDIA_ONLY_SOURCE = "adzuna"

fun matchesLocation(job: RawJob): Boolean {
    val location = job.location.lowercase()
    val inIndia = INDIA_LOCATIONS.any { containsWord(location, it) } || containsWord(location, "ind")
    if (!job.isRemote) {
        return inIndia || (location.isBlank() && job.source == INDIA_ONLY_SOURCE)
    }
    if (inIndia || INDIA_FRIENDLY_REMOTE.any { containsWord(location, it) }) return true
    // "Remote" with no country named = open. "Remote - United States" names somewhere else = closed.
    return location.replace(REMOTE_FILLER, "").isBlank()
}

/** Empty [cities] means all of India. Remote jobs aren't tied to a city. */
fun matchesCities(job: RawJob, cities: List<String>): Boolean {
    if (cities.isEmpty() || job.isRemote) return true
    val location = job.location.lowercase()
    return cities.any { containsWord(location, it) }
}

// ---- 5. Freshness ---------------------------------------------------------------------------

fun matchesFreshness(job: RawJob, nowMillis: Long, maxAgeMillis: Long = daysToMillis(FilterSettings().maxAgeDays)): Boolean {
    val postedAt = job.postedAtEpochMillis ?: return true
    return (nowMillis - postedAt) <= maxAgeMillis
}

// ---- The two stages -------------------------------------------------------------------------

fun acceptForStorage(job: RawJob, nowMillis: Long, extraRoles: List<String> = emptyList()): Boolean =
    matchesRole(job, extraRoles) && !isSeniorTitle(job) && matchesLocation(job) &&
        matchesFreshness(job, nowMillis, MAX_STORED_AGE_MILLIS)

fun matchesPreferences(job: RawJob, nowMillis: Long, settings: FilterSettings): Boolean {
    val title = job.title.lowercase()
    if (settings.blockedWords.any { containsWord(title, it) }) return false
    if (!settings.showRemote && job.isRemote) return false
    if (!matchesCities(job, settings.cities)) return false
    if (!matchesFreshness(job, nowMillis, daysToMillis(settings.maxAgeDays))) return false
    if (asksTooMuchExperience(job, settings.maxYears)) return false
    return !settings.requireEntryProof || hasEntryLevelProof(job, settings.maxYears)
}

fun passesAllFilters(job: RawJob, nowMillis: Long, settings: FilterSettings = FilterSettings()): Boolean =
    acceptForStorage(job, nowMillis, settings.extraRoles) && matchesPreferences(job, nowMillis, settings)

/** Re-check for already-saved jobs when the SAVE rules change. */
fun rejectedForStorage(job: RawJob, extraRoles: List<String>): Boolean =
    !matchesRole(job, extraRoles) || isSeniorTitle(job) || !matchesLocation(job)

// ---- Helpers --------------------------------------------------------------------------------

private fun daysToMillis(days: Int) = TimeUnit.DAYS.toMillis(days.toLong())

private val HTML_TAG = Regex("<[^>]+>")
private val WHITESPACE = Regex("\\s+")

/** Descriptions from RSS/Remotive arrive as HTML; this turns "<li>3+ years</li>" into "3+ years". */
fun plainText(html: String): String =
    html.replace(HTML_TAG, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(WHITESPACE, " ")
        .trim()

private fun searchableText(job: RawJob): String = plainText("${job.title} ${job.description}").lowercase()

// Whole-word match so "sr" doesn't fire inside "sre" and "hr" doesn't fire inside "three".
private fun containsWord(text: String, keyword: String): Boolean {
    var from = 0
    while (true) {
        val index = text.indexOf(keyword, from)
        if (index < 0) return false
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + keyword.length)
        if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) return true
        from = index + 1
    }
}
