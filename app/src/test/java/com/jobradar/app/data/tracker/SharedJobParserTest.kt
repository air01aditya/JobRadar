package com.jobradar.app.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedJobParserTest {

    @Test fun linkedInWording() {
        val job = SharedJobParser.parse("Check out this job at Infosys: Java Developer https://www.linkedin.com/jobs/view/123456", null)!!
        assertEquals("Java Developer", job.title)
        assertEquals("Infosys", job.company)
        assertEquals("https://www.linkedin.com/jobs/view/123456", job.url)
        assertEquals("LinkedIn", job.source)
    }

    @Test fun titleAtCompanyWording() {
        val job = SharedJobParser.parse(
            "Python Developer job at TCS in Pune. Apply now https://www.naukri.com/job-listings-python-developer-tcs-pune-0-to-1-years-123",
            null,
        )!!
        assertEquals("Python Developer", job.title)
        assertEquals("TCS", job.company)
        assertEquals("Naukri", job.source)
    }

    @Test fun dashSeparatedWording() {
        val job = SharedJobParser.parse("Software Engineer - Zoho - Chennai\nhttps://in.indeed.com/viewjob?jk=abc123", null)!!
        assertEquals("Software Engineer", job.title)
        assertEquals("Zoho", job.company)
        assertEquals("Indeed", job.source)
    }

    @Test fun browserShareUsesPageTitleFromSubject() {
        val job = SharedJobParser.parse("https://www.naukri.com/job-listings-abc", "Frontend Developer at Wipro - Naukri.com")!!
        assertEquals("Frontend Developer", job.title)
        assertEquals("Wipro", job.company)
    }

    @Test fun unknownWordingKeepsFirstLineAsTitle() {
        val job = SharedJobParser.parse("Great opening for freshers!\nhttps://example.com/jobs/1", null)!!
        assertEquals("Great opening for freshers", job.title)
        assertEquals("", job.company)
        assertEquals("a link", job.source)
    }

    @Test fun noLinkMeansNothingToSave() {
        assertNull(SharedJobParser.parse("Java Developer at Infosys", null))
    }

    private val SharedJob.source get() = sourceLabel
}
