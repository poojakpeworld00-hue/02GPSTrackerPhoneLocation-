package com.find.phone.gps.location.tracker.screens.uninstall

import android.app.Activity
import android.widget.FrameLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.find.phone.gps.location.tracker.insights.AdPulse
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * The funnel's ads from `uninstall_flow`: one ad per page (`<page>_ad`, rendered by the app's own
 * slot renderer) and one interstitial slot shown on leaving each page listed in `inter_ad.on`.
 * The interstitial skips InterCounter and never waits: ready → shown, not ready → move on.
 * IsAdsON off → nothing here runs.
 */
object UninstallAds {

    private const val TAG = "UninstallAds"

    private var inter: InterstitialAd? = null
    private var loading = false

    private fun adsOn(activity: Activity): Boolean = AdPrefsStore.getInstance(activity).getBoolean("IsAdsON")

    fun showPageAd(activity: Activity, page: String, container: FrameLayout, shimmer: ShimmerFrameLayout?) {
        val ad = UninstallFlow.config(activity)?.pageAds?.get(page)
        val slot = if (ad == null || !ad.show || !adsOn(activity)) {
            HomeAdsSettings.Slot(false, HomeAdsSettings.SlotAdUnit.NONE, "mid", "adaptive", "")
        } else when (ad.type) {
            "banner" -> HomeAdsSettings.Slot(true, HomeAdsSettings.SlotAdUnit.BANNER, "mid", "adaptive", "")
            "small" -> HomeAdsSettings.Slot(true, HomeAdsSettings.SlotAdUnit.NATIVE, "native_banner", "adaptive", "")
            else -> HomeAdsSettings.Slot(true, HomeAdsSettings.SlotAdUnit.NATIVE, ad.type, "adaptive", "")
        }
        HomeAdsSettings.presentSlot(activity, slot, container, shimmer)
    }

    fun preloadInter(activity: Activity) {
        val cfg = UninstallFlow.config(activity)?.inter ?: return
        val pref = AdPrefsStore.getInstance(activity)
        if (!cfg.show || !adsOn(activity) || AdFormatKind.fromString(pref.getString("IsAdType")) != AdFormatKind.GOOGLE) return
        if (inter != null || loading) return
        val id = pref.getString("googleInter")?.takeIf { it.isNotBlank() } ?: return
        loading = true
        AdPulse.req(activity, "interstitial")
        InterstitialAd.load(activity.applicationContext, id, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdPulse.loaded(activity, "interstitial")
                    inter = ad
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdPulse.loadFailed(activity, "interstitial", error.code, AdPulse.GOOGLE)
                    loading = false
                }
            })
    }

    /** Shows the funnel interstitial on leaving [page] if one is ready, then runs [next]. */
    fun showInterThen(activity: Activity, page: String, next: () -> Unit) {
        val cfg = UninstallFlow.config(activity)?.inter
        val ad = inter
        if (cfg == null || !cfg.show || page !in cfg.pages || !adsOn(activity) || ad == null) {
            if (cfg != null && page in cfg.pages) preloadInter(activity)
            return next()
        }
        inter = null

        var done = false
        val finish = {
            if (!done) {
                done = true
                FullscreenOnAction.isInterShow = false
                next()
            }
        }
        ad.setOnPaidEventListener { EarningsTracker.trackPaidEvent(activity, it, "interstitial", ad.adUnitId, ad.responseInfo) }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            // keeps the App Open ad from stacking when this one closes
            override fun onAdShowedFullScreenContent() {
                AdPulse.show(activity, "interstitial")
                FullscreenOnAction.isInterShow = true
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "interstitial")
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                SafeLogger.log(TAG, "inter failed to show on '$page': ${error.message}")
                finish()
            }
        }
        runCatching { ad.show(activity) }.onFailure { finish() }
    }
}
