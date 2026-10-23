package com.find.phone.gps.location.tracker.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class LookupAllowanceTest {

    @Test
    fun formatRemaining_minutesAndSeconds() {
        assertEquals("59:59", LookupAllowance.formatRemaining(3_599_000L))
        assertEquals("00:01", LookupAllowance.formatRemaining(1L))
    }

    @Test
    fun formatRemaining_hours() {
        assertEquals("1:00:00", LookupAllowance.formatRemaining(3_600_000L))
    }
}
