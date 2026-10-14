package com.find.phone.gps.location.tracker.screens.reveal

import android.content.Context
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant

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

            AskRhythm.APP_LAUNCHES ->
                config.interval > 0 && prefs.appLaunchCount % config.interval == 0
        }
    }

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

    fun shouldShowRemindersIntro(context: Context): Boolean =
        AlarmSettings.get(context).isIntroScreen &&
            AlarmGrant.shouldOfferManage(context) &&
            AlarmSettings.isCountryAllowed(context) &&
            shouldShow(context, TourUnveilSettings.REMINDERS_INTRO, TourUnveilSettings.remindersIntro(context))

    fun shouldShowPermissionSheet(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.PERMISSION_SHEET, TourUnveilSettings.permissionSheet(context))
}
