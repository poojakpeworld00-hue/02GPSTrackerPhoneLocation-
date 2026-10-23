package com.find.phone.gps.location.tracker.monetize.render

import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.insights.AdPulse
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.SideMenuAdSequence
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.SideMenuAdPlan
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.SideMenuAdFormat
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.R

object SideMenuAdDriver {

    private const val TAG = "SideMenuAdDriver"

    private const val CHAIN_GAP_MS = 400L

    private const val DIRECTLINK_POINTER_SUFFIX = "_directlink_url_ptr"

    private const val RETURN_STALE_MS = 10 * 60 * 1000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val interAds = HashMap<String, InterstitialAd>()
    private val appOpenAds = HashMap<String, AppOpenAd>()
    private val rewardedAds = HashMap<String, RewardedAd>()
    private val nativeAds = HashMap<String, NativeAd>()

    private val loading = HashSet<String>()

    @Volatile
    private var loaderPackage: String? = null

    fun preload(context: Context, flow: SideMenuAdSequence) {
        flow.sequence.forEach { spec ->
            when (spec.type) {
                SideMenuAdFormat.INTER -> fetchInter(context, spec.adUnitId)
                SideMenuAdFormat.APPOPEN -> fetchAppOpen(context, spec.adUnitId)
                SideMenuAdFormat.REWARDED -> fetchRewarded(context, spec.adUnitId)
                SideMenuAdFormat.FULLSCREEN_NATIVE -> fetchNative(context, spec.adUnitId)
                SideMenuAdFormat.DIRECTLINK, SideMenuAdFormat.CUSTOM -> Unit
            }
        }
    }

    private fun isReady(spec: SideMenuAdPlan): Boolean = when (spec.type) {
        SideMenuAdFormat.INTER -> interAds.containsKey(spec.adUnitId)
        SideMenuAdFormat.APPOPEN -> appOpenAds.containsKey(spec.adUnitId)
        SideMenuAdFormat.REWARDED -> rewardedAds.containsKey(spec.adUnitId)
        SideMenuAdFormat.FULLSCREEN_NATIVE -> nativeAds.containsKey(spec.adUnitId)
        SideMenuAdFormat.DIRECTLINK -> spec.adUnitId.isNotBlank() || spec.urls.isNotEmpty()

        SideMenuAdFormat.CUSTOM -> true
    }

    private val waiters = HashMap<String, MutableList<(Boolean) -> Unit>>()

    private fun settle(key: String, ok: Boolean) {
        waiters.remove(key)?.forEach { runCatching { it(ok) } }
    }

    private fun fetchNow(context: Context, spec: SideMenuAdPlan, timeoutMs: Long, then: (Boolean) -> Unit) {
        val prefix = when (spec.type) {
            SideMenuAdFormat.INTER -> "inter"
            SideMenuAdFormat.APPOPEN -> "appopen"
            SideMenuAdFormat.REWARDED -> "rewarded"
            SideMenuAdFormat.FULLSCREEN_NATIVE -> "native"
            else -> return then(isReady(spec))
        }
        var answered = false
        val once: (Boolean) -> Unit = { ok -> if (!answered) { answered = true; then(ok) } }
        waiters.getOrPut("$prefix:${spec.adUnitId}") { mutableListOf() }.add(once)
        mainHandler.postDelayed({ once(false) }, timeoutMs)
        preload(context, SideMenuAdSequence(true, 0, listOf(spec)))
    }

    fun run(activity: Activity, flow: SideMenuAdSequence, pointerKey: String, proceed: () -> Unit) =
        runFor(activity, flow, pointerKey, null, proceed)

    fun runFor(
        activity: Activity,
        flow: SideMenuAdSequence,
        pointerKey: String,
        loaderPackage: String?,
        proceed: () -> Unit,
    ) {
        this.loaderPackage = loaderPackage
        val done = once(proceed)
        val size = flow.sequence.size

        val start = if (flow.showAll || flow.startFromFirst) 0 else {
            AdPrefsStore.getInstance(activity).getInt(pointerKey, 0)
                .let { if (it in 0 until size) it else 0 }
        }
        val order = (0 until size).map { (start + it) % size }
        attempt(activity, flow, order, 0, pointerKey, done)
    }

    private fun attempt(
        activity: Activity,
        flow: SideMenuAdSequence,
        order: List<Int>,
        k: Int,
        pointerKey: String,
        done: () -> Unit,
    ) {
        val size = flow.sequence.size
        if (k >= order.size || activity.isFinishing || activity.isDestroyed) {
            AdPrefsStore.getInstance(activity).putInt(pointerKey, 0)
            log("sequence exhausted — pointer reset, proceeding")
            return done()
        }

        val idx = order[k]
        val spec = flow.sequence[idx]
        if (!isReady(spec)) {

            if (flow.onDemand && spec.adUnitId.isNotBlank() && spec.type != SideMenuAdFormat.DIRECTLINK) {
                log("${spec.type.key} not ready — loading on demand")
                ColdStartLoader.show(activity, loaderPackage)
                fetchNow(activity, spec, flow.loaderTimeoutMs) { ok ->
                    ColdStartLoader.hide()
                    val spent = flow.copy(onDemand = false)
                    if (ok && !activity.isFinishing && !activity.isDestroyed) {

                        attempt(activity, spent, order, k, pointerKey, done)
                    } else {
                        attempt(activity, spent, order, k + 1, pointerKey, done)
                    }
                }
                return
            }
            log("${spec.type.key} not ready — next")
            attempt(activity, flow, order, k + 1, pointerKey, done)
            return
        }

        if (!flow.showAll && !flow.startFromFirst) {
            AdPrefsStore.getInstance(activity).putInt(pointerKey, (idx + 1) % size)
        }
        val next = { attempt(activity, flow, order, k + 1, pointerKey, done) }

        val afterShown: () -> Unit =
            if (flow.showAll) ({ mainHandler.postDelayed({ next() }, CHAIN_GAP_MS) }) else done
        log("attempting ${spec.type.key} (idx=$idx)${if (flow.showAll) " [show_all]" else ""}")

        runCatching {
            when (spec.type) {
                SideMenuAdFormat.INTER -> presentInter(activity, spec.adUnitId, afterShown, next)
                SideMenuAdFormat.APPOPEN -> presentAppOpen(activity, spec.adUnitId, afterShown, next)
                SideMenuAdFormat.REWARDED -> presentRewarded(activity, spec.adUnitId, afterShown, next)
                SideMenuAdFormat.FULLSCREEN_NATIVE -> presentNative(activity, spec.adUnitId, afterShown, next)
                SideMenuAdFormat.CUSTOM -> presentCustom(activity, afterShown, next)
                SideMenuAdFormat.DIRECTLINK -> {
                    if (!unfoldLink(activity, spec, pointerKey + DIRECTLINK_POINTER_SUFFIX)) {
                        next()
                    } else {

                        onReturnTo(activity) { afterShown() }
                    }
                }
            }
        }.onFailure {
            Log.e(TAG, "attempt ${spec.type.key} threw", it)
            next()
        }
    }

    private fun fetchInter(context: Context, unitId: String) {
        if (unitId.isBlank() || interAds.containsKey(unitId) || !loading.add("inter:$unitId")) return
        AdPulse.req(context, "interstitial")
        InterstitialAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdPulse.loaded(context, "interstitial")
                    interAds[unitId] = ad
                    loading.remove("inter:$unitId")
                    settle("inter:$unitId", true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdPulse.loadFailed(context, "interstitial", error.code, AdPulse.GOOGLE)
                    loading.remove("inter:$unitId")
                    settle("inter:$unitId", false)
                    log("inter load failed: ${error.message}")
                }
            }
        )
    }

    private fun presentInter(activity: Activity, unitId: String, onShown: () -> Unit, onFailed: () -> Unit) {
        val ad = interAds.remove(unitId) ?: return onFailed()
        ad.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it, "interstitial", unitId, ad.responseInfo)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdPulse.show(activity, "interstitial")
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "interstitial")
                fetchInter(activity, unitId)
                onShown()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                fetchInter(activity, unitId)
                onFailed()
            }
        }
        ad.show(activity)
    }

    private fun fetchAppOpen(context: Context, unitId: String) {
        if (unitId.isBlank() || appOpenAds.containsKey(unitId) || !loading.add("appopen:$unitId")) return
        AdPulse.req(context, "app_open")
        AppOpenAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    AdPulse.loaded(context, "app_open")
                    appOpenAds[unitId] = ad
                    loading.remove("appopen:$unitId")
                    settle("appopen:$unitId", true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdPulse.loadFailed(context, "app_open", error.code, AdPulse.GOOGLE)
                    loading.remove("appopen:$unitId")
                    settle("appopen:$unitId", false)
                    log("appopen load failed: ${error.message}")
                }
            }
        )
    }

    private fun presentAppOpen(activity: Activity, unitId: String, onShown: () -> Unit, onFailed: () -> Unit) {
        val ad = appOpenAds.remove(unitId) ?: return onFailed()
        ad.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it, "app_open", unitId, ad.responseInfo)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdPulse.show(activity, "app_open")
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "app_open")
                fetchAppOpen(activity, unitId)
                onShown()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                fetchAppOpen(activity, unitId)
                onFailed()
            }
        }
        ad.show(activity)
    }

    private fun fetchRewarded(context: Context, unitId: String) {
        if (unitId.isBlank() || rewardedAds.containsKey(unitId) || !loading.add("rewarded:$unitId")) return
        AdPulse.req(context, "rewarded")
        RewardedAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    AdPulse.loaded(context, "rewarded")
                    rewardedAds[unitId] = ad
                    loading.remove("rewarded:$unitId")
                    settle("rewarded:$unitId", true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdPulse.loadFailed(context, "rewarded", error.code, AdPulse.GOOGLE)
                    loading.remove("rewarded:$unitId")
                    settle("rewarded:$unitId", false)
                    log("rewarded load failed: ${error.message}")
                }
            }
        )
    }

    private fun presentRewarded(activity: Activity, unitId: String, onShown: () -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAds.remove(unitId) ?: return onFailed()
        ad.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it, "rewarded", unitId, ad.responseInfo)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdPulse.show(activity, "rewarded")
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "rewarded")
                fetchRewarded(activity, unitId)
                onShown()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                fetchRewarded(activity, unitId)
                onFailed()
            }
        }
        ad.show(activity) {  }
    }

    private fun fetchNative(context: Context, unitId: String) {
        if (unitId.isBlank() || nativeAds.containsKey(unitId) || !loading.add("native:$unitId")) return
        AdPulse.req(context, "native")
        AdLoader.Builder(context, unitId)
            .forNativeAd { ad ->
                AdPulse.loaded(context, "native")
                ad.setOnPaidEventListener {
                    EarningsTracker.trackPaidEvent(context.applicationContext, it, "native", unitId, ad.responseInfo)
                }
                nativeAds.put(unitId, ad)?.destroy()
                loading.remove("native:$unitId")
                settle("native:$unitId", true)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdPulse.loadFailed(context, "native", error.code, AdPulse.GOOGLE)
                    loading.remove("native:$unitId")
                    settle("native:$unitId", false)
                    log("native load failed: ${error.message}")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    AdPulse.impression(context.applicationContext, "native")
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun presentNative(activity: Activity, unitId: String, onShown: () -> Unit, onFailed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return onFailed()
        val ad = nativeAds.remove(unitId) ?: return onFailed()

        val shown = runCatching {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val view = LayoutInflater.from(activity)
                .inflate(R.layout.sidemenu_ad_fullscreen_native, null) as NativeAdView
            bindNative(view, ad)
            view.findViewById<ImageView>(R.id.drawer_native_closeVw).setOnClickListener { dialog.dismiss() }
            dialog.setContentView(view, ViewGroup.LayoutParams(MATCH, MATCH))
            dialog.setOnDismissListener {
                runCatching { ad.destroy() }
                fetchNative(activity, unitId)
                onShown()
            }
            dialog.setCancelable(true)
            dialog.show()
            true
        }.getOrElse {
            Log.e(TAG, "native dialog failed", it)
            false
        }

        if (!shown) {
            runCatching { ad.destroy() }
            fetchNative(activity, unitId)
            onFailed()
        }
    }

    private fun bindNative(view: NativeAdView, ad: NativeAd) {
        val headline = view.findViewById<TextView>(R.id.drawer_native_headlineVw)
        val body = view.findViewById<TextView>(R.id.drawer_native_bodyVw)
        val cta = view.findViewById<Button>(R.id.drawer_native_ctaVw)
        val icon = view.findViewById<ImageView>(R.id.drawer_native_iconVw)
        val advertiser = view.findViewById<TextView>(R.id.drawer_native_advertiserVw)
        val media = view.findViewById<MediaView>(R.id.drawer_native_mediaVw)

        headline.text = ad.headline
        view.headlineView = headline

        body.text = ad.body.orEmpty()
        body.visibility = if (ad.body.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        view.bodyView = body

        cta.text = ad.callToAction.orEmpty()
        cta.visibility = if (ad.callToAction.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        view.callToActionView = cta

        val iconAsset = ad.icon
        if (iconAsset?.drawable != null) {
            icon.setImageDrawable(iconAsset.drawable)
            icon.visibility = android.view.View.VISIBLE
        } else {
            icon.visibility = android.view.View.GONE
        }
        view.iconView = icon

        advertiser.text = ad.advertiser.orEmpty()
        advertiser.visibility = if (ad.advertiser.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        view.advertiserView = advertiser

        view.mediaView = media
        ad.mediaContent?.let { media.mediaContent = it }

        view.setNativeAd(ad)
    }

    private fun presentCustom(activity: Activity, onShown: () -> Unit, onFailed: () -> Unit) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return onFailed()
        val overlay = android.widget.FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            isClickable = true
            elevation = 64f
        }
        val remove = { runCatching { content.removeView(overlay) } }
        val close = ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            val size = (40 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(
                size, size, android.view.Gravity.TOP or android.view.Gravity.END
            ).apply { topMargin = size; marginEnd = size / 2 }
            setOnClickListener { remove(); onShown() }
        }
        val slot = android.widget.FrameLayout(activity)
        overlay.addView(slot, ViewGroup.LayoutParams(MATCH, MATCH))
        overlay.addView(close)
        content.addView(overlay, ViewGroup.LayoutParams(MATCH, MATCH))
        OwnAdsKeeper().fetchCustomAd(
            context = activity,
            container = slot,
            type = OwnAdsKeeper.OwnAdFormat.FULLSCREEN_NATIVE,
            onFail = { remove(); onFailed() },
        )
    }

    private fun unfoldLink(activity: Activity, spec: SideMenuAdPlan, urlPointerKey: String): Boolean {
        val url = pickDirectLink(activity, spec, urlPointerKey)
        if (url.isBlank()) {
            log("directlink has no usable url — next")
            return false
        }
        val opened = DeepLinkRouter.open(activity, url, DeepLinkRouter.modeOf(spec.openType))
        if (!opened) log("directlink open failed for '$url' — next")
        return opened
    }

    private fun pickDirectLink(activity: Activity, spec: SideMenuAdPlan, urlPointerKey: String): String {
        val urls = spec.urls
        if (urls.isEmpty()) return spec.adUnitId

        val vault = AdPrefsStore.getInstance(activity)
        val at = vault.getInt(urlPointerKey, 0).let { if (it in urls.indices) it else 0 }
        vault.putInt(urlPointerKey, (at + 1) % urls.size)
        log("directlink url ${at + 1}/${urls.size}")
        return urls[at]
    }

    fun onReturnTo(activity: Activity, action: () -> Unit) {
        val app = activity.application
        if (app == null) return action()
        val screen = activity.javaClass
        val openedAt = System.currentTimeMillis()

        val callbacks = object : Application.ActivityLifecycleCallbacks {
            private var spent = false
            private var original: Activity? = activity

            private fun release(run: Boolean) {
                if (spent) return
                spent = true
                original = null
                runCatching { app.unregisterActivityLifecycleCallbacks(this) }
                if (!run) return
                if (System.currentTimeMillis() - openedAt > RETURN_STALE_MS) {
                    log("directlink return came too late — flow dropped")
                    return
                }
                runCatching(action).onFailure { Log.e(TAG, "directlink continuation threw", it) }
            }

            override fun onActivityResumed(a: Activity) {

                if (a === original || (original == null && a.javaClass == screen)) release(run = true)
            }

            override fun onActivityDestroyed(a: Activity) {
                if (a === original) original = null
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        log("directlink opened — holding the flow until the launcher returns")
    }

    private fun once(block: () -> Unit): () -> Unit {
        var called = false
        return {
            if (!called) {
                called = true
                block()
            }
        }
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
