package com.find.phone.gps.location.tracker.monetize.render
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.find.phone.gps.location.tracker.insights.AdPulse
import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.BuildConfig
import com.facebook.ads.AdView as FbAdView

enum class StripSizing { ADAPTIVE, INLINE, NORMAL }
enum class StripVariant { AUTO, GOOGLE, FACEBOOK, CUSTOM }

interface StripAdObserver {
    fun onAdLoaded() {}
    fun onAdFailed() {}
}

class StripSponsor {

    private var googleBanner: AdView? = null
    private var facebookBanner: FbAdView? = null

    companion object {
        var bannerCounter = 0
    }

    fun presentBanner(
        activity: Activity,
        container: FrameLayout,
        type: StripVariant = StripVariant.AUTO,
        size: StripSizing = StripSizing.ADAPTIVE,
        isCollapsable: Boolean = false,
        shimmer: ShimmerFrameLayout? = null,
        observer: StripAdObserver? = null,
        customAdUnitId: String? = null,
        disableInternalFallback: Boolean = false
    ) {
        val pref = AdPrefsStore.getInstance(activity)

        if (!isNetworkConnected(activity)|| !pref.getBoolean("IsAdsON") || !pref.getBoolean("BannerAds")) {
            hide(container)
            observer?.onAdFailed()
            return
        }

        if (bannerCounter < pref.getInt("BannerCounter")) {
            bannerCounter++
            hide(container)
            observer?.onAdFailed()
            return
        }
        bannerCounter = 0

        when (type) {
            StripVariant.AUTO -> {
                when (AdFormatKind.fromString(pref.getString("IsAdType"))) {
                    AdFormatKind.GOOGLE -> fetchGoogleBanner(
                        activity,
                        container,
                        size,
                        isCollapsable,
                        shimmer,
                        observer,
                        customAdUnitId,
                        disableInternalFallback
                    )

                    AdFormatKind.FACEBOOK -> fetchFacebookBanner(
                        activity, container, shimmer, observer, disableInternalFallback
                    )

                    AdFormatKind.CUSTOM, AdFormatKind.UNKNOWN -> {
                        if (disableInternalFallback) {
                            observer?.onAdFailed()
                        } else {
                            OwnAdsKeeper().fetchCustomAd(
                                activity,
                                container,
                                OwnAdsKeeper.OwnAdFormat.BANNER
                            )
                        }
                    }
                }
            }

            StripVariant.GOOGLE -> fetchGoogleBanner(
                activity,
                container,
                size,
                isCollapsable,
                shimmer,
                observer,
                customAdUnitId,
                disableInternalFallback
            )

            StripVariant.FACEBOOK -> fetchFacebookBanner(
                activity, container, shimmer, observer, disableInternalFallback
            )

            StripVariant.CUSTOM -> {
                if (disableInternalFallback) {
                    observer?.onAdFailed()
                } else {
                    OwnAdsKeeper().fetchCustomAd(
                        activity,
                        container,
                        OwnAdsKeeper.OwnAdFormat.BANNER
                    )
                }
            }
        }
    }

    private fun fetchGoogleBanner(
        activity: Activity,
        container: FrameLayout,
        size: StripSizing,
        isCollapsable: Boolean,
        shimmer: ShimmerFrameLayout? = null,
        observer: StripAdObserver?,
        customAdUnitId: String? = null,
        disableInternalFallback: Boolean = false
    ) {
        val pref = AdPrefsStore.getInstance(activity)
        val adUnitId = customAdUnitId ?: pref.getString("googleBanner")

        if (adUnitId.isNullOrEmpty()) {
            if (disableInternalFallback) observer?.onAdFailed()
            else fallbackToFBOrCustom(activity, container, shimmer, observer)
            return
        }

        hide(container)

        shimmer?.startShimmer()
        shimmer?.visibility = View.VISIBLE
        container.removeAllViews()
        shimmer?.let { container.addView(it) }
        container.visibility = View.VISIBLE

        if (googleBanner == null) googleBanner = AdView(activity)
        googleBanner?.adUnitId = adUnitId

        if (isCollapsable) {
            googleBanner?.setAdSize(getAdSize(activity, container))
        } else {
            googleBanner?.setAdSize(getGoogleSize(activity, container, size, isCollapsable))

        }

        googleBanner?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                AdPulse.loaded(activity, "banner")
                Log.i(
                    "BannerAds",
                    "Ad loaded. adView.isCollapsible() is ${googleBanner?.isCollapsible}.",
                )

                activity.trackEvent("Banner_Load")

                googleBanner!!.setOnPaidEventListener {
                    EarningsTracker.trackPaidEvent(activity, it, "banner", googleBanner?.adUnitId, googleBanner?.responseInfo)
                }

                shimmer?.stopShimmer()
                shimmer?.visibility = View.GONE
                container.removeAllViews()

                container.addView(googleBanner)
                container.visibility = View.VISIBLE
                observer?.onAdLoaded()

            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                AdPulse.loadFailed(activity, "banner", error.code, AdPulse.GOOGLE)
                shimmer?.stopShimmer()
                shimmer?.visibility = View.GONE
                try {
                    activity.trackEvent("Banner_fail_Load")
                } catch (e: Exception) {
                    SafeLogger.error("StripSponsor", "banner event logging failed", e)
                }
                Log.e("StripSponsor", "Google Banner Failed: ${error.message}")
                observer?.onAdFailed()
                if (!disableInternalFallback) {
                    fallbackToFBOrCustom(activity, container, shimmer, observer)
                }
            }

            override fun onAdClicked() {
                activity.trackEvent("admob_banner")
            }

            override fun onAdImpression() {
                super.onAdImpression()
                AdPulse.impression(activity, "banner")
            }
        }

        val request = if (isCollapsable) {
            val extras = Bundle()
            extras.putString("collapsible", "bottom")
            AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, extras).build()
        } else AdRequest.Builder().build()

        AdPulse.req(activity, "banner")
        googleBanner?.loadAd(request)
    }

    private fun getAdSize(
        activity: Activity,
        adContainer: FrameLayout
    ): com.google.android.gms.ads.AdSize {
        val display = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(display)

        val density = display.density
        val widthPixels =
            if (adContainer.width == 0) display.widthPixels.toFloat()
            else adContainer.width.toFloat()

        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    private fun getGoogleSize(
        activity: Activity,
        container: FrameLayout,
        size: StripSizing,
        isCollapsable: Boolean
    ): AdSize {
        return when (size) {
            StripSizing.NORMAL -> AdSize.BANNER
            StripSizing.INLINE -> AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(
                activity,
                getWidthDp(activity)
            )

            StripSizing.ADAPTIVE -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                activity,
                getWidthDp(activity)
            )
        }
    }

    private fun getWidthDp(activity: Activity): Int {
        val display = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(display)
        return (display.widthPixels / display.density).toInt()
    }

    private fun fetchFacebookBanner(
        activity: Activity,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        observer: StripAdObserver?,
        disableInternalFallback: Boolean = false
    ) {
        val pref = AdPrefsStore.getInstance(activity)
        val fbId = pref.getString("faceB_BannerAds")

        if (fbId.isNullOrEmpty()) {
            if (disableInternalFallback) {
                observer?.onAdFailed()
            } else {
                OwnAdsKeeper().fetchCustomAd(
                    activity,
                    container,
                    OwnAdsKeeper.OwnAdFormat.BANNER
                )
            }
            return
        }

        hide(container)

        shimmer?.startShimmer()
        shimmer?.visibility = View.VISIBLE
        container.removeAllViews()
        shimmer?.let { container.addView(it) }

        if (facebookBanner == null) facebookBanner =
            FbAdView(activity, fbId, com.facebook.ads.AdSize.BANNER_HEIGHT_50)

        AdPulse.req(activity, "banner", AdPulse.FACEBOOK)
        facebookBanner?.loadAd(
            facebookBanner!!.buildLoadAdConfig()
                .withAdListener(object : com.facebook.ads.AdListener {

                    override fun onAdLoaded(ad: Ad?) {
                        AdPulse.loaded(activity, "banner", AdPulse.FACEBOOK)
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.GONE
                        container.removeAllViews()

                        container.addView(facebookBanner)
                        container.visibility = View.VISIBLE
                        observer?.onAdLoaded()
                        activity.trackEvent("facebook_banner_load")
                    }

                    override fun onError(
                        ad: Ad?,
                        error: AdError?
                    ) {
                        AdPulse.loadFailed(activity, "banner", error?.errorCode, AdPulse.FACEBOOK)
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.GONE
                        observer?.onAdFailed()
                        Log.e("StripSponsor", "FB Banner Failed: ${error?.errorMessage}")
                        if (!disableInternalFallback) {
                            OwnAdsKeeper().fetchCustomAd(
                                activity,
                                container,
                                OwnAdsKeeper.OwnAdFormat.BANNER
                            )
                        }
                    }

                    override fun onAdClicked(ad: Ad?) {
                    }

                    override fun onLoggingImpression(ad: Ad?) {
                        AdPulse.impression(activity, "banner", AdPulse.FACEBOOK)
                    }
                })
                .build()
        )
    }

    private fun fallbackToFBOrCustom(
        activity: Activity,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        observer: StripAdObserver?
    ) {
        val pref = AdPrefsStore.getInstance(activity)
        if (pref.getBoolean("IsFail_FB")) {

            fetchFacebookBanner(activity, container, shimmer, observer)
        } else {

            shimmer?.startShimmer()
            shimmer?.visibility = View.VISIBLE
            OwnAdsKeeper().fetchCustomAd(
                activity,
                container,
                OwnAdsKeeper.OwnAdFormat.BANNER
            )
        }
    }

    private fun hide(container: FrameLayout) {
        container.removeAllViews()
        container.visibility = View.GONE
    }

}

