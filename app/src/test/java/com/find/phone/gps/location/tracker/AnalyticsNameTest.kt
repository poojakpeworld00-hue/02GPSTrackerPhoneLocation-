package com.find.phone.gps.location.tracker

import com.find.phone.gps.location.tracker.monetize.engine.analyticsName
import com.find.phone.gps.location.tracker.monetize.engine.permissionEventName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsNameTest {

    private val valid = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")

    @Test
    fun reservedPrefixesAreRemapped() {
        assertEquals("admob_banner", analyticsName("google_banner"))
        assertEquals("app_x", analyticsName("firebase_x"))
        assertEquals("app_y", analyticsName("ga_y"))
    }

    @Test
    fun invalidCharactersBecomeUnderscores() {
        assertEquals("BackLoad_AdsOFF", analyticsName("BackLoad:AdsOFF"))
        assertTrue(valid.matches(analyticsName("Back_Inter_Load_FAILED:Network error (code 2)")))
    }

    @Test
    fun reservedNamesAndLengthAreHandled() {
        assertEquals("app_update_evt", analyticsName("app_update"))
        assertEquals(40, analyticsName("inter_closed_google_fail_no_fb_no_custom_extra").length)
        assertTrue(analyticsName("1abc").first().isLetter())
    }

    @Test
    fun permissionNamesFitFirebaseLimit() {
        val long = "android.permission.ACCESS_BACKGROUND_LOCATION"
        listOf("Show", "Allow", "Deny", "Bypass").forEach {
            val name = permissionEventName(long, it)
            assertTrue(name, name.length <= 40 && name.endsWith("_$it"))
        }
        assertEquals("Permission_POST_NOTIFICATIONS_Allow", permissionEventName("android.permission.POST_NOTIFICATIONS", "Allow"))
    }

    @Test
    fun newFeatureEventNamesPassThroughUnchanged() {
        val names = listOf(
            "ad_splash_timeout", "reminder_toggle_on", "reminder_toggle_off", "reminder_notif_denied",
            "reminder_prompt_dismiss", "reminder_fsi_granted", "reminder_fsi_denied",
            "reminder_alert_tap", "reminder_alert_dismiss", "PermissionSheet_Close",
            "drawer_language", "drawer_reminders", "drawer_rate", "drawer_share", "drawer_privacy",
            "drawer_terms", "drawer_delete_contacts",
            "un_open_shortcut", "un_language_next", "un_sorry_search",
            "un_sorry_my_number", "un_sorry_reminders", "un_sorry_keep", "un_sorry_still",
            "un_reason_1", "un_reason_2", "un_reason_3", "un_reason_4", "un_reason_5", "un_reason_6",
            "un_tip_location", "un_tip_notif", "un_uninstall_anyway", "un_keep_app",
            "un_thanks_continue", "un_thanks_auto", "un_app_info_opened", "un_app_closed",
        )
        names.forEach { assertEquals(it, analyticsName(it)) }
    }
}
