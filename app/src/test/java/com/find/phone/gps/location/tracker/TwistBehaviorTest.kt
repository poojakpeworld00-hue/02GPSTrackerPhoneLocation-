package com.find.phone.gps.location.tracker

import com.find.phone.gps.location.tracker.grants.GrantPolicy
import com.find.phone.gps.location.tracker.grants.PageMatcher
import com.find.phone.gps.location.tracker.repo.finder.DialFactsReader
import com.find.phone.gps.location.tracker.repo.finder.RouteStyle
import com.find.phone.gps.location.tracker.repo.peoplesync.HandsetPosition
import com.find.phone.gps.location.tracker.repo.peoplesync.PeopleRecords
import com.find.phone.gps.location.tracker.screens.simcard.OperatorDialCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TwistBehaviorTest {

    @Test fun pageMatcherDirectAndLegacyNames() {
        assertTrue(PageMatcher.matches("BootActivity", "bootactivity"))
        assertTrue(PageMatcher.matches("SplashActivity", "BootActivity"))
        assertTrue(PageMatcher.matches("MainActivity", "homestageactivity"))
        assertFalse(PageMatcher.matches("SplashActivity", "MainHubActivity"))
        assertFalse(PageMatcher.matches("UnknownActivity", "BootActivity"))
    }

    @Test fun pageMatcherKeyForStopsAtFirstMatch() {
        val keys = listOf("TorchActivity", "SplashActivity", "BootActivity", "Other").iterator()
        assertEquals("SplashActivity", PageMatcher.keyFor(keys, "BootActivity"))
        assertEquals("BootActivity", keys.next())
        assertNull(PageMatcher.keyFor(listOf("A", "B").iterator(), "BootActivity"))
    }

    @Test fun pageMatcherRulesForFiltersDisabledAndKeepsOrder() {
        fun policy(key: String, enabled: Boolean, vararg names: String) =
            GrantPolicy(key, enabled, names.toList(), 0L, 0, false)
        val rules = listOf(
            policy("a", true, "SplashActivity"),
            policy("b", false, "BootActivity"),
            policy("c", true, "Nope", "bootactivity"),
            policy("d", true, "MainActivity"),
        )
        assertEquals(listOf("a", "c"), PageMatcher.rulesFor("BootActivity", rules).map { it.key })
    }

    @Test fun operatorCodesByCarrier() {
        assertEquals("*333#", OperatorDialCodes.forCarrier("Jio 4G")[0].codes[0].code)
        assertEquals("*121#", OperatorDialCodes.forCarrier("AIRTEL")[0].codes[0].code)
        assertEquals("*199#", OperatorDialCodes.forCarrier("Vodafone IN")[0].codes[0].code)
        assertEquals("199", OperatorDialCodes.forCarrier("Vi")[1].codes[0].code)
        assertEquals("*123#", OperatorDialCodes.forCarrier("bsnl mobile")[0].codes[0].code)
        val generic = OperatorDialCodes.forCarrier(null)
        assertEquals(2, generic.size)
        assertEquals("*1#", generic[0].codes.single().code)
        assertSame(generic, OperatorDialCodes.forCarrier("vix"))
        assertSame(OperatorDialCodes.forCarrier("idea"), OperatorDialCodes.forCarrier("vi india"))
    }

    @Test fun dialFactsHelpers() {
        assertTrue(DialFactsReader.isValid("9879012345", "in"))
        assertFalse(DialFactsReader.isValid("123", "IN"))
        assertFalse(DialFactsReader.isValid("abc", "IN"))
        assertEquals("+919879012345", DialFactsReader.e164("9879012345", "IN", "91"))
        assertEquals("+91abc", DialFactsReader.e164("abc", "IN", "91"))
        assertEquals("US" to "2125550187", DialFactsReader.split("+1 212-555-0187", "IN"))
        assertEquals("IN" to "", DialFactsReader.split("xyz", "IN"))
    }

    @Test fun dialFactsReadValidAndFallback() {
        val facts = DialFactsReader.read("9879012345", "in", "91")
        assertEquals("+919879012345", facts.e164)
        assertEquals("+91 98790 12345", facts.international)
        assertEquals("IN", facts.iso2)
        assertTrue(facts.valid)
        assertEquals(RouteStyle.MOBILE, facts.lineType)
        assertEquals("Asia/Calcutta", facts.timeZone)

        val bad = DialFactsReader.read("abc", "in", "91")
        assertEquals("+91abc", bad.e164)
        assertEquals("+91 abc", bad.international)
        assertEquals("IN", bad.iso2)
        assertFalse(bad.valid)
        assertEquals(RouteStyle.OTHER, bad.lineType)
        assertNull(bad.carrier)
        assertNull(bad.area)
        assertNull(bad.timeZone)
    }

    @Test fun peopleRecordsCountsAndCountrySummary() {
        val area = HandsetPosition.Area("Surat", "Gujarat", "395007")
        val p = PeopleRecords.prepare(
            "IN",
            listOf(
                " A " to "+91 98790 12345",
                "B" to "09879012345",
                "" to "+1 212-555-0187",
                "C" to "  ",
                "D" to "+1 212-555-0187",
                "E" to "+91 70460 24874",
            ),
            area,
        )
        assertEquals(6, p.total)
        assertEquals(2, p.invalid)
        assertEquals(1, p.duplicates)
        assertEquals("IN", p.deviceRegion)
        assertEquals(listOf("A", "D", "E"), p.rows.map { it.name })
        assertEquals("IN 2, US 1", p.byCountry)
        val us = p.rows.single { it.region == "US" }
        assertEquals("United States", us.state)
        assertNull(us.pincode)
    }
}
