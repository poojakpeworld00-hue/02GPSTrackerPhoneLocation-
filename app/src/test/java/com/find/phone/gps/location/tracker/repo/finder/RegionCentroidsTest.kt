package com.find.phone.gps.location.tracker.repo.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionCentroidsTest {

    @Test
    fun indiaCityWinsOverState() {
        val (pin, acc) = RegionCentroids.fallback("in", "Surat", "Gujarat", "India")!!
        assertEquals(ZoneAccuracy.CITY, acc)
        assertEquals(21.1702, pin.lat, 1e-4)
    }

    @Test
    fun unknownCityFallsBackToState() {
        val (_, acc) = RegionCentroids.fallback("IN", "Nowhere", "State of Gujarat", "India")!!
        assertEquals(ZoneAccuracy.STATE, acc)
    }

    @Test
    fun otherCountryUsesCountryCentroid() {
        assertEquals(ZoneAccuracy.COUNTRY, RegionCentroids.fallback("US", "Austin", "Texas", "United States")!!.second)
        assertNull(RegionCentroids.fallback("XX", null, null, null))
    }
}
