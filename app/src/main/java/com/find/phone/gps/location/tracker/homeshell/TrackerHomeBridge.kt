package com.find.phone.gps.location.tracker.homeshell

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.fragment.app.Fragment
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.TrackerPhoneApp
import com.find.phone.gps.location.tracker.repo.LanguageKeeper
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.setupflow.HomeRoleNudgeDialog
import com.find.phone.gps.location.tracker.setupflow.HomeRoleTipActivity
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.helpers.imprintNativeAdTheme
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.homedeck.grid.screens.HomeDeskPane
import io.homedeck.grid.bridge.HomeHostPort
import io.homedeck.grid.bridge.HomeConfigKeys
import org.json.JSONObject

class TrackerHomeBridge : HomeHostPort {

    override fun configString(key: String, fallback: String): String {

        if (key == HomeConfigKeys.ADS_CONFIG) {
            val row = HomeAdsSettings.appDrawerSlot(TrackerPhoneApp.appContext).position
            return """{"screenWiseAds":{"app_drawer":{"ad_row_position":$row}}}"""
        }
        if (key == HomeConfigKeys.LAUNCHER_CONFIG) return launcherConfig(fallback)
        return topLevel(key).ifBlank { fallback }
    }

    private fun topLevel(key: String): String =
        runCatching { FirebaseRemoteConfig.getInstance().getString(key) }.getOrNull().orEmpty()

    private fun launcherConfig(fallback: String): String {
        val base = runCatching { JSONObject(topLevel(HomeConfigKeys.LAUNCHER_CONFIG)) }.getOrNull()
            ?.let { root -> root.optJSONObject(if (isOrganicAudience()) "organic" else "marketing") ?: root }
        val overlay = AdPrefsStore.getInstance(TrackerPhoneApp.appContext)
            .getString(HomeConfigKeys.LAUNCHER_CONFIG, "")
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val merged = base ?: JSONObject()
        overlay?.let { o -> o.keys().forEach { merged.put(it, o.get(it)) } }
        imprintLauncherAds(merged)
        return merged.toString()
    }

    private fun imprintLauncherAds(config: JSONObject) {
        val context = TrackerPhoneApp.appContext
        config.put(
            "panels",
            JSONObject()
                .put("apps", HomeAdsSettings.panelEnabled(context, left = true))
                .put("host", HomeAdsSettings.panelEnabled(context, left = false)),
        )
        fun gate(surface: HomeAdsSettings.HomeDeskFragment) = JSONObject()
            .put("inter_enabled", HomeAdsSettings.surfaceOn(context, surface))
            .put("inter_counter", 0)
            .put("url_enabled", false)
            .put("url", "")
        val gestures = config.optJSONObject("gestures") ?: JSONObject()
        gestures.put("left_swipe", gate(HomeAdsSettings.HomeDeskFragment.LEFT_PANEL))
        gestures.put("right_swipe", gate(HomeAdsSettings.HomeDeskFragment.RIGHT_PANEL))
        gestures.put("app_launch", gate(HomeAdsSettings.HomeDeskFragment.APP_CLICK))
        gestures.put(
            "home_launch",
            gate(HomeAdsSettings.clickSurface(context, HomeAdsSettings.HomeDeskFragment.HOME_CLICK)),
        )
        gestures.put(
            "drawer_launch",
            gate(HomeAdsSettings.clickSurface(context, HomeAdsSettings.HomeDeskFragment.DRAWER_CLICK)),
        )
        gestures.put("home_back", gate(HomeAdsSettings.HomeDeskFragment.HOME_BACK))
        gestures.put("home_press", gate(HomeAdsSettings.HomeDeskFragment.HOME_PRESS))
        gestures.put("app_exit", gate(HomeAdsSettings.HomeDeskFragment.APP_CLOSE))
        gestures.put("drawer_open", JSONObject().put("inter_enabled", false))
        config.put("gestures", gestures)

        if (!AdPrefsStore.getInstance(context).getBoolean("IsAdsON")) {
            config.put("drawerAppAdEnabled", false)
        }
    }

    override fun dockSlotPackage(context: Context): String? {
        val showHost = runCatching { JSONObject(launcherConfig("")).optBoolean("dock_host_app", true) }
            .getOrDefault(true)
        return if (showHost) context.applicationContext.packageName else Telephony.Sms.getDefaultSmsPackage(context)
    }

    override fun hostLaunchComponent(context: Context): ComponentName =
        ComponentName(context, MainHubActivity::class.java)

    override fun defaultHomeHintIntent(context: Context): Intent = HomeRoleTipActivity.intent(context)

    override fun onEvent(name: String, params: Map<String, String>) {
        TrackerPhoneApp.appContext.trackEvent(name)
    }

    override fun isOrganicAudience(): Boolean =
        !AdPrefsStore.getInstance(TrackerPhoneApp.appContext).getBoolean("OnMaketing")

    override fun panelFragment(): Fragment = HomeShellPane()

    override fun onLauncherStart(activity: Activity): Boolean {
        if (HomeRoleJourney.resumeIfUnfinished(activity)) {
            activity.finish()
            return false
        }

        LanguageKeeper.imprintSaved(activity)
        activity.imprintNativeAdTheme()
        (activity as? HomeDeskPane)?.let {
            HomeShellOwner.attach(it)
            RecentsTaskObserver.attach(it)
        }

        HomeAdsSettings.prefetchLauncherAds(activity)
        return true
    }

    override fun onAppLaunched(packageName: String) {
        ResumeAdKeeper.expectReturnAd()
        HomeAdsSettings.prefetchLauncherAds(TrackerPhoneApp.appContext)
    }

    override fun onLauncherResume(activity: Activity) {

        HomeRoleNudgeDialog.dismiss()
        LanguageKeeper.imprintSaved(activity)
        HomeShellOwner.of(activity)?.homeShellController?.onHostResume()

        HomeAdsSettings.prefetchLauncherAds(activity)

        RecentsTaskObserver.onLauncherResume(activity)
    }

    override fun onNonFatal(error: Throwable) {
        runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(error) }
    }

    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
