package io.homedeck.grid.sponsor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAdsSettingsTest {

    private val blob = """
        {"organic":{"screenWiseAds":{
            "app_drawer": {"reload_ad_on_open": false, "ad_row_position": 9, "adType": "native", "loadType": "preload",
                           "adData": {"adId": "x", "isActive": false, "adSubType": 4}, "adDataList": []},
            "apps_panel": {"reload_ad_on_open": false, "adType": "native"}
         }},
         "marketing":{"screenWiseAds":{
            "app_drawer": {"ad_row_position": 3, "adType": "native"}
         }}}
    """.trimIndent()

    @Test
    fun readsLauncherOnlyFieldsFromTheAudienceBranch() {
        val organic = HomeAdsSettings.screenWiseAd(blob, organic = true, HomeAdsSettings.KEY_APP_DRAWER)
        assertEquals(9, organic.optInt("ad_row_position"))
        assertFalse(organic.optBoolean("reload_ad_on_open", true))

        val marketing = HomeAdsSettings.screenWiseAd(blob, organic = false, HomeAdsSettings.KEY_APP_DRAWER)
        assertEquals(3, marketing.optInt("ad_row_position"))
        assertTrue(marketing.optBoolean("reload_ad_on_open", true))
    }

    @Test
    fun missingKeyBranchOrGarbageIsEmpty() {
        assertEquals(0, HomeAdsSettings.screenWiseAd(blob, organic = false, HomeAdsSettings.KEY_PANEL_BOTTOM).length())
        assertEquals(0, HomeAdsSettings.screenWiseAd("""{"organic":{}}""", organic = true, "app_drawer").length())
        assertEquals(0, HomeAdsSettings.screenWiseAd("", organic = true, "app_drawer").length())
        assertEquals(0, HomeAdsSettings.screenWiseAd("not json", organic = true, "app_drawer").length())
    }

    @Test
    fun flatBlobWithoutAudienceBranchesIsReadAsIs() {
        val flat = """{"screenWiseAds":{"app_drawer":{"ad_row_position":5}}}"""
        assertEquals(5, HomeAdsSettings.screenWiseAd(flat, organic = true, "app_drawer").optInt("ad_row_position"))
        assertEquals(5, HomeAdsSettings.screenWiseAd(flat, organic = false, "app_drawer").optInt("ad_row_position"))
    }
}
