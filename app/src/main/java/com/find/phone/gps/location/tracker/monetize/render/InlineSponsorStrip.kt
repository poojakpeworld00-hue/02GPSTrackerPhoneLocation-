package com.find.phone.gps.location.tracker.monetize.render

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.engine.AdTallyKeeper.nativeBannerCounter
import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.monetize.engine.TAG_EVENT
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.insights.AdPulse
import com.find.phone.gps.location.tracker.insights.ScreenInsights
import com.find.phone.gps.location.tracker.insights.ScreenTokens
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity
import com.find.phone.gps.location.tracker.databinding.AdMNativeStripBinding
import com.find.phone.gps.location.tracker.databinding.AdGNativeStripBinding
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdOptionsView
import com.facebook.ads.NativeAdListener
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

class InlineSponsorStrip {
    companion object {
        private var nativeAdBanner: NativeAd? = null

        /** A native banner is cached and ready to show. */
        fun hasPreloadedNativeBanner(): Boolean = nativeAdBanner != null
    }

    fun fetchNativeBannerAds(activity: Activity) {
        val adsPref = AdPrefsStore.getInstance(activity)
        if (!adsPref.getBoolean("IsAdsON")) return
        // Firebase "NativeBanner" master switch — disable native banner loading
        if (!adsPref.getBoolean("NativeBanner")) return


        when (AdFormatKind.fromString(adsPref.getString("IsAdType"))) {
            AdFormatKind.GOOGLE -> {
                val adUnitId = adsPref.getString("googleNative") ?: return

                val adLoader = AdLoader.Builder(activity, adUnitId).forNativeAd { ad ->
                    AdPulse.loaded(activity, "native_banner")
                    nativeAdBanner?.destroy()
                    nativeAdBanner = ad
                    try {
                        activity.trackEvent("NativeBanner_Load")
                    } catch (e: Exception) {
                    }

                    Log.d("InlineSponsorStrip", "Ad loaded successfully")
                }.withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AdPulse.loadFailed(activity, "native_banner", error.code, AdPulse.GOOGLE)
                        Log.e("InlineSponsorStrip", "Ad failed to load: ${error.message}")
                        nativeAdBanner = null
                        // No retry logic

                        try {
                            activity.trackEvent("NativeBanner_fail")
                        } catch (e: Exception) {
                        }
                    }

                    // The cached ad may be shown on a later screen than the one that loaded it,
                    // so these resolve the screen in front rather than the loading activity.
                    override fun onAdImpression() {
                        super.onAdImpression()
                        AdPulse.impression(activity.applicationContext, "native_banner")
                    }

                    override fun onAdClicked() {
                        super.onAdClicked()
                        if (ScreenInsights.currentToken() == ScreenTokens.tokenFor(LocalePickActivity::class.java)) {
                            AdPulse.clicked(activity.applicationContext, "native_banner")
                        }
                    }
                }).withNativeAdOptions(NativeAdOptions.Builder().build()).build()

                AdPulse.req(activity, "native_banner")
                adLoader.loadAd(AdRequest.Builder().build())
            }

            AdFormatKind.FACEBOOK -> {
                // FB ad type → directly try FB
                Log.e(TAG_EVENT, "AdType FaceBook NOt Pre load Google Native")
                return
            }

            AdFormatKind.UNKNOWN, AdFormatKind.CUSTOM -> {
                // Custom ad type
                Log.e(TAG_EVENT, "AdType Custom NOt Pre load Google Native")
                return
            }
        }


    }

    fun presentNativeBannerNative(
        context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout? = null
    ) {
        Log.e("NativeAds", "Google Show: nativeAd")
        val adsPref = AdPrefsStore.getInstance(context)

        // 🔥 CRASH FIX 1: Activity lifecycle safety
        if (context.isFinishing || context.isDestroyed) return


        if (!isNetworkConnected(context)
            || !adsPref.getBoolean("IsAdsON")
            || !adsPref.getBoolean("NativeBanner")
            // Per-screen visibility: a screen whose ScreenAds entry is off gets no native banner.
            || !PageSponsorSettings.resolve(context, context.javaClass.simpleName).show
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            return
        }

        if (nativeBannerCounter < adsPref.getInt("MidNativeCounter")) {
            nativeBannerCounter += 1
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            return
        }

        nativeBannerCounter = 0

        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true

        when (AdFormatKind.fromString(adsPref.getString("IsAdType"))) {
            AdFormatKind.GOOGLE -> {

                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (nativeAdBanner != null) {
                            val binding = AdGNativeStripBinding.inflate(context.layoutInflater)
                            wireGoogleNativeAd(nativeAdBanner!!, binding, context)

                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false

                            layout.addView(binding.root)
                            nativeAdBanner = null
                            fetchNativeBannerAds(context)
                            return@post
                        } else {
                            // Google failed → FB fallback or Custom
                            if (adsPref.getBoolean("IsFail_FB")) {
                                // With the shimmer: this branch renders the ad itself, so
                                // without it nothing ever takes the placeholder down.
                                presentFBNativeBannerFallback(context, layout, shimmer)
                            } else {
                                layout.removeAllViews()
                                shimmer?.stopShimmer()
                                shimmer?.isVisible = false
                                OwnAdsKeeper().fetchCustomAd(
                                    context, layout, OwnAdsKeeper.OwnAdFormat.BANNER
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("InlineSponsorStrip", "Google NativeBanner failed: ${e.message}")
                        // Same reason as InlineSponsor.wipeToEmpty: the ad view is built
                        // before the shimmer comes down, so a throw here would otherwise
                        // leave the placeholder running over an empty frame.
                        runCatching {
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.removeAllViews()
                        }
                    }
                }
            }

            AdFormatKind.FACEBOOK -> {
                presentFBNativeBannerFallback(context, layout, shimmer)
            }

            AdFormatKind.UNKNOWN, AdFormatKind.CUSTOM -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                OwnAdsKeeper().fetchCustomAd(
                    context, layout, OwnAdsKeeper.OwnAdFormat.BANNER
                )
            }
        }
    }

    private fun wireGoogleNativeAd(
        nativeAd: NativeAd, binding: AdGNativeStripBinding, context: Activity
    ) {
        // Log load
        context.trackEvent("NativeBanner_Show_Google")

        if (BuildConfig.DEBUG) EarningsTracker.simulateDebugRevenue(context)

        nativeAd.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(context, it)
        }

        binding.apply {
            mainNativeadViewVw.headlineView = adHeadlineVw
            mainNativeadViewVw.bodyView = adBodyVw
            mainNativeadViewVw.callToActionView = adCallToActionVw
            mainNativeadViewVw.iconView = adAppIconVw

            (adHeadlineVw as TextView).text = nativeAd.headline
            // Remote Config ad colours (theme fallback) are applied in InlineAdSkin.bind.


            if (nativeAd.body != null) {
                adBodyVw.visibility = View.VISIBLE
                (adBodyVw as TextView).text = nativeAd.body
            } else {
                adBodyVw.visibility = View.GONE
            }

            if (nativeAd.icon != null) {
                adAppIconVw.visibility = View.VISIBLE
                (adAppIconVw as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            } else {
                adAppIconVw.visibility = View.GONE
            }

            if (nativeAd.callToAction != null) {
                adCallToActionVw.visibility = View.VISIBLE
                (adCallToActionVw as TextView).text = nativeAd.callToAction

            } else {
                adCallToActionVw.visibility = View.GONE
            }

            InlineAdSkin.bind(mainNativeadViewVw, nativeAd)
            mainNativeadViewVw.setNativeAd(nativeAd)
        }
    }

    fun safeParseColor(colorString: String?, defaultColor: String): Int {
        return try {
            if (!colorString.isNullOrBlank()) {
                Color.parseColor(colorString)
            } else {
                Color.parseColor(defaultColor)
            }
        } catch (e: IllegalArgumentException) {
            Color.parseColor(defaultColor)
        }
    }


    private fun presentFBNativeBannerFallback(
        context: Activity, layout: FrameLayout, shimmer: ShimmerFrameLayout? = null
    ) {
        val adsPref = AdPrefsStore.getInstance(context)
        val fbId = adsPref.getString("faceB_NativeBannerAds")

        if (fbId.isNullOrEmpty()) {
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            OwnAdsKeeper().fetchCustomAd(
                context, layout, OwnAdsKeeper.OwnAdFormat.BANNER
            )
            return
        }

        val fbNative = com.facebook.ads.NativeAd(context, fbId)
        AdPulse.req(context, "native_banner", AdPulse.FACEBOOK)
        fbNative.loadAd(
            fbNative.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                override fun onMediaDownloaded(ad: Ad?) {
                    shimmer?.stopShimmer()
                    shimmer?.isVisible = false
                    layout.removeAllViews()
                    expandFbNativeBAnnerAd(fbNative, layout, context)
                    context.trackEvent("NativeBAnner_FB")
                }

                override fun onError(ad: Ad?, adError: AdError?) {
                    AdPulse.loadFailed(context, "native_banner", adError?.errorCode, AdPulse.FACEBOOK)
                    shimmer?.stopShimmer()
                    shimmer?.isVisible = false
                    Log.e("NativeAds", "FB MidNative failed: ${adError?.errorMessage}")
                    OwnAdsKeeper().fetchCustomAd(
                        context, layout, OwnAdsKeeper.OwnAdFormat.BANNER
                    )
                }

                override fun onAdLoaded(ad: Ad?) {
                    if (fbNative !== ad) return
                    AdPulse.loaded(context, "native_banner", AdPulse.FACEBOOK)
                    fbNative.downloadMedia()
                }

                override fun onAdClicked(ad: Ad?) {
                    if (context is LocalePickActivity) AdPulse.clicked(context, "native_banner", AdPulse.FACEBOOK)
                }
                override fun onLoggingImpression(ad: Ad?) {
                    AdPulse.impression(context, "native_banner", AdPulse.FACEBOOK)
                }
            }).build()
        )
    }

    fun expandFbNativeBAnnerAd(
        nativeAd: com.facebook.ads.NativeAd,
        viewGroup: ViewGroup,
        activity: Activity,
        adSize: String? = null
    ) {
        // ✅ Make sure container is visible
        viewGroup.isVisible = true

        // Unregister any old ad view
        nativeAd.unregisterView()

        // ✅ Inflate layout with ViewBinding
        val binding =
            AdMNativeStripBinding.inflate(LayoutInflater.from(activity), viewGroup, false)

        // Clear old views and add new ad view
        viewGroup.removeAllViews()
        viewGroup.addView(binding.root)

        // ✅ Add AdChoicesView
        val adOptionsView = AdOptionsView(activity, nativeAd, binding.nativviewVw)
        binding.adChoicesContainerVw.removeAllViews()
        binding.adChoicesContainerVw.addView(adOptionsView, 0)

        // ✅ Bind ad data to views
        binding.promoAdTitle.text = nativeAd.advertiserName
        binding.promoAdSocialContext.text = nativeAd.adSocialContext
        binding.promoAdSponsoredLabel.text = nativeAd.sponsoredTranslation

        val bgColor = AdPrefsStore.getInstance(activity).getString("NativeBgColor")
        val btnColor = AdPrefsStore.getInstance(activity).getString("NativebtnColor")
        val txtColor = AdPrefsStore.getInstance(activity).getString("NativetxtColor") ?: "#000000"
        val btntxtColor =
            AdPrefsStore.getInstance(activity).getString("NativebtntxtColor") ?: "#000000"

        binding.promoAdTitle.setTextColor(Color.parseColor(txtColor))
        binding.promoAdSocialContext.setTextColor(Color.parseColor(txtColor))
        binding.promoAdSponsoredLabel.setTextColor(Color.parseColor(txtColor))

        binding.nativviewVw.backgroundTintList =
            ColorStateList.valueOf(safeParseColor(bgColor, "#FFFFFF"))

        SponsorButtonStyle.apply(binding.promoAdCallToAction as TextView)
        (binding.promoAdCallToAction as TextView).apply {
            setTextColor(Color.parseColor(btntxtColor))
        }

        if (nativeAd.hasCallToAction()) {
            binding.promoAdCallToAction.text = nativeAd.adCallToAction
            binding.promoAdCallToAction.isVisible = true
        } else {
            binding.promoAdCallToAction.isVisible = false
        }

        // ✅ Register clickable views
        val clickableViews = listOf(binding.promoAdTitle, binding.promoAdCallToAction)

        nativeAd.registerViewForInteraction(
            binding.root, binding.promoIconView, clickableViews
        )
    }

}
