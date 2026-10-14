package com.find.phone.gps.location.tracker.monetize.render.fullscreen

import com.find.phone.gps.location.tracker.insights.AdPulse
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.content.ContextCompat
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.engine.AdTallyKeeper.interCounter
import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeSlotAds
import com.find.phone.gps.location.tracker.monetize.render.DeepLinkRouter
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.isNetworkConnected
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.R
class FullscreenOnAction {

    companion object {

        /**
         * The app-wide interstitial's own placement name, so its link-first / `ad_flow` /
         * `inter_fallback` keys live under `app_…` like every other placement's.
         */
        private const val APP_PLACEMENT = "app"

        private var _isInterShow: Boolean = false
        var isInterShow: Boolean
            get() = _isInterShow
            set(value) {
                _isInterShow = value
            }
        private var googleInterAd: InterstitialAd? = null

        /**
         * Whether a show right now would play an interstitial that is already loaded. In Google
         * preload mode that means the preloaded ad is in hand; any other setup (on-demand,
         * Facebook, custom) is left to [presentInterAds] to decide. For moments that must not sit
         * behind a loader or fall back to another format — they show a ready ad or nothing.
         */
        fun hasReadyInter(context: Context): Boolean {
            val pref = AdPrefsStore.getInstance(context)
            val googlePreload = AdFormatKind.fromString(pref.getString("IsAdType")) == AdFormatKind.GOOGLE &&
                pref.getBoolean("is_preload_ads")
            return !googlePreload || googleInterAd != null
        }
        private var preloadedFbAd: com.facebook.ads.InterstitialAd? = null
        private var isFbPreloading = false
        // Forwarding callbacks — set just before ad.show(), fired by the preload listener
        private var fbOnDismissed: (() -> Unit)? = null
        private var fbOnFail: (() -> Unit)? = null
        var isOpened = false
        var onTabClosed: (() -> Unit)? = null
        fun processTabReturn(context: Context) {
            if (!isOpened) return

            isOpened = false
            onTabClosed?.invoke()
            onTabClosed = null

            releaseSession(context) // 🔥 ADD THIS
        }
        private var customTabsClient: CustomTabsClient? = null
        private var customTabsSession: CustomTabsSession? = null
        private var serviceConnection: CustomTabsServiceConnection? = null

        fun releaseSession(context: Context) {
            serviceConnection?.let {
                try {
                    context.unbindService(it)
                } catch (_: Exception) {}
            }
            serviceConnection = null
            customTabsClient = null
            customTabsSession = null
        }

        fun prefetchFbAd(context: Context) {
            val pref = AdPrefsStore.getInstance(context)
            if (!pref.getBoolean("IsAdsON")) return
            if (isFbPreloading || preloadedFbAd != null) return
            val fbId = pref.getString("faceB_InterAds") ?: return
            isFbPreloading = true
            val inter = com.facebook.ads.InterstitialAd(context, fbId)
            AdPulse.req(context, "interstitial", AdPulse.FACEBOOK)
            inter.loadAd(
                inter.buildLoadAdConfig()
                    .withAdListener(object : com.facebook.ads.InterstitialAdListener {
                        override fun onAdLoaded(ad: com.facebook.ads.Ad?) {
                            AdPulse.loaded(context, "interstitial", AdPulse.FACEBOOK)
                            preloadedFbAd = inter
                            isFbPreloading = false
                        }
                        override fun onError(ad: com.facebook.ads.Ad?, e: com.facebook.ads.AdError?) {
                            AdPulse.loadFailed(context, "interstitial", e?.errorCode, AdPulse.FACEBOOK)
                            isFbPreloading = false
                            fbOnFail?.invoke()
                            fbOnFail = null
                            fbOnDismissed = null
                        }
                        override fun onInterstitialDismissed(ad: com.facebook.ads.Ad?) {
                            AdPulse.close(context, "interstitial", AdPulse.FACEBOOK)
                            prefetchFbAd(context)   // replenish
                            fbOnDismissed?.invoke()
                            fbOnDismissed = null
                            fbOnFail = null
                        }
                        override fun onInterstitialDisplayed(ad: com.facebook.ads.Ad?) {
                            AdPulse.show(context, "interstitial", AdPulse.FACEBOOK)
                        }
                        override fun onAdClicked(ad: com.facebook.ads.Ad?) {}
                        override fun onLoggingImpression(ad: com.facebook.ads.Ad?) {}
                    }).build()
            )
        }

        fun unfoldDirectLink(context: Activity, onClosed: () -> Unit) {
            val url = AdPrefsStore.getInstance(context).getString("DirectLink")

            if (url.isNullOrEmpty()) {
                onClosed()
                return
            }

            // `DirectLinkType` webview / browser: DeepLinkRouter owns those. Only the custom tab
            // path below needs the close tracking, so it alone stays here.
            if (DeepLinkRouter.mode(context) != DeepLinkRouter.Mode.CUSTOM_TAB) {
                DeepLinkRouter.open(context, url)
                onClosed()
                return
            }

            val uri = Uri.parse(url)
            isOpened = true
            onTabClosed = onClosed

            getSession(context) { session ->

                val customTab = CustomTabsIntent.Builder(session)
                    .setShowTitle(true)
                    .setToolbarColor(ContextCompat.getColor(context, R.color.black))
                    .build()

                try {
                    customTab.launchUrl(context, uri)
                } catch (e: Exception) {
                    unfoldInBrowser(context, uri, onClosed)
                }
            }
        }

        // Use CustomTabsSession to track tab close
        private fun getSession(
            context: Context,
            onReady: (CustomTabsSession?) -> Unit
        ) {
            if (customTabsSession != null) {
                onReady(customTabsSession)
                return
            }

            serviceConnection = object : CustomTabsServiceConnection() {

                override fun onCustomTabsServiceConnected(
                    name: ComponentName,
                    client: CustomTabsClient
                ) {
                    customTabsClient = client
                    customTabsSession = client.newSession(null)
                    onReady(customTabsSession)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    customTabsClient = null
                    customTabsSession = null
                    onReady(null)
                }
            }

            CustomTabsClient.bindCustomTabsService(
                context,
                "com.android.chrome",
                serviceConnection as CustomTabsServiceConnection
            )
        }

        private fun unfoldInBrowser(
            context: Activity,
            uri: Uri,
            onClosed: () -> Unit
        ) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                context.startActivity(intent)
//                safeLog("browser_opened")
            } catch (e: Exception) {
//                context.safeLog("browser_open_failed")
            } finally {
                // Ensure callback is always called
                isOpened = false
                onTabClosed?.invoke()
                onTabClosed = null
                onClosed()
            }
        }


    }


    // ----------------------------------------------------------------------
    // LOAD INTER AD (Google)
    // ----------------------------------------------------------------------
    fun fetchInterAds(activity: Activity) {
        val pref = AdPrefsStore.getInstance(activity)
        if (!pref.getBoolean("IsAdsON")) return
        // Firebase "InterAds" master switch — disable interstitial loading entirely
        if (!pref.getBoolean("InterAds")) return
        // Only preload when is_preload_ads = true; on-demand path loads at show time
        if (!pref.getBoolean("is_preload_ads")) return

        val adType = AdFormatKind.fromString(pref.getString("IsAdType"))

        if (adType == AdFormatKind.GOOGLE) {
            activity.safeLog("google_inter_load_start")
            val id = pref.getString("googleInter") ?: return
            AdPulse.req(activity, "interstitial")
            InterstitialAd.load(
                activity, id, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AdPulse.loadFailed(activity, "interstitial", error.code, AdPulse.GOOGLE)
                        googleInterAd = null
                        activity.safeLog("google_inter_load_failed_${error.code}")
                        Log.e("FullscreenOnAction", "Load Failed: ${error.message}")
                    }
                    override fun onAdLoaded(ad: InterstitialAd) {
                        AdPulse.loaded(activity, "interstitial")
                        googleInterAd = ad
                        activity.safeLog("google_inter_loaded")
                        Log.d("FullscreenOnAction", "Google inter loaded")
                    }
                })
        }

        if (adType == AdFormatKind.FACEBOOK || pref.getBoolean("IsFail_FB")) {
            prefetchFbAd(activity)
        }
    }

    // ----------------------------------------------------------------------
    // PUBLIC SHOW METHOD
    // ----------------------------------------------------------------------
    fun presentInterAds(activity: Activity?, adsClose: () -> Unit) {
        presentAdInternal(activity, adsClose)
    }

    // ----------------------------------------------------------------------
    // MAIN INTER AD SHOW
    // ----------------------------------------------------------------------
    private fun presentAdInternal(activity: Activity?, adsClose: () -> Unit) {
        val act = activity ?: return adsClose()
        act.safeLog("inter_request_start")

        val pref = AdPrefsStore.getInstance(act)
        var hasClosed = false

        fun safeClose(reason: String) {
            if (hasClosed) return
            hasClosed = true
            act.safeLog("inter_closed_$reason")
            adsClose()   // OPEN NEXT ACTIVITY INSTANTLY
        }

        // Network check
        if (!isNetworkConnected(act)) return safeClose("no_network")
        if (!pref.getBoolean("IsAdsON")) return safeClose("ads_off")
        // Firebase "InterAds" master switch — skip showing interstitials entirely
        if (!pref.getBoolean("InterAds")) return safeClose("inter_ads_disabled")

        // Counter logic
        val target = pref.getInt("InterCounter")
        if (interCounter != target) {
            interCounter++
            act.safeLog("inter_counter_skip")
            return safeClose("counter_skip")
        }
        interCounter = 0
        act.safeLog("inter_counter_triggered")

        // `app_link_first_then` + `app_DirectLink`: the links first, then the listed follow-ups.
        if (HomeSlotAds.presentLinkFirst(act, APP_PLACEMENT) { safeClose("link_first") }) {
            act.safeLog("inter_link_first")
            return
        }
        // `app_ad_flow`: the whole chain, in the configured order, instead of the interstitial.
        if (HomeSlotAds.presentAdFlow(act, APP_PLACEMENT) { safeClose("ad_flow") }) {
            act.safeLog("inter_ad_flow")
            return
        }

        val isPreload = pref.getBoolean("is_preload_ads")

        when (AdFormatKind.fromString(pref.getString("IsAdType"))) {

            AdFormatKind.GOOGLE -> {
                act.safeLog("inter_type_google")
                if (isPreload) {
                    // A preloaded ad has no load time of its own; `Inter_Loader_Ms` can still put a
                    // short loader in front of it. Nothing to put it in front of when none is ready.
                    if (googleInterAd != null) {
                        BlockingLoaderDialog.beforeShow(act) { presentGoogleInterstitial(act, pref, ::safeClose) }
                    } else {
                        presentGoogleInterstitial(act, pref, ::safeClose)
                    }
                } else {
                    fetchAndShowGoogleOnDemand(act, pref, ::safeClose)
                }
            }

            AdFormatKind.FACEBOOK -> {
                act.safeLog("inter_type_facebook")
                if (isPreload && preloadedFbAd != null) {
                    presentPreloadedFbAd(
                        act,
                        onDismissed = { safeClose("fb_dismiss") },
                        onFail = {
                            act.safeLog("fb_preload_fail_fallback")
                            fetchAndShowFacebookInter(
                                act,
                                onDismissed = { safeClose("fb_dismiss") },
                                onFail = { presentCustomAfterFacebookFail(act, pref) { safeClose("fb_fail_custom") } }
                            )
                        }
                    )
                } else {
                    fetchAndShowFacebookInter(
                        act,
                        onDismissed = { safeClose("fb_dismiss") },
                        onFail = {
                            act.safeLog("fb_load_fail")
                            presentCustomAfterFacebookFail(act, pref) { safeClose("fb_fail_custom") }
                        }
                    )
                }
            }

            AdFormatKind.CUSTOM, AdFormatKind.UNKNOWN -> {
                act.safeLog("inter_type_custom")
                if (pref.getBoolean("IsCustomADS"))
                    unfoldDirectLink(act) { safeClose("custom_opened") }
                else safeClose("custom_disabled")
            }
        }
    }

    // ----------------------------------------------------------------------
    // GOOGLE INTERSTITIAL
    // ----------------------------------------------------------------------
    private fun presentGoogleInterstitial(
        activity: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {

        val inter = googleInterAd
        if (inter == null) {
            activity.safeLog("google_inter_null")
            return processGoogleFail(activity, pref, safeClose)
        }

        // Log load
        activity.trackEvent("google_inter_show_attempt")

        if (BuildConfig.DEBUG) EarningsTracker.simulateDebugRevenue(activity)

        inter.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it)
        }

        inter.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                AdPulse.show(activity, "interstitial")
                isInterShow = true
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "interstitial")
                isInterShow = false
                googleInterAd = null
                activity.safeLog("google_inter_dismiss")
                safeClose("google_dismiss")
                if (pref.getBoolean("is_preload_ads")) fetchInterAds(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                googleInterAd = null
                activity.safeLog("google_inter_failed_show_${error.code}")
                processGoogleFail(activity, pref, safeClose)
                if (pref.getBoolean("is_preload_ads")) fetchInterAds(activity)
            }
        }

        try {
            inter.show(activity)
        } catch (e: Exception) {
            googleInterAd = null
            activity.safeLog("google_inter_exception")
            processGoogleFail(activity, pref, safeClose)
            fetchInterAds(activity)
        }
    }

    // ----------------------------------------------------------------------
    // GOOGLE ON-DEMAND (is_preload_ads = false)
    // ----------------------------------------------------------------------
    private fun fetchAndShowGoogleOnDemand(
        activity: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {
        val id = pref.getString("googleInter") ?: return processGoogleFail(activity, pref, safeClose)
        val isLoader = BlockingLoaderDialog.isEnabled(activity)

        activity.safeLog("google_inter_ondemand_load_start")
        BlockingLoaderDialog.show(activity, isLoader)

        AdPulse.req(activity, "interstitial")
        InterstitialAd.load(
            activity, id, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdPulse.loaded(activity, "interstitial")
                    BlockingLoaderDialog.hide()
                    googleInterAd = ad
                    activity.safeLog("google_inter_ondemand_loaded")
                    presentGoogleInterstitial(activity, pref, safeClose)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdPulse.loadFailed(activity, "interstitial", error.code, AdPulse.GOOGLE)
                    BlockingLoaderDialog.hide()
                    googleInterAd = null
                    activity.safeLog("google_inter_ondemand_fail_${error.code}")
                    processGoogleFail(activity, pref, safeClose)
                }
            }
        )
    }

    // ----------------------------------------------------------------------
    // FACEBOOK PRELOADED SHOW (is_preload_ads = true)
    // ----------------------------------------------------------------------
    private fun presentPreloadedFbAd(
        activity: Activity,
        onDismissed: () -> Unit,
        onFail: () -> Unit
    ) {
        val ad = preloadedFbAd
        if (ad == null || !ad.isAdLoaded) {
            preloadedFbAd = null
            onFail()
            return
        }
        preloadedFbAd = null
        // Wire forwarding callbacks (listener was set at load time in prefetchFbAd)
        fbOnDismissed = onDismissed
        fbOnFail = onFail
        try {
            ad.show()
            activity.safeLog("fb_preloaded_show")
        } catch (e: Exception) {
            activity.safeLog("fb_preloaded_show_exception")
            fbOnDismissed = null
            fbOnFail = null
            onFail()
        }
    }

    private fun processGoogleFail(
        activity: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {
        activity.safeLog("google_fail_start")

        val fbEnabled = pref.getBoolean("IsFail_FB")
        val customEnabled = pref.getBoolean("IsCustomADS")

        if (fbEnabled) {

            activity.safeLog("google_fail_try_facebook")

            fetchAndShowFacebookInter(
                activity,
                onDismissed = { safeClose("fb_dismiss") },
                onFail = {
                    activity.safeLog("fb_fail_after_google_fail")
                    presentCustomAfterFacebookFail(activity, pref, safeClose)
                }
            )
        } else if (HomeSlotAds.hasFallback(activity, APP_PLACEMENT)) {
            // `inter_fallback`: rewarded / full-screen native / … before giving up on the slot.
            activity.safeLog("google_fail_fallback_chain")
            HomeSlotAds.runFallback(activity, APP_PLACEMENT) { safeClose("google_fail_fallback") }
        } else {
            if (customEnabled) {
                activity.safeLog("google_fail_open_custom")
                unfoldDirectLink(activity) { safeClose("google_fail_custom") }
            } else safeClose("google_fail_no_fb_no_custom")
        }
    }

    // ----------------------------------------------------------------------
    // FACEBOOK INTERSTITIAL
    // ----------------------------------------------------------------------
    fun fetchAndShowFacebookInter(
        context: Context,
        onDismissed: () -> Unit,
        onFail: () -> Unit
    ) {
        val pref = AdPrefsStore.getInstance(context)
        val isLoader = BlockingLoaderDialog.isEnabled(context)
        val fbId = pref.getString("faceB_InterAds") ?: return onFail()

        context.safeLog("facebook_inter_load_start")

        val inter = com.facebook.ads.InterstitialAd(context, fbId)

        // ⬅ FULLSCREEN LOADER (only if Activity)
        // Show loader only if isLoader == true
        if (context is Activity) BlockingLoaderDialog.show(context, isLoader)

        AdPulse.req(context, "interstitial", AdPulse.FACEBOOK)
        inter.loadAd(
            inter.buildLoadAdConfig()
                .withAdListener(object : InterstitialAdListener {

                    override fun onAdLoaded(ad: Ad?) {
                        AdPulse.loaded(context, "interstitial", AdPulse.FACEBOOK)
                        context.safeLog("facebook_inter_loaded")
                        if (context is Activity) BlockingLoaderDialog.hide()
                        try {
                            inter.show()
                            context.safeLog("facebook_inter_show")
                        } catch (e: Exception) {
                            context.safeLog("facebook_inter_show_exception")
                            onFail()
                        }
                    }

                    override fun onError(ad: Ad?, error: com.facebook.ads.AdError?) {
                        AdPulse.loadFailed(context, "interstitial", error?.errorCode, AdPulse.FACEBOOK)
                        context.safeLog("facebook_inter_error_${error?.errorCode}")
                        if (context is Activity) BlockingLoaderDialog.hide()
                        onFail()
                    }

                    override fun onInterstitialDismissed(ad: Ad?) {
                        AdPulse.close(context, "interstitial", AdPulse.FACEBOOK)
                        context.safeLog("facebook_inter_dismiss")
                        if (context is Activity) BlockingLoaderDialog.hide()
                        onDismissed()
                    }

                    override fun onInterstitialDisplayed(ad: Ad?) {
                        AdPulse.show(context, "interstitial", AdPulse.FACEBOOK)
                        context.safeLog("facebook_inter_displayed")

                        if (context is Activity) BlockingLoaderDialog.hide()
                    }

                    override fun onAdClicked(ad: Ad?) {
                        context.safeLog("facebook_inter_clicked")
                    }

                    override fun onLoggingImpression(ad: Ad?) {
                        context.safeLog("facebook_inter_impression")
                    }

                }).build()
        )
    }

    private fun presentCustomAfterFacebookFail(
        activity: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {
        activity.safeLog("custom_after_fb_fail")

        if (pref.getBoolean("IsCustomADS"))
            unfoldDirectLink(activity) { safeClose("fb_fail_custom") }
        else safeClose("fb_fail_no_custom")
    }

    // ----------------------------------------------------------------------
    // SAFE LOG WRAPPER
    // ----------------------------------------------------------------------
    private fun Context.safeLog(event: String) {
        try {
            trackEvent(event)
            Log.d("InterADsLog", event)
        } catch (_: Exception) {
        }
    }
}
