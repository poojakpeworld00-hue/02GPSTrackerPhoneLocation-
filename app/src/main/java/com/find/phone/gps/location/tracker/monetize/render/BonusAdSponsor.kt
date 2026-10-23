package com.find.phone.gps.location.tracker.monetize.render

import com.find.phone.gps.location.tracker.monetize.engine.EarningsTracker
import com.find.phone.gps.location.tracker.insights.AdPulse
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.BonusAdSettings
import com.find.phone.gps.location.tracker.monetize.engine.BonusAdCooldown
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.find.phone.gps.location.tracker.databinding.PopupRewardRetryBinding
import com.find.phone.gps.location.tracker.databinding.PopupRewardWaitBinding

class BonusAdSponsor {

    enum class Result { EARNED, NOT_EARNED, CANCELED }

    companion object {
        private const val TAG = "BonusAdSponsor"

        private const val WAIT_FOR_LOAD_MS = 6_000L

        private const val RING_TICK_MS = 100L

        private const val DIM_AMOUNT = 0.6f

        private val NO_FILL_CODES = setOf(AdRequest.ERROR_CODE_NO_FILL, AdRequest.ERROR_CODE_MEDIATION_NO_FILL)

        private var loadedAd: RewardedAd? = null
        private var isLoading = false
        private val waiting = mutableListOf<() -> Unit>()

        fun isReady(): Boolean = loadedAd != null

        fun preload(context: Context) {
            val pref = AdPrefsStore.getInstance(context)
            if (!pref.getBoolean("IsAdsON")) return
            if (loadedAd != null || isLoading) return

            if (BonusAdCooldown.isActive(context)) return

            val unitId = pref.getString("googleRewarded")
            if (unitId.isNullOrEmpty()) return

            val app = context.applicationContext
            isLoading = true
            Log.d(TAG, "Preloading…")

            AdPulse.req(context, "rewarded")
            RewardedAd.load(
                app,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        AdPulse.loaded(context, "rewarded")
                        loadedAd = ad
                        isLoading = false
                        BonusAdCooldown.reset(app)
                        Log.d(TAG, "Preloaded OK")
                        flushWaiting()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AdPulse.loadFailed(context, "rewarded", error.code, AdPulse.GOOGLE)
                        loadedAd = null
                        isLoading = false
                        Log.e(TAG, "Preload failed (${error.code}): ${error.message}")
                        if (error.code in NO_FILL_CODES) {
                            app.trackEvent("reward_nofill")
                            BonusAdCooldown.start(app)
                        } else {
                            app.trackEvent("reward_load_error_${error.code}")
                        }
                        flushWaiting()
                    }
                }
            )
        }

        private fun flushWaiting() {
            val pending = waiting.toList()
            waiting.clear()
            pending.forEach { it() }
        }

        private fun presentDirectLinkFallback(activity: Activity, onClosed: () -> Unit) {
            val pref = AdPrefsStore.getInstance(activity)
            if (pref.getBoolean("IsCustomADS")) {
                Log.d(TAG, "Falling back to DirectLink")
                FullscreenOnAction.unfoldDirectLink(activity) { onClosed() }
            } else {
                onClosed()
            }
        }
    }

    fun show(activity: Activity, onDone: (Result) -> Unit) = show(activity, onDone, attempt = 0)

    private fun show(activity: Activity, onDone: (Result) -> Unit, attempt: Int) {
        val pref = AdPrefsStore.getInstance(activity)

        if (!pref.getBoolean("IsAdsON")) {
            onDone(Result.EARNED)
            return
        }

        if (!isNetworkConnected(activity)) {
            Log.d(TAG, "No internet — content shown without an ad")
            onDone(Result.EARNED)
            return
        }
        if (BonusAdCooldown.isActive(activity)) return onCooldown(activity, onDone)

        if (loadedAd == null) preload(activity)

        if (loadedAd == null && isLoading) {
            val handler = Handler(Looper.getMainLooper())
            var settled = false
            val resume = {
                if (!settled) {
                    settled = true
                    handler.removeCallbacksAndMessages(null)
                    if (!activity.isFinishing && !activity.isDestroyed) presentLoaded(activity, onDone, attempt)
                }
            }
            waiting += resume
            handler.postDelayed({ waiting.remove(resume); resume() }, WAIT_FOR_LOAD_MS)
            return
        }
        presentLoaded(activity, onDone, attempt)
    }

    private fun presentLoaded(activity: Activity, onDone: (Result) -> Unit, attempt: Int) {
        val ad = loadedAd
        if (ad == null) {
            val maxRetries = BonusAdSettings.read(activity).maxRetries
            when {
                !isNetworkConnected(activity) -> {
                    Log.d(TAG, "No ad, no internet — content shown without an ad")
                    onDone(Result.EARNED)
                }
                AdPrefsStore.getInstance(activity).getString("googleRewarded").isNullOrEmpty() -> {
                    Log.d(TAG, "No rewarded unit — showing DirectLink fallback")
                    presentDirectLinkFallback(activity) { onDone(Result.EARNED) }
                }

                BonusAdCooldown.isActive(activity) -> onCooldown(activity, onDone)
                maxRetries >= 0 && attempt >= maxRetries -> {
                    Log.d(TAG, "Still no ad after $attempt Retries — content shown without an ad")
                    activity.trackEvent("reward_retry_exhausted")
                    onDone(Result.EARNED)
                }
                else -> {
                    Log.d(TAG, "Online but the ad didn't load — asking to try again")
                    preload(activity)
                    promptRetry(activity, onDone, attempt)
                }
            }
            return
        }

        loadedAd = null
        var earned = false

        ad.setOnPaidEventListener {
            EarningsTracker.trackPaidEvent(activity, it, "rewarded", ad.adUnitId, ad.responseInfo)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdPulse.show(activity, "rewarded")
            }

            override fun onAdDismissedFullScreenContent() {
                AdPulse.close(activity, "rewarded")
                Log.d(TAG, "Dismissed, earned=$earned")
                preload(activity)
                onDone(if (earned) Result.EARNED else Result.NOT_EARNED)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Show failed: ${error.message} — showing DirectLink fallback")
                preload(activity)
                presentDirectLinkFallback(activity) { onDone(Result.EARNED) }
            }
        }

        ad.show(activity) {
            earned = true
            Log.d(TAG, "Reward earned: ${it.type} x${it.amount}")
        }
    }

    private fun dimBehind(dialog: Dialog) {
        dialog.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(DIM_AMOUNT)
        }
    }

    private fun onCooldown(activity: Activity, onDone: (Result) -> Unit) {
        if (BonusAdSettings.read(activity).mode == BonusAdSettings.Mode.FREE) {
            Log.d(TAG, "No-fill cooldown (${BonusAdCooldown.remainingMs(activity) / 1000}s left) — content shown without an ad")
            activity.trackEvent("reward_cooldown_free")
            onDone(Result.EARNED)
        } else {
            activity.trackEvent("reward_cooldown_wait")
            promptWait(activity, onDone)
        }
    }

    private fun dialogFor(activity: Activity): Pair<Dialog, PopupRewardRetryBinding> {
        val themed = ContextThemeWrapper(activity, R.style.Theme_GpsTracker)
        val view = PopupRewardRetryBinding.inflate(LayoutInflater.from(themed))
        val dialog = Dialog(themed)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dimBehind(dialog)
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCanceledOnTouchOutside(false)
        return dialog to view
    }

    private fun promptRetry(activity: Activity, onDone: (Result) -> Unit, attempt: Int) {
        if (activity.isFinishing || activity.isDestroyed) return onDone(Result.CANCELED)
        val (dialog, view) = dialogFor(activity)
        var retrying = false
        dialog.setOnDismissListener { if (!retrying) onDone(Result.CANCELED) }
        view.padRetryCancel.setOnClickListener { dialog.dismiss() }
        view.padRetry.setOnClickListener {
            retrying = true
            dialog.dismiss()
            show(activity, onDone, attempt + 1)
        }
        dialog.show()
    }

    private fun promptWait(activity: Activity, onDone: (Result) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return onDone(Result.CANCELED)
        val themed = ContextThemeWrapper(activity, R.style.Theme_GpsTracker)
        val view = PopupRewardWaitBinding.inflate(LayoutInflater.from(themed))
        val dialog = Dialog(themed)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dimBehind(dialog)
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCanceledOnTouchOutside(false)

        val handler = Handler(Looper.getMainLooper())
        val total = BonusAdCooldown.totalMs(activity).toFloat()
        var unlock = false

        fun setUnlockEnabled(on: Boolean) {
            view.padWaitUnlock.isEnabled = on
            view.padWaitUnlock.alpha = if (on) 1f else 0.45f
        }
        setUnlockEnabled(false)

        val tick = object : Runnable {
            override fun run() {
                val left = BonusAdCooldown.remainingMs(activity)
                if (left > 0) {
                    view.ringWaitVw.progress = (left / total * view.ringWaitVw.max).toInt()
                    view.lblWaitTime.text = DateUtils.formatElapsedTime((left + 999) / 1000)
                    handler.postDelayed(this, RING_TICK_MS)
                } else {
                    view.ringWaitVw.progress = 0
                    view.lblWaitTime.isVisible = false
                    view.picWaitDone.isVisible = true
                    view.lblWaitTitle.setText(R.string.ds_reward_wait_ready_title)
                    view.lblWaitBody.setText(R.string.ds_reward_wait_ready)
                    setUnlockEnabled(true)
                }
            }
        }
        tick.run()

        dialog.setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
            if (unlock) show(activity, onDone) else onDone(Result.CANCELED)
        }
        view.padWaitLater.setOnClickListener { dialog.dismiss() }
        view.padWaitUnlock.setOnClickListener {
            unlock = true
            dialog.dismiss()
        }
        dialog.show()
    }
}
