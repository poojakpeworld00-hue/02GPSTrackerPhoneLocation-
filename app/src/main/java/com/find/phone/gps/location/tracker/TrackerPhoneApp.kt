package com.find.phone.gps.location.tracker

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.ViewTreeObserver
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.find.phone.gps.location.tracker.screens.alerts.AlarmPopupActivity
import com.google.firebase.FirebaseApp
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.HomeSlotAds
import com.find.phone.gps.location.tracker.monetize.engine.RemoteValuesObserver
import com.find.phone.gps.location.tracker.monetize.render.MonetizeGateActivity
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper.isAdAvailable
import com.find.phone.gps.location.tracker.monetize.render.RecentsAdObserver
import com.find.phone.gps.location.tracker.setupflow.WalkthroughHomeAdvance
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnExit
import com.find.phone.gps.location.tracker.monetize.render.SponsorBrowserActivity
import com.find.phone.gps.location.tracker.repo.peoplesync.PeopleUploader
import com.find.phone.gps.location.tracker.homeshell.TrackerHomeAds
import com.find.phone.gps.location.tracker.homeshell.TrackerHomeBridge
import com.find.phone.gps.location.tracker.homeshell.HomeModeToggle
import com.find.phone.gps.location.tracker.homeshell.UnlockAdObserver
import com.find.phone.gps.location.tracker.grants.GrantOrchestrator
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.screens.battery.PowerPlugObserver
import com.find.phone.gps.location.tracker.screens.appevent.InstallEventObserver
import com.find.phone.gps.location.tracker.screens.boot.BootActivity
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore
import com.find.phone.gps.location.tracker.helpers.FaultCatcher
import com.find.phone.gps.location.tracker.helpers.FaultRecoveryActivity
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.find.phone.gps.location.tracker.helpers.PositionJournal
import com.find.phone.gps.location.tracker.helpers.SimLineJournal
import io.homedeck.grid.screens.HomeDeskPane as LauncherHomeActivity
import io.homedeck.grid.bridge.HomeServiceLocator
import io.lighthouse.push.LightHouse
import io.lighthouse.push.LightHouseConfig
import io.lighthouse.push.extended.LightHouseRichPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.SIDELOADING_FALSE

class TrackerPhoneApp : Application(), Application.ActivityLifecycleCallbacks, LifecycleObserver {

    private var currentActivity: Activity? = null

    companion object {

        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        if (FaultRecoveryActivity.isRestartProcess(this)) return
        AdPrefsStore.getInstance(this)

        AppCompatDelegate.setDefaultNightMode(
            when (AppPrefsStore.effectiveTheme(this)) {
                AppPrefsStore.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                AppPrefsStore.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        runCatching { WebSettings.getDefaultUserAgent(this) }

        HomeModeToggle.apply(this)

        CoroutineScope(Dispatchers.IO).launch { PeopleUploader.uploadOnceIfDue(this@TrackerPhoneApp) }

        SimLineJournal.log(this)

        PositionJournal.log(this)

        InstallEventObserver.register(this)

        PowerPlugObserver.register(this)

        UnlockAdObserver.register(this)

        BaseConfig.newInstance(this).appSideloadingStatus = SIDELOADING_FALSE

        HomeServiceLocator.install(
            context = this,
            bridge = TrackerHomeBridge(),
            ads = TrackerHomeAds(),
        )

        LightHouseRichPush.setActivities(
            splashActivity = BootActivity::class.java,
            richPushActivity = MainHubActivity::class.java,
        )
        LightHouse.initialize(
            context = this,
            config = LightHouseConfig(
                apiKey = Obscured.s(BuildConfig.LH_API_KEY),
                baseUrl = Obscured.s(BuildConfig.LH_BASE_URL),
                attributionWaitMs = MonetizeGateActivity.ATTRIBUTION_WAIT_MS,
                richPushActivity = MainHubActivity::class.java,
                onRemoteConfigSync = { version -> RemoteValuesObserver.applyPushed(this, version) },
            ),
        )

        if (MonetizeGateActivity.isAudienceForced) {
            if (MonetizeGateActivity.DEBUG_AUDIENCE_MARKETING) LightHouse.debugForceFlagged(false)
            LightHouse.debugForceInstallSource(
                if (MonetizeGateActivity.DEBUG_AUDIENCE_MARKETING) "paid" else "organic"
            )
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                FirebaseApp.initializeApp(this@TrackerPhoneApp)

                GrantOrchestrator.init(this@TrackerPhoneApp)
            } catch (e: Exception) {
                SafeLogger.log("TrackerPhoneApp", "init failed: ${e.message}")
            }
        }

        registerActivityLifecycleCallbacks(this)

        com.find.phone.gps.location.tracker.insights.ScreenInsights.install(this)

        RecentsAdObserver.install(this)

        WalkthroughHomeAdvance.install(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    processAppForeground()

                    RemoteValuesObserver.start(this@TrackerPhoneApp)

                    RemoteValuesObserver.renewIfStale(this@TrackerPhoneApp)
                }

                override fun onStop(owner: LifecycleOwner) {
                    RemoteValuesObserver.stop()
                }
            }
        )

        FaultCatcher.install(this) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    }

    private fun processAppForeground() {
        SafeLogger.log("AppOpen", "processAppForeground() called")

        val activity = currentActivity
        if (activity == null) {
            SafeLogger.log("AppOpen", "❌ No RESUMED activity")
            return
        }
        SafeLogger.log("AppOpen", "Activity = ${activity::class.java.simpleName}")
        if (activity.isFinishing || activity.isDestroyed) {
            SafeLogger.log("AppOpen", "❌ Activity invalid")
            return
        }

        // never stack app-open over an interstitial; the flags miss Facebook and the pre-callback window
        if (FullscreenOnAction.isInterShow || FullscreenOnExit.isInterBAckShow) {
            SafeLogger.log("AppOpen", "⛔ Interstitial showing — skip app-open")
            return
        }
        val foregroundClass = activity.javaClass.name
        if (foregroundClass == "com.google.android.gms.ads.AdActivity" ||
            foregroundClass == "com.facebook.ads.AudienceNetworkActivity"
        ) {
            SafeLogger.log("AppOpen", "⛔ Ad activity in foreground — skip app-open")
            return
        }

        if (activity is BootActivity) {
            SafeLogger.log("AppOpen", "⛔ Excluded screen (splash)")
            return
        }

        if (activity is AlarmPopupActivity) {
            SafeLogger.log("AppOpen", "⛔ Excluded screen (reminder alert)")
            return
        }

        if (ResumeAdKeeper.skipNextAppOpenAd) {
            ResumeAdKeeper.skipNextAppOpenAd = false
            ResumeAdKeeper.consumeExpectReturnAd()
            SafeLogger.log("AppOpen", "⛔ Skipped (app-initiated settings return)")
            return
        }

        if (ResumeAdKeeper.consumeExpectReturnAd()) {
            SafeLogger.log("AppOpen", "↩ other_app_return")
            activity.runWhenWindowFocused {
                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused
                HomeAdsSettings.runSurface(activity, HomeAdsSettings.HomeDeskFragment.APP_CLOSE) {}
            }
            return
        }

        if (activity is LauncherHomeActivity) {
            SafeLogger.log("AppOpen", "⛔ Excluded screen")
            return
        }

        if (HomeSlotAds.hasOwnFlow(activity, "appOpen")) {
            if (!AdPrefsStore.getInstance(activity).getBoolean("IsAdsON") ||
                !HomeSlotAds.placementEnabled(activity, "appOpen") ||
                ResumeAdKeeper.isShowingAd || FullscreenOnAction.isInterShow
            ) return
            SafeLogger.log("AppOpen", "🚀 appOpen flow (appOpen_*)")
            activity.runWhenWindowFocused {
                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused
                HomeSlotAds.showInterstitial(activity, "appOpen") {}
            }
            return
        }

        val adType = AdFormatKind.fromString(AdPrefsStore.getInstance(activity).getString("IsAdType"))
        SafeLogger.log(
            "AppOpen",
            "AdType=$adType | available=${isAdAvailable} | showing=${ResumeAdKeeper.isShowingAd}"
        )

        if (adType == AdFormatKind.GOOGLE && isAdAvailable && !ResumeAdKeeper.isShowingAd) {
            activity.runWhenWindowFocused {
                if (activity.isFinishing || activity.isDestroyed) return@runWhenWindowFocused
                SafeLogger.log("AppOpen", "🚀 Showing App Open Ad")
                ResumeAdKeeper.presentAdIfAvailable(
                    activity,
                    object : ResumeAdKeeper.OnAdDoneCallback {
                        override fun onShowAdComplete() {
                            SafeLogger.log("AppOpen", "✅ App Open Ad closed safely")
                        }
                    }
                )
            }
        } else {
            SafeLogger.log("AppOpen", "❌ Ad NOT shown (conditions failed)")
        }
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {}

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        if (ResumeAdKeeper.pendingGameCloseAd && activity !is SponsorBrowserActivity) {
            ResumeAdKeeper.pendingGameCloseAd = false
            HomeAdsSettings.runSurface(activity, HomeAdsSettings.HomeDeskFragment.GAME_CLOSE) {}
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityPaused(p0: Activity) {}
    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
    override fun onActivityStarted(p0: Activity) {}
    override fun onActivityStopped(p0: Activity) {}

    private fun Activity.runWhenWindowFocused(action: () -> Unit) {
        if (hasWindowFocus()) {
            action()
        } else {
            val decorView = window.decorView
            val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (hasFocus) {
                        decorView.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                        action()
                    }
                }
            }
            decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        }
    }
}
