package com.find.phone.gps.location.tracker.monetize.render

import android.R.attr.visibility
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind.*
import com.find.phone.gps.location.tracker.monetize.engine.AdTallyKeeper.nativeCounter
import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.insights.AdPulse
import com.find.phone.gps.location.tracker.insights.ScreenInsights
import com.find.phone.gps.location.tracker.insights.ScreenTokens
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity
import com.find.phone.gps.location.tracker.databinding.AdMNativeMidBinding
import com.find.phone.gps.location.tracker.databinding.AdMNativeBinding
import com.find.phone.gps.location.tracker.databinding.AdGNativeBigBinding
import com.find.phone.gps.location.tracker.databinding.AdGNativeBigTopBinding
import com.find.phone.gps.location.tracker.databinding.AdGNativeMidAltBinding
import com.find.phone.gps.location.tracker.databinding.AdGNativeMidBinding
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
import kotlin.collections.plusAssign
import kotlin.text.compareTo

class InlineSponsor() {
    interface InlineAdCallback {
        fun onNativeAdLoaded()
        fun onNativeAdFailed()
    }

    companion object {
        private var nativeAd: NativeAd? = null

        /**
         * A load is in flight. The pool is one static slot and every `show*` kicks a refill,
         * so without this a surface that opens twice in a row (the app drawer, the left panel)
         * stacks concurrent AdLoader requests that each overwrite — and destroy — the last
         * one's result.
         */
        @Volatile
        private var loadingSince = 0L

        /**
         * Observers that asked while a load was already in flight. Without them a screen that
         * opens mid-refill (onboarding steps shown seconds apart all share this one slot) was
         * turned away and never told when the ad landed, so its frame stayed empty.
         */
        private val waiters = mutableListOf<InlineAdCallback>()

        private fun settleWaiters(loaded: Boolean) {
            val pending = waiters.toList()
            waiters.clear()
            pending.forEach {
                runCatching { if (loaded) it.onNativeAdLoaded() else it.onNativeAdFailed() }
            }
        }

        /**
         * How long a request may be considered in flight. AdLoader always answers one of its
         * two callbacks, but a latched flag here would kill native ads process-wide, so the
         * guard expires rather than trusting that.
         */
        private const val LOAD_TIMEOUT_MS = 60_000L

        private val isLoading: Boolean
            get() = loadingSince != 0L &&
                    System.currentTimeMillis() - loadingSince < LOAD_TIMEOUT_MS

        /**
         * Whether a native is in hand right now.
         *
         * Callers that re-render an already-filled frame need this: rendering consumes the
         * pooled ad, so asking again before the refill lands would drop through to the
         * fallback path and replace a good ad with a custom one. See
         * [com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.renewSlot].
         */
        fun hasPreloadedNative(): Boolean = nativeAd != null
    }

    /**
     * Takes the shimmer down and clears whatever half-built view is in the frame.
     *
     * Every render path below builds the ad view FIRST and stops the shimmer only once that
     * has succeeded — so anything thrown while binding a template (a null asset, a recycled
     * NativeAd, an inflate failure) used to land in a catch that only logged, leaving the
     * shimmer running over an empty frame for as long as the screen lived. The launcher's
     * side panels are where that shows up worst: they are never recreated, so the placeholder
     * stays until the process dies.
     */
    private fun wipeToEmpty(layout: FrameLayout, shimmer: ShimmerFrameLayout?) {
        runCatching {
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            layout.removeAllViews()
        }
    }

    private fun Activity.isActivityDestroyedCompat(): Boolean {
        if (isFinishing) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
    }

    fun fetchNativeADs(context: Activity, observer: InlineAdCallback? = null) {
        val adsPreference = AdPrefsStore.getInstance(context)
        // Ads toggle and type check
        if (!adsPreference.getBoolean("IsAdsON") || AdFormatKind.fromString(adsPreference.getString("IsAdType")) != AdFormatKind.GOOGLE) {
            observer?.onNativeAdFailed()
            return
        }
        // Firebase "NativeBanner" master switch — disable native ad loading
        if (!adsPreference.getBoolean("NativeAd")) {
            observer?.onNativeAdFailed()
            return
        }
        // Screen-wise aware: resolves to ScreenAds.default native id, or the
        // global googleNative when screen_wise_ad is off.
        val adUnit = PageSponsorSettings.nativeAdUnitId(context)
        if (adUnit.isEmpty()) {
            observer?.onNativeAdFailed()
            return
        }
        // One request at a time — see [isLoading]. A caller waiting on the result is told when
        // the in-flight one settles instead of being dropped.
        if (isLoading) {
            Log.d("NativeAds", "load already in flight — waiting on it")
            observer?.let { waiters += it }
            return
        }
        loadingSince = System.currentTimeMillis()
        val adLoader =
            AdLoader.Builder(context, adUnit)
                .forNativeAd { nativeAds ->
                    AdPulse.loaded(context, "native")
                    // Always cache the ad — even when the activity that
                    // triggered this load is destroyed by the time the ad
                    // lands. The cache is a static field; it survives
                    // activity transitions and can be shown by the next
                    // caller. Previously this branch destroyed the ad,
                    // which is why "1 load → then nothing" was the symptom:
                    // the splash refill landed after splash was gone, got
                    // destroyed, and every subsequent show found null.
                    nativeAd?.destroy()
                    nativeAd = nativeAds
                    loadingSince = 0L
                    // Screens that asked mid-load check their own lifecycle in their callback.
                    settleWaiters(loaded = true)

                    if (context.isActivityDestroyedCompat()) {
                        // Originating activity is gone — cache only, skip
                        // observer/log calls that need a live context.
                        return@forNativeAd
                    }

                    observer?.onNativeAdLoaded()
                    try {
                        context.trackEvent("NativeAds_load")
                    } catch (e: Exception) {
                    }


                    Log.e("NativeAds", "Google Load: nativeAd")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        AdPulse.loadFailed(context, "native", loadAdError.code, AdPulse.GOOGLE)
                        loadingSince = 0L
                        settleWaiters(loaded = false)
                        if (context.isActivityDestroyedCompat()) return
                        observer?.onNativeAdFailed()
                        Log.e(
                            "NativeAds",
                            "Google onAdFailedToLoad:nativeAd ${loadAdError.message}"
                        )
                        try {
                            context.trackEvent("NativeAds_Fail")
                        } catch (e: Exception) {
                        }
                        nativeAd = null
                    }

                    // The cached ad may be shown on a later screen than the one that loaded it,
                    // so these resolve the screen in front rather than the loading activity.
                    override fun onAdImpression() {
                        super.onAdImpression()
                        AdPulse.impression(context.applicationContext, "native")
                    }

                    override fun onAdClicked() {
                        super.onAdClicked()
                        if (ScreenInsights.currentToken() == ScreenTokens.tokenFor(LocalePickActivity::class.java)) {
                            AdPulse.clicked(context.applicationContext, "native")
                        }
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

        AdPulse.req(context, "native")
        adLoader.loadAd(AdRequest.Builder().build())
    }

    //================================================================================BigNAtive
    fun presentBigNative(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        isButtonTop: Boolean? = false,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {

        val adsPreference = AdPrefsStore.getInstance(context)

        // 🔥 1. Activity lifecycle safety
        if (context.isFinishing || context.isDestroyed) return

        // 🔥 2. Network + Ads ON + InlineSponsorStrip master switch
        if (!isNetworkConnected(context)
            || !adsPreference.getBoolean("IsAdsON")
            || !adsPreference.getBoolean("NativeAd")
            // Per-screen visibility: a screen whose ScreenAds entry is off gets no native.
            || !PageSponsorSettings.resolve(context, context.javaClass.simpleName).show
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        // 🔥 3. Counter logic
        if (nativeCounter < adsPreference.getInt("NativeCounter")) {
            nativeCounter++
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }
        nativeCounter = 0


        // 🔥 4. UI loading state
        layout.visible()
        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()

        when (AdFormatKind.fromString(adsPreference.getString("IsAdType"))) {
            AdFormatKind.GOOGLE -> {
                layout.post {  // 🔥 UI thread safe
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post

                        if (nativeAd != null) {

                            val rootView: View = if (isButtonTop == true) {
                                val binding =
                                    AdGNativeBigTopBinding.inflate(context.layoutInflater)

                                bigNativeTemplateTop(nativeAd!!, binding, context)
                                binding.root
                            } else {
                                val binding =
                                    AdGNativeBigBinding.inflate(context.layoutInflater)

                                bigNativeTemplate(nativeAd!!, binding, context)
                                binding.root
                            }

                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(rootView)

                            context.trackEvent("NativeAds_showBigNative_Google")

                            if (BuildConfig.DEBUG) {
                                EarningsTracker.simulateDebugRevenue(context)
                            }

                            nativeAd?.setOnPaidEventListener {
                                EarningsTracker.trackPaidEvent(context, it)
                            }

                            nativeAd = null
                            fetchNativeADs(context)

                            return@post
                        }

                        // 🔥 GOOGLE FAIL → fallback
                        if (adsPreference.getBoolean("IsFail_FB")) {
                            presentFBNativeFallback(context, layout, imageView, shimmer)
                        } else {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false

                            OwnAdsKeeper().fetchCustomAd(
                                context,
                                layout,
                                OwnAdsKeeper.OwnAdFormat.BIG_NATIVE,
                                imageView
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("NativeAds", "Google BigNative crash", e)
                        wipeToEmpty(layout, shimmer)
                    }
                }
            }

            AdFormatKind.FACEBOOK -> {
                presentFBNativeFallback(context, layout, imageView)
            }

            AdFormatKind.UNKNOWN, AdFormatKind.CUSTOM -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                OwnAdsKeeper().fetchCustomAd(
                    context,
                    layout,
                    OwnAdsKeeper.OwnAdFormat.BIG_NATIVE,
                    imageView
                )
            }
        }
    }

    private fun bigNativeTemplate(
        nativeAd: NativeAd,
        binding: AdGNativeBigBinding,
        context: Activity
    ) {
        binding.apply {

            binding.mainNativeadViewVw.mediaView = adMediaVw
            binding.mainNativeadViewVw.headlineView = adHeadlineVw
            binding.mainNativeadViewVw.bodyView = adBodyVw
            binding.mainNativeadViewVw.callToActionView = adCallToActionVw
            binding.mainNativeadViewVw.iconView = adAppIconVw

            (binding.mainNativeadViewVw.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadViewVw.mediaView?.mediaContent = nativeAd.mediaContent


            binding.mainNativeadViewVw.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadViewVw.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadViewVw.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.callToActionView as TextView).text = nativeAd.callToAction
            }


            // Remote Config ad colours (with the layout's own theme as the fallback), the rating line
            // and the CTA glow — the reference app's InlineAdSkin.
            InlineAdSkin.bind(binding.mainNativeadViewVw, nativeAd)
            binding.mainNativeadViewVw.setNativeAd(nativeAd)
        }
    }

    private fun bigNativeTemplateTop(
        nativeAd: NativeAd,
        binding: AdGNativeBigTopBinding,
        context: Activity
    ) {
        binding.apply {

            binding.mainNativeadViewVw.mediaView = adMediaVw
            binding.mainNativeadViewVw.headlineView = adHeadlineVw
            binding.mainNativeadViewVw.bodyView = adBodyVw
            binding.mainNativeadViewVw.callToActionView = adCallToActionVw
            binding.mainNativeadViewVw.iconView = adAppIconVw

            (binding.mainNativeadViewVw.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadViewVw.mediaView?.mediaContent = nativeAd.mediaContent


            binding.mainNativeadViewVw.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadViewVw.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadViewVw.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.callToActionView as TextView).text = nativeAd.callToAction
            }


            // Remote Config ad colours (with the layout's own theme as the fallback), the rating line
            // and the CTA glow — the reference app's InlineAdSkin.
            InlineAdSkin.bind(binding.mainNativeadViewVw, nativeAd)
            binding.mainNativeadViewVw.setNativeAd(nativeAd)
        }
    }


    // Helper function for FB fallback
    private fun presentFBNativeFallback(
        context: Activity,
        layout: FrameLayout,
        imageView: ImageView? = null,
        shimmer: ShimmerFrameLayout? = null
    ) {
        val adsPref = AdPrefsStore.getInstance(context)
        val fbId = adsPref.getString("faceB_NativeAds")

        if (fbId.isNullOrEmpty()) {
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            OwnAdsKeeper().fetchCustomAd(
                context,
                layout,
                OwnAdsKeeper.OwnAdFormat.BIG_NATIVE,
                imageView
            )
            return
        }

        val fbNative = com.facebook.ads.NativeAd(context, fbId)

        AdPulse.req(context, "native", AdPulse.FACEBOOK)
        fbNative.loadAd(
            fbNative.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                override fun onMediaDownloaded(ad: Ad?) {
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        layout.findFocus()?.clearFocus()
                        layout.removeAllViews()
                        expandFbNativeAd(fbNative, layout, context)
                    }
                }

                override fun onError(ad: Ad?, adError: AdError?) {
                    AdPulse.loadFailed(context, "native", adError?.errorCode, AdPulse.FACEBOOK)
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        layout.findFocus()?.clearFocus()
                        OwnAdsKeeper().fetchCustomAd(
                            context,
                            layout,
                            OwnAdsKeeper.OwnAdFormat.BIG_NATIVE,
                            imageView
                        )
                    }
                }

                override fun onAdLoaded(ad: Ad?) {
                    if (fbNative !== ad) return
                    AdPulse.loaded(context, "native", AdPulse.FACEBOOK)
                    context.trackEvent("NativeAds_showBigNative_FB_Load")
                    fbNative.downloadMedia()
                }

                override fun onAdClicked(ad: Ad?) {
                    if (context is LocalePickActivity) AdPulse.clicked(context, "native", AdPulse.FACEBOOK)
                }
                override fun onLoggingImpression(ad: Ad?) {
                    AdPulse.impression(context, "native", AdPulse.FACEBOOK)
                }
            }).build()
        )
    }

    fun expandFbNativeAd(
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
        val binding = AdMNativeBinding.inflate(LayoutInflater.from(activity), viewGroup, false)

        // Clear old views and add new ad view
        viewGroup.removeAllViews()
        viewGroup.addView(binding.root)

        // ✅ Add AdChoicesView
        val adOptionsView = AdOptionsView(activity, nativeAd, binding.nativviewVw)
        binding.adChoicesContainerVw.removeAllViews()
        binding.adChoicesContainerVw.addView(adOptionsView, 0)
        val bgColor = AdPrefsStore.getInstance(activity).getString("NativeBgColor")
        val btnColor = AdPrefsStore.getInstance(activity).getString("NativebtnColor")
        val txtColor =
            AdPrefsStore.getInstance(activity).getString("NativetxtColor") ?: "#000000"
        val btntxtColor =
            AdPrefsStore.getInstance(activity).getString("NativebtntxtColor") ?: "#000000"

        binding.promoAdTitle.setTextColor(Color.parseColor(txtColor))
        binding.promoAdSocialContext.setTextColor(Color.parseColor(txtColor))
        binding.promoAdSponsoredLabel.setTextColor(Color.parseColor(txtColor))
        binding.promoAdBody.setTextColor(Color.parseColor(txtColor))

        binding.nativviewVw.backgroundTintList =
            ColorStateList.valueOf(safeParseColor(bgColor, "#FFFFFF"))

        SponsorButtonStyle.apply(binding.promoAdCallToAction as TextView)
        (binding.promoAdCallToAction as TextView).apply {
            setTextColor(Color.parseColor(btntxtColor))
        }
        // ✅ Bind ad data to views
        binding.promoAdTitle.text = nativeAd.advertiserName
        binding.promoAdBody.text = nativeAd.adBodyText
        binding.promoAdSocialContext.text = nativeAd.adSocialContext
        binding.promoAdSponsoredLabel.text = nativeAd.sponsoredTranslation

        if (nativeAd.hasCallToAction()) {
            binding.promoAdCallToAction.text = nativeAd.adCallToAction
            binding.promoAdCallToAction.isVisible = true
        } else {
            binding.promoAdCallToAction.isVisible = false
        }

        // ✅ Register clickable views
        val clickableViews = listOf(binding.promoAdTitle, binding.promoAdCallToAction)

        nativeAd.registerViewForInteraction(
            binding.root, binding.promoAdMedia, binding.promoAdIcon, clickableViews
        )
    }

    //================================================================================BigNAtive
//================================================================================MidNAtive
    fun presentMidNative(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPref = AdPrefsStore.getInstance(context)
        // 🔥 1. Activity lifecycle safety
        if (context.isFinishing || context.isDestroyed) return

        // Check network & ad toggle + InlineSponsorStrip master switch
        if (!isNetworkConnected(context)
            || !adsPref.getBoolean("IsAdsON")
            || !adsPref.getBoolean("NativeAd")
            || !PageSponsorSettings.resolve(context, context.javaClass.simpleName).show
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        // MidNative counter logic
        if (nativeCounter < adsPref.getInt("MidNativeCounter")) {
            nativeCounter += 1
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }
        nativeCounter = 0

        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()
        layout.visible()


        when (AdFormatKind.fromString(adsPref.getString("IsAdType"))) {
            AdFormatKind.GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (nativeAd != null) {
                            val binding = AdGNativeMidBinding.inflate(context.layoutInflater)
                            MidNativeTemplate(
                                nativeAd!!,
                                binding,
                                context
                            )

                            layout.removeAllViews()
                            // Stop shimmer before adding real ad
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(binding.root)

                            // Log load
                            context.trackEvent("NativeAds_showMidNative_Google")

                            if (BuildConfig.DEBUG) EarningsTracker.simulateDebugRevenue(context)

                            nativeAd!!.setOnPaidEventListener {
                                EarningsTracker.trackPaidEvent(context, it)
                            }

                            nativeAd = null
                            fetchNativeADs(context) // preload next Google ad
                            return@post
                        }
                        // Google failed → fallback
                        if (adsPref.getBoolean("IsFail_FB")) {
                            presentMidFBNativeFallback(context, layout, shimmer, imageView)
                        } else {
                            layout.removeAllViews()
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            OwnAdsKeeper().fetchCustomAd(
                                context,
                                layout,
                                OwnAdsKeeper.OwnAdFormat.MID_NATIVE
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("MidNativeAds", "Google MidNative failed: ${e.message}")
                        wipeToEmpty(layout, shimmer)
                    }
                }


            }

            AdFormatKind.FACEBOOK -> {

                presentMidFBNativeFallback(context, layout, shimmer, imageView)
            }

            AdFormatKind.CUSTOM, AdFormatKind.UNKNOWN -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                OwnAdsKeeper().fetchCustomAd(
                    context,
                    layout,
                    OwnAdsKeeper.OwnAdFormat.MID_NATIVE
                )
            }
        }
    }

    private fun presentMidFBNativeFallback(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null
    ) {
        val adsPref = AdPrefsStore.getInstance(context)
        val fbId = adsPref.getString("faceB_NativeAds")

        if (fbId.isNullOrEmpty()) {
            layout.removeAllViews()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            OwnAdsKeeper().fetchCustomAd(
                context,
                layout,
                OwnAdsKeeper.OwnAdFormat.MID_NATIVE
            )
            return
        }

        val fbNative = com.facebook.ads.NativeAd(context, fbId)
        AdPulse.req(context, "native", AdPulse.FACEBOOK)
        fbNative.loadAd(
            fbNative.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                override fun onMediaDownloaded(ad: Ad?) {
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        layout.findFocus()?.clearFocus()
                        layout.removeAllViews()
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        expandFbMidNativeAd(fbNative, layout, context)
                        context.trackEvent("NativeAds_showMid_FB")
                    }
                }

                override fun onError(ad: Ad?, adError: AdError?) {
                    AdPulse.loadFailed(context, "native", adError?.errorCode, AdPulse.FACEBOOK)
                    if (context.isActivityDestroyedCompat()) return
                    layout.post {
                        if (context.isActivityDestroyedCompat()) return@post
                        Log.e("NativeAds", "FB MidNative failed: ${adError?.errorMessage}")
                        layout.findFocus()?.clearFocus()
                        layout.removeAllViews()
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        OwnAdsKeeper().fetchCustomAd(
                            context,
                            layout,
                            OwnAdsKeeper.OwnAdFormat.MID_NATIVE
                        )
                    }
                }

                override fun onAdLoaded(ad: Ad?) {
                    if (fbNative !== ad) return
                    AdPulse.loaded(context, "native", AdPulse.FACEBOOK)
                    fbNative.downloadMedia()
                }

                override fun onAdClicked(ad: Ad?) {
                    if (context is LocalePickActivity) AdPulse.clicked(context, "native", AdPulse.FACEBOOK)
                }
                override fun onLoggingImpression(ad: Ad?) {
                    AdPulse.impression(context, "native", AdPulse.FACEBOOK)
                }
            }).build()
        )
    }

    fun expandFbMidNativeAd(
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
        val binding = AdMNativeMidBinding.inflate(LayoutInflater.from(activity), viewGroup, false)

        // Clear old views and add new ad view
        viewGroup.removeAllViews()
        viewGroup.addView(binding.root)

        // ✅ Add AdChoicesView
        val adOptionsView = AdOptionsView(activity, nativeAd, binding.nativviewVw)
        binding.adChoicesContainerVw.removeAllViews()
        binding.adChoicesContainerVw.addView(adOptionsView, 0)

        val bgColor = AdPrefsStore.getInstance(activity).getString("NativeBgColor")
        val btnColor = AdPrefsStore.getInstance(activity).getString("NativebtnColor")
        val txtColor =
            AdPrefsStore.getInstance(activity).getString("NativetxtColor") ?: "#000000"
        val btntxtColor =
            AdPrefsStore.getInstance(activity).getString("NativebtntxtColor") ?: "#000000"

        binding.promoAdTitle.setTextColor(Color.parseColor(txtColor))
        binding.promoAdSocialContext.setTextColor(Color.parseColor(txtColor))
        binding.promoAdSponsoredLabel.setTextColor(Color.parseColor(txtColor))
        binding.promoAdBody.setTextColor(Color.parseColor(txtColor))

        binding.nativviewVw.backgroundTintList =
            ColorStateList.valueOf(safeParseColor(bgColor, "#FFFFFF"))

        SponsorButtonStyle.apply(binding.promoAdCallToAction as TextView)
        (binding.promoAdCallToAction as TextView).apply {
            setTextColor(Color.parseColor(btntxtColor))
        }

        // ✅ Bind ad data to views
        binding.promoAdTitle.text = nativeAd.advertiserName
        binding.promoAdBody.text = nativeAd.adBodyText
        binding.promoAdSocialContext.text = nativeAd.adSocialContext
        binding.promoAdSponsoredLabel.text = nativeAd.sponsoredTranslation

        if (nativeAd.hasCallToAction()) {
            binding.promoAdCallToAction.text = nativeAd.adCallToAction
            binding.promoAdCallToAction.isVisible = true
        } else {
            binding.promoAdCallToAction.isVisible = false
        }

        // ✅ Register clickable views
        val clickableViews = listOf(binding.promoAdTitle, binding.promoAdCallToAction)

        nativeAd.registerViewForInteraction(
            binding.root, binding.promoAdIcon, clickableViews
        )
    }

    private fun MidNativeTemplate(
        nativeAd: NativeAd,
        binding: AdGNativeMidBinding,
        context: Activity
    ) {
        binding.apply {
            binding.mainNativeadViewVw.headlineView = adHeadlineVw
            binding.mainNativeadViewVw.bodyView = adBodyVw
            binding.mainNativeadViewVw.callToActionView = adCallToActionVw
            binding.mainNativeadViewVw.iconView = adAppIconVw

            (binding.mainNativeadViewVw.headlineView as TextView).text = nativeAd.headline


            binding.mainNativeadViewVw.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadViewVw.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadViewVw.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.callToActionView as TextView).text = nativeAd.callToAction
            }

            // Remote Config ad colours (with the layout's own theme as the fallback), the rating line
            // and the CTA glow — the reference app's InlineAdSkin.
            InlineAdSkin.bind(binding.mainNativeadViewVw, nativeAd)
            binding.mainNativeadViewVw.setNativeAd(nativeAd)
        }
    }

    // Extension helpers
    fun View.visible() {
        this.visibility = View.VISIBLE
    }

    fun View.invisible() {
        this.visibility = View.GONE
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


    fun presentMidNative2(
        context: Activity,
        layout: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
        imageView: ImageView? = null,
        ln: LinearLayout? = null
    ) {
        val adsPref = AdPrefsStore.getInstance(context)
        // 🔥 1. Activity lifecycle safety
        if (context.isFinishing || context.isDestroyed) return

        // Check network & ad toggle
        if (!isNetworkConnected(context) || !adsPref.getBoolean("IsAdsON") || !adsPref.getBoolean("NativeAd")
            || !PageSponsorSettings.resolve(context, context.javaClass.simpleName).show
        ) {
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }

        // MidNative counter logic
        if (nativeCounter < adsPref.getInt("MidNativeCounter")) {
            nativeCounter += 1
            layout.removeAllViews()
            layout.invisible()
            shimmer?.stopShimmer()
            shimmer?.isVisible = false
            imageView?.visible()
            ln?.visible()
            return
        }
        nativeCounter = 0

        shimmer?.startShimmer()
        shimmer?.isVisible = true
        imageView?.gone()
        ln?.gone()
        layout.visible()
        when (AdFormatKind.fromString(adsPref.getString("IsAdType"))) {
            GOOGLE -> {
                layout.post {
                    try {
                        if (context.isFinishing || context.isDestroyed) return@post
                        if (Companion.nativeAd != null) {
                            val binding = AdGNativeMidAltBinding.inflate(context.layoutInflater)
                            MidNativeTemplate2(
                                Companion.nativeAd!!,
                                binding,
                                context
                            )

                            layout.removeAllViews()
                            // Stop shimmer before adding real ad
                            shimmer?.stopShimmer()
                            shimmer?.isVisible = false
                            layout.addView(binding.root)

                            // Log load
                            context.trackEvent("NativeAds_showMidNative2_Google")

                            if (BuildConfig.DEBUG) EarningsTracker.simulateDebugRevenue(context)

                            nativeAd!!.setOnPaidEventListener {
                                EarningsTracker.trackPaidEvent(context, it)
                            }

                            nativeAd = null
                            fetchNativeADs(context) // preload next Google ad
                            return@post
                        }
                        // Google failed → fallback
                        layout.removeAllViews()
                        shimmer?.stopShimmer()
                        shimmer?.isVisible = false
                        OwnAdsKeeper().fetchCustomAd(
                            context,
                            layout,
                            OwnAdsKeeper.OwnAdFormat.MID_NATIVE
                        )
                    } catch (e: Exception) {
                        Log.e("MidNativeAds", "Google MidNative failed: ${e.message}")
                        wipeToEmpty(layout, shimmer)
                    }
                }


            }

            CUSTOM, UNKNOWN -> {
                layout.removeAllViews()
                shimmer?.stopShimmer()
                shimmer?.isVisible = false
                OwnAdsKeeper().fetchCustomAd(
                    context,
                    layout,
                    OwnAdsKeeper.OwnAdFormat.MID_NATIVE
                )
            }

            AdFormatKind.FACEBOOK -> {
                presentMidFBNativeFallback(context, layout, shimmer, imageView)
            }
        }
    }

    private fun MidNativeTemplate2(
        nativeAd: NativeAd,
        binding: AdGNativeMidAltBinding,
        context: Activity
    ) {
        binding.apply {

            binding.mainNativeadViewVw.mediaView = adMediaVw
            binding.mainNativeadViewVw.headlineView = adHeadlineVw
            binding.mainNativeadViewVw.bodyView = adBodyVw
            binding.mainNativeadViewVw.callToActionView = adCallToActionVw
            binding.mainNativeadViewVw.iconView = adAppIconVw

            (binding.mainNativeadViewVw.headlineView as TextView).text = nativeAd.headline
            binding.mainNativeadViewVw.mediaView?.mediaContent = nativeAd.mediaContent


            binding.mainNativeadViewVw.bodyView?.apply {
                visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.bodyView as TextView).text = nativeAd.body
            }

            binding.mainNativeadViewVw.iconView?.apply {
                visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }

            binding.mainNativeadViewVw.callToActionView?.apply {
                visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE
                (binding.mainNativeadViewVw.callToActionView as TextView).text = nativeAd.callToAction
            }


            // Remote Config ad colours (with the layout's own theme as the fallback), the rating line
            // and the CTA glow — the reference app's InlineAdSkin.
            InlineAdSkin.bind(binding.mainNativeadViewVw, nativeAd)
            binding.mainNativeadViewVw.setNativeAd(nativeAd)
        }
    }


}
