package com.jobradar.app.data.discovery

import java.util.concurrent.TimeUnit

// Exactly two role categories, per the confirmed scope: software-side + analyst-side.
// Deliberately excludes Data Scientist / ML / AI Engineer / Data Engineer.
private val TECH_KEYWORDS = listOf(
    // Software side: core dev roles + QA/test
    "software engineer", "software developer", "software development engineer", "sde",
    "full stack", "full-stack", "fullstack",
    "backend developer", "back-end developer", "back end developer",
    "frontend developer", "front-end developer", "front end developer",
    "web developer", "application developer",
    "qa engineer", "qa analyst", "quality assurance", "test engineer", "sdet", "automation engineer",
    // Analyst side: data + business/systems analyst
    "data analyst", "business analyst", "systems analyst",
)

// Keywords/level-indicators that signal a job is explicitly NOT fresher/entry-level.
// Includes SDE-2/3 and L4+ level indicators — plain "senior"/"staff"/etc. isn't enough,
// since companies like Amazon label seniority purely through numbers (SDE 2, SDE 3, L5...).
private val SENIOR_KEYWORDS = listOf(
    "senior", "sr.", "sr ", "staff", "principal", "lead ", "director",
    "head of", "manager", "architect", "vp ", "vice president",
    "5+ year", "6+ year", "7+ year", "8+ year", "10+ year",
    "sde 2", "sde2", "sde-2", "sde ii", "sde 3", "sde3", "sde-3", "sde iii",
    "sde iv", "sde 4",
    "l4", "l5", "l6", "l7", "level 4", "level 5", "level 6",
    "swe ii", "swe iii", "swe 2", "swe 3",
)

private val INDIA_LOCATIONS = listOf(
    "india", "bangalore", "bengaluru", "hyderabad", "pune", "mumbai",
    "delhi", "gurgaon", "gurugram", "noida", "chennai", "kolkata",
    "ahmedabad", "jaipur", "kochi", "coimbatore",
)

// Postings older than this at discovery time are almost certainly stale aggregator
// indexing lag, not a fresh opportunity — don't surface or notify about them.
private val MAX_POSTING_AGE_MILLIS = TimeUnit.DAYS.toMillis(21)

fun matchesRole(job: RawJob): Boolean {
    val text = "${job.title} ${job.description}".lowercase()
    return TECH_KEYWORDS.any { text.contains(it) }
}

fun matchesExperience(job: RawJob): Boolean {
    // Best-effort heuristic on free text, not a structured field — biased toward
    // inclusion (only rejects postings that explicitly signal a senior level).
    val text = "${job.title} ${job.description}".lowercase()
    return SENIOR_KEYWORDS.none { text.contains(it) }
}

fun matchesLocation(job: RawJob): Boolean {
    if (job.isRemote) return true
    val text = job.location.lowercase()
    return INDIA_LOCATIONS.any { text.contains(it) }
}

fun matchesFreshness(job: RawJob, nowMillis: Long): Boolean {
    val postedAt = job.postedAtEpochMillis ?: return true
    return (nowMillis - postedAt) <= MAX_POSTING_AGE_MILLIS
}

fun passesAllFilters(job: RawJob, nowMillis: Long): Boolean =
    matchesRole(job) && matchesExperience(job) && matchesLocation(job) && matchesFreshness(job, nowMillis)
