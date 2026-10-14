package com.find.phone.gps.location.tracker.screens.reveal

import android.content.Context
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant

/**
 * Decides whether an intro screen (Language / Onboarding) shows this launch, from
 * its [TourUnveil] Remote Config policy plus the persisted ledger in
 * [SecurePrefsKeeper]. A screen calls [markShown] when it actually appears.
 *
 * Session = one cold start: [SecurePrefsKeeper.appLaunchCount] is bumped once per launch
 * (in Splash), so `app_launches` counts launches and [markShown] can de-dupe within
 * a single launch even if the gate is evaluated at several points in the funnel.
 */
object TourUnveilRules {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun shouldShow(context: Context, key: String, config: TourUnveil): Boolean {
        if (!config.enabled) return false
        val prefs = SecurePrefsKeeper(context)
        return when (config.frequency) {
            AskRhythm.NEVER -> false
            AskRhythm.ALWAYS -> true
            AskRhythm.ONCE -> prefs.introShownCount(key) == 0
            AskRhythm.EVERY_DAYS -> {
                val last = prefs.introLastShownMs(key)
                if (last == 0L) true
                else (System.currentTimeMillis() - last) / DAY_MS >= config.interval
            }
            // Show on every Nth launch (interval must be positive to be meaningful).
            AskRhythm.APP_LAUNCHES ->
                config.interval > 0 && prefs.appLaunchCount % config.interval == 0
        }
    }

    /** Record that [key] was shown this launch (bumps count + stamps time, once per launch). */
    fun markShown(context: Context, key: String) {
        val prefs = SecurePrefsKeeper(context)
        val session = prefs.appLaunchCount
        if (prefs.introLastShownSession(key) == session) return
        prefs.recordIntroShown(key, session)
    }

    fun shouldShowLanguage(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.LANGUAGE, TourUnveilSettings.language(context))

    fun shouldShowTerms(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.TERMS, TourUnveilSettings.terms(context))

    fun shouldShowOnboarding(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.ONBOARDING, TourUnveilSettings.onboarding(context))

    /**
     * The reminders intro on later launches: `intro_display.reminders_intro` cadence, AND
     * feature_config's `is_intro_screen` + country filter, AND reminders still to be allowed.
     */
    fun shouldShowRemindersIntro(context: Context): Boolean =
        AlarmSettings.get(context).isIntroScreen &&
            AlarmGrant.shouldOfferManage(context) &&
            AlarmSettings.isCountryAllowed(context) &&
            shouldShow(context, TourUnveilSettings.REMINDERS_INTRO, TourUnveilSettings.remindersIntro(context))

    /** Frequency gate only — callers still AND this with their own "is anything pending?" check. */
    fun shouldShowPermissionSheet(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.PERMISSION_SHEET, TourUnveilSettings.permissionSheet(context))
}
