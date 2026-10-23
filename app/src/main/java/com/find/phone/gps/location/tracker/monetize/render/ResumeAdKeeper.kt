package com.find.phone.gps.location.tracker.monetize.render

import com.find.phone.gps.location.tracker.insights.AdPulse
import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import io.lighthouse.push.extended.LightHouseRichPush
import com.find.phone.gps.location.tracker.monetize.model.AdFormatKind
import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnExit
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.find.phone.gps.location.tracker.BuildConfig

object ResumeAdKeeper {
    private const val LOG_TAG = "ResumeAdKeeper"
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd: Boolean = false
    var callbackshow:Boolean = false
    var isOpenAppDismiss: Boolean = false

    var skipNextAppOpenAd: Boolean = false

    var pendingGameCloseAd: Boolean = false

    private var expectReturnAd: Boolean = false

    fun expectReturnAd() {
        expectReturnAd = true

        skipNextAppOpenAd = false
    }

    fun consumeExpectReturnAd(): Boolean {
        val v = expectReturnAd
        expectReturnAd = false
        return v
    }

    val isAdAvailable: Boolean
        get() = appOpenAd != null

    fun loadAd(context: Context?) {
        if (isLoadingAd || isAdAvailable) {
            return
        }
        if (context == null) {
            return
        }
        val adsPreference = AdPrefsStore.getInstance(context)

        if (!adsPreference.getBoolean("AppopenAds")) {
            Log.e(LOG_TAG, "AppopenAds disabled by Firebase flag")
            return
        }
        if (AdFormatKind.fromString(adsPreference.getString("IsAdType")) == AdFormatKind.GOOGLE) {
            isLoadingAd = true
            val request = AdRequest.Builder().build()

            if (adsPreference.getString("IsAdType").equals("Google", true)) {
                AdPrefsStore.getInstance(context).getString("googleAppopen")?.let { adUnitId ->
                    AdPulse.req(context, "app_open")
                    AppOpenAd.load(
                        context, adUnitId, request, object : AppOpenAdLoadCallback() {
                            override fun onAdLoaded(ad: AppOpenAd) {
                                AdPulse.loaded(context, "app_open")

                                try {
                                    context.trackEvent("appopen_ad_loaded")
                                } catch (e: Exception) {
                                }

                                Log.d(LOG_TAG, "Ad was loaded.")
                                appOpenAd = ad
                                isLoadingAd = false
                            }

                            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                AdPulse.loadFailed(context, "app_open", loadAdError.code, AdPulse.GOOGLE)
                                Log.e(
                                    LOG_TAG, "Ad failed to load: ${loadAdError.message}"
                                )

                                try {
                                    context.trackEvent("appopen_ad_fail")
                                } catch (e: Exception) {
                                }
                                isLoadingAd = false
                            }
                        })
                }
            }

        } else {
            return
        }
    }

    fun presentAdIfAvailable(
        activity: Activity, onShowAdCompleteListener: OnAdDoneCallback
    ) {
        if (!AdPrefsStore.getInstance(activity).getBoolean("IsAdsON")) {
            return
        }

        if (!AdPrefsStore.getInstance(activity).getBoolean("AppopenAds")) {
            Log.e(LOG_TAG, "AppopenAds disabled by Firebase flag")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }
        if (callbackshow) {
            Log.e(
                LOG_TAG, "callbackshow is Show"
            )
            return
        }

        if (LightHouseRichPush.shouldDeferOverlay(activity)) {
            Log.e(LOG_TAG, "Deferring App Open — rich-push overlay active")
            return
        }
        if (FullscreenOnAction.Companion.isInterShow) {
            Log.e(
                LOG_TAG, "Inter is Show"
            )
            return
        }
        if (FullscreenOnExit.Companion.isInterBAckShow) {
            Log.e(
                LOG_TAG, "Inter Back is Show"
            )
            return
        }
        if (isShowingAd) {
            Log.e(
                LOG_TAG, "The app open ad is already showing."
            )
            return
        }
        if (!isAdAvailable) {
            Log.e(
                LOG_TAG, "The app open ad is not ready yet."
            )
            onShowAdCompleteListener.onShowAdComplete()
            loadAd(activity)
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "app_open")
                Log.e(
                    LOG_TAG, "Ad dismissed fullscreen content."
                )

                try {
                    activity.trackEvent("appopen_ad_dismissed")
                } catch (_: Exception) {
                }

                appOpenAd = null
                isShowingAd = false
                isOpenAppDismiss = true
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(
                    activity
                )
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(
                    LOG_TAG, adError.message
                )
                try {
                    activity.trackEvent("appopen_ad_show_fail")
                } catch (_: Exception) {
                }
                appOpenAd = null
                isShowingAd = false
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(
                    activity
                )
            }

            override fun onAdShowedFullScreenContent() {
                AdPulse.show(activity, "app_open")
                Log.e(
                    LOG_TAG, "Ad showed fullscreen content."
                )
            }
        }

        activity.trackEvent("appopen_ad_shown")

        appOpenAd!!.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it, "app_open", appOpenAd?.adUnitId, appOpenAd?.responseInfo)
        }

        isShowingAd = true

        if (AdPrefsStore.getInstance(activity).getString("IsAdType").equals("Google", true)) {
            appOpenAd?.show(activity)
        }
    }

    interface OnAdDoneCallback {
        fun onShowAdComplete()
    }

}
