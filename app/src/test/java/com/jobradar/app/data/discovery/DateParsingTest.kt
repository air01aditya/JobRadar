package com.jobradar.app.data.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class DateParsingTest {
    private val now = 1_800_000_000_000L
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    @Test fun workdayRelativeDates() {
        assertEquals(now, parseWorkdayPostedOn("Posted Today", now))
        assertEquals(daysAgo(1), parseWorkdayPostedOn("Posted Yesterday", now))
        assertEquals(daysAgo(3), parseWorkdayPostedOn("Posted 3 Days Ago", now))
        assertEquals(daysAgo(19), parseWorkdayPostedOn("Posted 19 Days Ago", now))
        // "30+" must land past the 30-day window so it's treated as old, not borderline.
        assertEquals(daysAgo(31), parseWorkdayPostedOn("Posted 30+ Days Ago", now))
    }

    @Test fun workdayUnknownTextGivesNoDate() {
        assertNull(parseWorkdayPostedOn("", now))
        assertNull(parseWorkdayPostedOn(null, now))
        assertNull(parseWorkdayPostedOn("Recently", now))
    }
}
