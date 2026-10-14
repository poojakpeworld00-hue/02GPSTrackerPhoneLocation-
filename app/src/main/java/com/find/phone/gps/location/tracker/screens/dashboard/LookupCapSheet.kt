package com.find.phone.gps.location.tracker.screens.dashboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.render.BonusAdSponsor
import com.find.phone.gps.location.tracker.repo.billing.ProSettings
import com.find.phone.gps.location.tracker.repo.billing.LookupAllowance
import com.find.phone.gps.location.tracker.databinding.BsheetSearchLimitBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Search limit reached (design 9b), every time a free user is out of searches: Go Premium goes to
 * [onPremium]. Only when Remote Config's `rewarded_extra_searches` is above 0 does it also offer a
 * rewarded ad, which adds that many searches and [onExtraSearch] carries on with the search that
 * was asked for. Closing the ad early adds nothing.
 */
class LookupCapSheet(
    private val activity: Activity,
    private val onExtraSearch: () -> Unit,
    private val onPremium: () -> Unit,
) {
    private val themed = ContextThemeWrapper(activity, R.style.Theme_GpsTracker)
    private val binding = BsheetSearchLimitBinding.inflate(LayoutInflater.from(themed))
    private val dialog = BottomSheetDialog(themed, R.style.Ds_BottomSheetDialog)
    private var shine: ValueAnimator? = null
    private var watching = false

    fun show() {
        val limit = LookupAllowance.limit(activity)
        val used = LookupAllowance.used(activity).coerceAtMost(limit)
        binding.lblUsed.text = "$used/$limit"
        binding.ringVw.fraction = if (limit > 0) used.toFloat() / limit else 1f
        // `rewarded_extra_searches` 0 (the default): Premium is the only way past the limit.
        val offersAd = ProSettings.read(activity).rewardedExtraSearches > 0
        binding.lblBody.text = themed.getString(
            if (offersAd) R.string.ds_limit_body else R.string.ds_limit_body_premium, limit,
        )
        binding.padWatch.isVisible = offersAd

        VisualTweens.pressScale(binding.padWatch)
        VisualTweens.pressScale(binding.padPremium)
        binding.padWatch.setOnClickListener { watch() }
        binding.padPremium.clipToOutline = true
        binding.padPremium.setOnClickListener {
            dialog.dismiss()
            onPremium()
        }
        if (offersAd) BonusAdSponsor.preload(activity)

        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.setOnDismissListener { shine?.cancel() }
        dialog.show()
        kickShine()
    }

    private fun watch() {
        if (watching) return
        watching = true
        binding.lblWatch.isInvisible = true
        binding.pbWatchVw.isVisible = true
        BonusAdSponsor().show(activity) { result ->
            watching = false
            binding.lblWatch.isInvisible = false
            binding.pbWatchVw.isVisible = false
            when (result) {
                BonusAdSponsor.Result.EARNED -> {
                    LookupAllowance.grantRewarded(activity)
                    dialog.dismiss()
                    onExtraSearch()
                }
                BonusAdSponsor.Result.NOT_EARNED ->
                    Toast.makeText(activity, R.string.ds_reward_not_earned, Toast.LENGTH_SHORT).show()
                BonusAdSponsor.Result.CANCELED -> Unit
            }
        }
    }

    /** The light streak sweeping across the gold button (design `shine`). */
    private fun kickShine() {
        if (VisualTweens.animationsDisabled(themed)) {
            binding.shineVw.isVisible = false
            return
        }
        binding.padPremium.post {
            val w = binding.padPremium.width.toFloat()
            shine = ObjectAnimator.ofFloat(binding.shineVw, View.TRANSLATION_X, -0.3f * w, 1.1f * w).apply {
                duration = 1_800L
                startDelay = 600L
                repeatCount = ValueAnimator.INFINITE
                interpolator = VisualTweens.EASE_IN_OUT
                start()
            }
        }
    }
}
