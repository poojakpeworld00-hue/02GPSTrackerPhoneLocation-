package com.find.phone.gps.location.tracker.screens.reveal

import android.content.Context
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant

object TourUnveilRules {

    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    private fun daysElapsedReached(lastMs: Long, interval: Int): Boolean =
        lastMs == 0L || (System.currentTimeMillis() - lastMs) / ONE_DAY_MS >= interval

    private fun launchTurnReached(launches: Int, interval: Int): Boolean =
        interval > 0 && launches % interval == 0

    fun shouldShow(context: Context, key: String, config: TourUnveil): Boolean {
        if (!config.enabled) return false
        val store = SecurePrefsKeeper(context)
        return when (config.frequency) {
            AskRhythm.ALWAYS -> true
            AskRhythm.NEVER -> false
            AskRhythm.ONCE -> store.introShownCount(key) == 0
            AskRhythm.EVERY_DAYS -> daysElapsedReached(store.introLastShownMs(key), config.interval)
            AskRhythm.APP_LAUNCHES -> launchTurnReached(store.appLaunchCount, config.interval)
        }
    }

    fun markShown(context: Context, key: String) {
        val store = SecurePrefsKeeper(context)
        val launch = store.appLaunchCount
        if (store.introLastShownSession(key) != launch) store.recordIntroShown(key, launch)
    }

    fun shouldShowLanguage(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.LANGUAGE, TourUnveilSettings.language(context))

    fun shouldShowTerms(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.TERMS, TourUnveilSettings.terms(context))

    fun shouldShowOnboarding(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.ONBOARDING, TourUnveilSettings.onboarding(context))

    fun shouldShowRemindersIntro(context: Context): Boolean {
        if (!AlarmSettings.get(context).isIntroScreen) return false
        if (!AlarmGrant.shouldOfferManage(context)) return false
        if (!AlarmSettings.isCountryAllowed(context)) return false
        return shouldShow(context, TourUnveilSettings.REMINDERS_INTRO, TourUnveilSettings.remindersIntro(context))
    }

    fun shouldShowPermissionSheet(context: Context): Boolean =
        shouldShow(context, TourUnveilSettings.PERMISSION_SHEET, TourUnveilSettings.permissionSheet(context))
}
