package com.jobradar.app.data.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class JobFiltersTest {

    private fun job(
        title: String,
        description: String = "",
        location: String = "Bangalore, Karnataka",
        isRemote: Boolean = false,
        source: String = "test",
        postedAt: Long? = null,
    ) = RawJob(
        source = source, sourceId = "1", title = title, company = "Acme", location = location,
        url = "https://example.com", description = description, isRemote = isRemote, postedAtEpochMillis = postedAt,
    )

    private val now = System.currentTimeMillis()

    // ---- Jobs that were actually in the feed and should NOT have been ----

    @Test fun rejectsNumberedAndRomanLevelsSeenInFeed() {
        listOf(
            "Software Engineer II-C++", "Software Engineer 2 - Java (BYDS)", "Software Engineer 3 – Network Security",
            "Software Engr II", "QA Engineer 2 -Mobile Apps", "Data Analyst II", "Business Analyst II",
            "Advanced Software Engineer", "Business Analyst Experienced", "Lead Developer",
            "Staff Software Development Engineer (Backend - Python/Go/Rust)",
        ).forEach { assertFalse(it, passesAllFilters(job(it), now)) }
    }

    @Test fun rejectsExperienceRequirementsInDescription() {
        listOf(
            "Requires 3-5 years experience in Java",
            "<li>6–10 years of Salesforce administration and development experience</li>",
            "Experience: 4+ yrs",
            "2+ yrs of experience with Spring",
            "Minimum 3 years in backend work",
            "We need someone with 2 years of hands-on Python experience",
            "Not for freshers. Strong React skills.",
        ).forEach { assertFalse(it, passesAllFilters(job("Junior Developer", it), now)) }
    }

    @Test fun rejectsRemoteJobsLockedToOtherCountries() {
        listOf(
            "Remote - United States", "Ontario Remote Work, More...", "Canada - Remote",
            "North America Only", "Ciudad de México, DIF, mx", "Hybrid - Lahore; Pakistan - Remote",
        ).forEach { assertFalse(it, matchesLocation(job("x", location = it, isRemote = true))) }
    }

    @Test fun rejectsPostingsOlderThanTwoWeeks() {
        assertFalse(matchesFreshness(job("x", postedAt = now - TimeUnit.DAYS.toMillis(30)), now))
        assertFalse(matchesFreshness(job("x", postedAt = now - TimeUnit.DAYS.toMillis(15)), now))
        assertTrue(matchesFreshness(job("x", postedAt = now - TimeUnit.DAYS.toMillis(3)), now))
    }

    @Test fun rejectsPlainTitleWithNoEntryLevelSignal() {
        // A bare "Software Engineer" at a big company usually wants 2+ years and says so nowhere we can see.
        assertFalse(passesAllFilters(job("Software Engineer"), now))
    }

    // ---- Fresher jobs that SHOULD show up ----

    @Test fun acceptsFresherTitlesAcrossRoleFamilies() {
        listOf(
            "Junior Web Developer (Fresher)", "AI & Automation Developer (Fresher)", "Junior Software Engineer",
            "Graduate Engineer Trainee", "Associate Software Engineer", "Associate Quality Assurance Engineer - Trainee",
            "Trainee Solutions Engineer", "Junior Data Analyst", "Software Engineer I", "SDE 1", "SDE-I",
            "Android Developer Intern", "Flutter Developer - Fresher", "DevOps Engineer Trainee",
            "Technical Support Engineer - Freshers", "SOC Analyst L1", "Java Developer Trainee",
            "Tehnical Trainee for NetSuite, Zoho, Odoo",
        ).forEach { assertTrue(it, passesAllFilters(job(it), now)) }
    }

    @Test fun acceptsPlainTitleWhenDescriptionSaysEntryLevel() {
        listOf(
            "Freshers can apply",
            "0-1 years experience",
            "Experience: 0-2 years",
            "Open to 2025 batch graduates",
            "This is an entry-level role",
        ).forEach { assertTrue(it, passesAllFilters(job("Software Developer", it), now)) }
    }

    @Test fun ignoresCompanyAgeAndBondNumbers() {
        assertTrue(passesAllFilters(job("Junior Developer", "We have 25+ years of industry experience. 2 year bond."), now))
    }

    @Test fun rejectsNonTechAndOutOfScopeTitles() {
        listOf(
            "Management Trainee - Sales (Freshers)", "BDE (FRESHER/ GRADUATED)", "HR Intern - Jaipur",
            "Content Writer (Fresher)", "Junior Accountant", "Data Scientist - Fresher", "Graduate Trainee (Marine HR)",
            "Mechanical Engineer Trainee",
        ).forEach { assertFalse(it, passesAllFilters(job(it), now)) }
    }

    @Test fun rejectsPaidCoursesSpamAndCoreEngineeringSeenInFeed() {
        listOf(
            "SAP Freshers Training & Simulated Project Experience",
            "% Fresher Software Developer 99.89.61.27.35",
            "Diploma Engineer Trainee",
            "Graduate Engineer Trainee (GET) – Design Engineering",
        ).forEach { assertFalse(it, passesAllFilters(job(it), now)) }
    }

    @Test fun onJobTrainingIsNotACourse() {
        assertTrue(passesAllFilters(job("Web Developer (Fresh graduate welcome, WFH policy, on-job-training)"), now))
    }

    @Test fun shortWordsNeedWholeWordMatches() {
        assertTrue(matchesRole(job("SRE Intern")))
        assertFalse(isSeniorTitle(job("SRE Intern")))     // "sr" must not fire inside "sre"
        assertFalse(matchesRole(job("Internal Communications Intern")))
    }

    // ---- Location ----

    @Test fun acceptsIndiaAndIndiaFriendlyRemote() {
        listOf("Bengaluru - Bellandur (GTP)", "HYDERABAD, IND", "IND - Pune, India", "Indore, MP", "Trivandrum", "Gurugram")
            .forEach { assertTrue(it, matchesLocation(job("x", location = it))) }
        listOf("Remote", "Worldwide", "Anywhere in the World", "Remote - India", "APAC", "")
            .forEach { assertTrue(it, matchesLocation(job("x", location = it, isRemote = true))) }
        assertFalse(matchesLocation(job("x", location = "London, UK")))
    }

    // ---- User settings ----

    @Test fun turningOffEntryProofShowsPlainTitlesButStillRejectsExperience() {
        val loose = FilterSettings(requireEntryProof = false)
        assertTrue(passesAllFilters(job("Software Engineer"), now, loose))
        assertFalse(passesAllFilters(job("Software Engineer", "3-5 years experience"), now, loose))
        assertFalse(passesAllFilters(job("Senior Software Engineer"), now, loose))
    }

    @Test fun maxYearsSettingMovesTheExperienceLimit() {
        val twoYears = FilterSettings(maxYears = 2)
        assertTrue(passesAllFilters(job("Junior Developer", "2+ years of experience"), now, twoYears))
        assertFalse(passesAllFilters(job("Junior Developer", "3+ years of experience"), now, twoYears))
        assertFalse(passesAllFilters(job("Junior Developer", "1+ years of experience"), now, FilterSettings(maxYears = 0)))
    }

    @Test fun freshnessSettingNarrowsTheWindow() {
        val fiveDaysOld = job("Junior Developer", postedAt = now - TimeUnit.DAYS.toMillis(5))
        assertTrue(passesAllFilters(fiveDaysOld, now, FilterSettings(maxAgeDays = 7)))
        assertFalse(passesAllFilters(fiveDaysOld, now, FilterSettings(maxAgeDays = 3)))
    }

    @Test fun citiesAndRemoteSettings() {
        val pune = FilterSettings(cities = listOf("pune"))
        assertTrue(passesAllFilters(job("Junior Developer", location = "Pune, Maharashtra"), now, pune))
        assertFalse(passesAllFilters(job("Junior Developer", location = "Chennai"), now, pune))
        assertTrue(passesAllFilters(job("Junior Developer", location = "Remote", isRemote = true), now, pune))
        assertFalse(passesAllFilters(job("Junior Developer", location = "Remote", isRemote = true), now, FilterSettings(showRemote = false)))
    }

    @Test fun extraRolesAndBlockedWords() {
        assertFalse(passesAllFilters(job("Junior Game Designer"), now))
        assertTrue(passesAllFilters(job("Junior Game Designer"), now, FilterSettings(extraRoles = listOf("game designer"))))
        assertFalse(passesAllFilters(job("Junior Developer - Night Shift"), now, FilterSettings(blockedWords = listOf("night shift"))))
    }

    @Test fun storageStageIsBroaderThanDefaultFeed() {
        // Saved so that turning off "entry-level only" later has something to show.
        assertTrue(acceptForStorage(job("Software Engineer"), now))
        assertFalse(acceptForStorage(job("Software Engineer II"), now))
    }

    @Test fun parseWordListCleansInput() {
        assertTrue(parseWordList(" Pune, bengaluru ,, PUNE\n") == listOf("pune", "bengaluru"))
    }

    @Test fun blankLocationOnlyTrustedForAdzuna() {
        assertTrue(matchesLocation(job("x", location = "", source = "adzuna")))
        assertFalse(matchesLocation(job("x", location = "", source = "Google")))
    }
}
