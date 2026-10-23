package com.find.phone.gps.location.tracker.monetize.render.fullscreen

import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.insights.AdPulse
import android.app.Activity
import android.content.Context
import android.util.Log
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.engine.AdTallyKeeper.interBackCounter
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeSlotAds
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.isNetworkConnected

class FullscreenOnExit {

    companion object {
        private var _isInterBAckShow: Boolean = false
        var isInterBAckShow: Boolean
            get() = _isInterBAckShow
            set(value) {
                _isInterBAckShow = value
            }
        private var googleInterBack: InterstitialAd? = null
    }

    fun fetchBackInterAds(activity: Activity) {
        val pref = AdPrefsStore.getInstance(activity)

        if (!pref.getBoolean("IsAdsON")) {
            activity.safeLog("back_load_ads_off")
            return
        }

        if (!pref.getBoolean("InterAds")) {
            activity.safeLog("back_load_inter_disabled")
            return
        }

        if (!pref.getBoolean("IsBack")) {
            activity.safeLog("back_load_back_ads_off")
            return
        }

        val id = pref.getString("googleBackInter") ?: return
        val req = AdRequest.Builder().build()

        if (AdFormatKind.fromString(pref.getString("IsAdType")) == AdFormatKind.GOOGLE) {
            AdPulse.req(activity, "interstitial_back")
            InterstitialAd.load(
                activity, id, req,
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(ad: InterstitialAd) {
                        AdPulse.loaded(activity, "interstitial_back")
                        googleInterBack = ad
                        Log.d("FullscreenOnExit", "Back Inter Loaded")
                        activity.safeLog("Back_Inter_Loaded")
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        AdPulse.loadFailed(activity, "interstitial_back", err.code, AdPulse.GOOGLE)
                        googleInterBack = null
                        Log.e("FullscreenOnExit", "Back Inter Load Fail: ${err.message}")
                        activity.safeLog("back_inter_load_failed_${err.code}")
                    }
                })
        }
    }

    fun presentBackAds(activity: Activity?, adsClose: () -> Unit) {
        presentBackInternal(activity, adsClose)
    }

    private fun presentBackInternal(activity: Activity?, adsClose: () -> Unit) {
        val act = activity ?: return adsClose()
        val pref = AdPrefsStore.getInstance(act)
        var closedOnce = false

        fun safeClose(reason: String) {
            if (closedOnce) return
            closedOnce = true
            act.safeLog("Closed_$reason")
            Log.e("FullscreenOnExit", "Closed: $reason")
            try {
                adsClose()
            } catch (_: Exception) {
            }
        }

        if (!isNetworkConnected(act)) return safeClose("no_network")
        if (!pref.getBoolean("IsAdsON")) return safeClose("ads_off")

        if (!pref.getBoolean("InterAds")) return safeClose("inter_ads_disabled")
        if (!pref.getBoolean("IsBack")) return safeClose("back_ads_disabled")

        val target = pref.getInt("InterBackCounter")

        if (interBackCounter != target) {
            interBackCounter++
            return safeClose("counter_skip")
        }
        interBackCounter = 0

        if (HomeSlotAds.hasOwnFlow(act, "back")) {
            if (!HomeSlotAds.placementEnabled(act, "back")) return safeClose("back_ads_on_false")
            HomeSlotAds.showInterstitial(act, "back") { safeClose("back_flow") }
            return
        }

        when (AdFormatKind.fromString(pref.getString("IsAdType"))) {

            AdFormatKind.GOOGLE -> {

                if (googleInterBack != null) {
                    BlockingLoaderDialog.beforeShow(act) { presentGoogleBackInter(act, pref, ::safeClose) }
                } else {
                    presentGoogleBackInter(act, pref, ::safeClose)
                }
            }

            AdFormatKind.FACEBOOK -> {
                presentFacebookBackInter(
                    act,
                    onDismiss = { safeClose("fb_back_dismiss") },
                    onFail = {
                        presentCustomAfterFBFail(act, pref) {
                            safeClose("fb_back_fail")
                        }
                    }
                )
            }

            AdFormatKind.CUSTOM, AdFormatKind.UNKNOWN -> {
                if (pref.getBoolean("IsCustomADS"))
                    FullscreenOnAction.unfoldDirectLink(act) { safeClose("custom_open") }
                else safeClose("custom_disabled")
            }

            else -> safeClose("invalid_type")
        }
    }

    private fun presentGoogleBackInter(
        activity: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {
        val ad = googleInterBack
        if (ad == null) {
            return processGoogleFail(activity, pref, safeClose)
        }
        activity.safeLog("admob_back_inter_show_attempt")

        ad.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it, "interstitial_back", ad.adUnitId, ad.responseInfo)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                AdPulse.show(activity, "interstitial_back")
                isInterBAckShow = true
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "interstitial_back")
                googleInterBack = null
                isInterBAckShow = false
                safeClose("Google_Dismiss")
                fetchBackInterAds(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdPulse.showFailed(activity, "interstitial_back", error.code)
                googleInterBack = null
                processGoogleFail(activity, pref, safeClose)
                fetchBackInterAds(activity)
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            googleInterBack = null
            processGoogleFail(activity, pref, safeClose)
            fetchBackInterAds(activity)
        }
    }

    private fun processGoogleFail(
        activity: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {
        if (pref.getBoolean("IsFail_FB")) {
            presentFacebookBackInter(
                activity,
                onDismiss = { safeClose("fb_dismiss") },
                onFail = { presentCustomAfterFBFail(activity, pref, safeClose) }
            )

        } else {
            if (pref.getBoolean("IsCustomADS")) {
                FullscreenOnAction.unfoldDirectLink(activity) { safeClose("google_fail_custom") }
            } else safeClose("google_fail_no_fb_no_custom")
        }
    }

    private fun presentFacebookBackInter(
        context: Context,
        onDismiss: () -> Unit,
        onFail: () -> Unit
    ) {
        val pref = AdPrefsStore.getInstance(context)
        val isLoader = BlockingLoaderDialog.isEnabled(context)
        val fbId = pref.getString("faceB_InterAds") ?: return onFail()

        val fb = com.facebook.ads.InterstitialAd(context, fbId)

        if (context is Activity) BlockingLoaderDialog.show(context, isLoader)

        AdPulse.req(context, "interstitial_back", AdPulse.FACEBOOK)
        fb.loadAd(
            fb.buildLoadAdConfig()
                .withAdListener(object : InterstitialAdListener {

                    override fun onAdLoaded(ad: Ad?) {
                        AdPulse.loaded(context, "interstitial_back", AdPulse.FACEBOOK)
                        if (context is Activity) BlockingLoaderDialog.hide()
                        try {
                            fb.show()
                        } catch (e: Exception) {
                            onFail()
                        }
                    }

                    override fun onError(ad: Ad?, err: com.facebook.ads.AdError?) {
                        AdPulse.loadFailed(context, "interstitial_back", err?.errorCode, AdPulse.FACEBOOK)
                        if (context is Activity) BlockingLoaderDialog.hide()
                        onFail()
                    }

                    override fun onInterstitialDismissed(ad: Ad?) {
                        AdPulse.close(context, "interstitial_back", AdPulse.FACEBOOK)
                        if (context is Activity) BlockingLoaderDialog.hide()
                        onDismiss()
                    }

                    override fun onLoggingImpression(ad: Ad?) {}
                    override fun onInterstitialDisplayed(ad: Ad?) {
                        AdPulse.show(context, "interstitial_back", AdPulse.FACEBOOK)
                        if (context is Activity) BlockingLoaderDialog.hide()
                    }

                    override fun onAdClicked(ad: Ad?) {}

                }).build()
        )
    }

    private fun presentCustomAfterFBFail(
        context: Activity,
        pref: AdPrefsStore,
        safeClose: (String) -> Unit
    ) {
        if (pref.getBoolean("IsCustomADS"))
            FullscreenOnAction.unfoldDirectLink(context) { safeClose("fb_fail_custom") }
        else safeClose("fb_fail_no_custom")
    }

    private fun Context.safeLog(event: String) {
        try {
            this.trackEvent(event)
        } catch (_: Exception) {
        }
    }

}
