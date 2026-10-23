package com.find.phone.gps.location.tracker.screens.uninstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallConfigTest {

    private val full = """
        {"enabled": true, "type": "advance", "thanks_delay": 3,
         "splash_ad": {"show": true, "type": "inter"},
         "sorry_ad": {"show": true, "type": "big"},
         "reason_ad": {"show": false, "type": "weird"},
         "thanks_ad": {"show": true, "type": "banner"},
         "inter_ad": {"show": true, "on": ["sorry", " Reason "]}}
    """.trimIndent()

    @Test
    fun missingOrDisabledBlockTurnsFeatureOff() {
        assertNull(UninstallConfig.parse(null, paid = true))
        assertNull(UninstallConfig.parse("", paid = true))
        assertNull(UninstallConfig.parse("not json", paid = true))
        assertNull(UninstallConfig.parse("""{"type":"advance"}""", paid = true))
        assertNull(UninstallConfig.parse("""{"enabled":false}""", paid = true))
    }

    @Test
    fun organicAlwaysSimplePaidFollowsType() {
        assertFalse(UninstallConfig.parse(full, paid = false)!!.advanced)
        assertTrue(UninstallConfig.parse(full, paid = true)!!.advanced)
        assertFalse(UninstallConfig.parse("""{"enabled":true,"type":"simple"}""", paid = true)!!.advanced)
        assertFalse(UninstallConfig.parse("""{"enabled":true,"type":"turbo"}""", paid = true)!!.advanced)
        assertFalse(UninstallConfig.parse("""{"enabled":true}""", paid = true)!!.advanced)
    }

    @Test
    fun splashAdResolution() {
        assertEquals(UninstallConfig.SplashAd(true, "inter"), UninstallConfig.parse(full, true)!!.splashAd)
        fun splash(block: String) = UninstallConfig.parse("""{"enabled":true,"splash_ad":$block}""", true)!!.splashAd
        assertEquals(UninstallConfig.SplashAd(true, "appopen"), splash("""{"show":true,"type":"AppOpen"}"""))
        assertEquals(UninstallConfig.SplashAd(false, "inter"), splash("""{"show":false,"type":"inter"}"""))
        // unknown / missing type → null type = normal is_splash_inter_show choice
        assertEquals(UninstallConfig.SplashAd(true, null), splash("""{"show":true,"type":"video"}"""))
        assertEquals(UninstallConfig.SplashAd(true, null), splash("""{"show":true}"""))
        // whole block missing → null = normal splash switches
        assertNull(UninstallConfig.parse("""{"enabled":true}""", true)!!.splashAd)
    }

    @Test
    fun thanksDelayIsClamped() {
        fun delay(v: String) = UninstallConfig.parse("""{"enabled":true,"thanks_delay":$v}""", false)!!.thanksDelayMs
        assertEquals(3_000L, UninstallConfig.parse("""{"enabled":true}""", false)!!.thanksDelayMs)
        assertEquals(1_000L, delay("0"))
        assertEquals(10_000L, delay("60"))
        assertEquals(5_000L, delay("5"))
        assertEquals(3_000L, delay("\"x\""))
    }

    @Test
    fun pageAdsAndInter() {
        val cfg = UninstallConfig.parse(full, false)!!
        assertEquals(UninstallConfig.PageAd(true, "big"), cfg.pageAds["sorry"])
        assertEquals(UninstallConfig.PageAd(false, "mid"), cfg.pageAds["reason"])
        assertEquals(UninstallConfig.PageAd(true, "banner"), cfg.pageAds["thanks"])
        assertNull(cfg.pageAds["language"])
        assertEquals(setOf("sorry", "reason"), cfg.inter!!.pages)
        assertEquals(UninstallConfig.ALL_PAGES, UninstallConfig.parse("""{"enabled":true,"inter_ad":{}}""", false)!!.inter!!.pages)
        assertNull(UninstallConfig.parse("""{"enabled":true}""", false)!!.inter)
    }
}
