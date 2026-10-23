package com.find.phone.gps.location.tracker.screens.dashboard

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.repo.SearchConfig
import com.find.phone.gps.location.tracker.repo.LookupAllowance
import com.find.phone.gps.location.tracker.databinding.BsheetSearchLimitBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class LookupCapSheet(
    private val activity: Activity,
    private val onReady: () -> Unit,
) {
    private val themed = ContextThemeWrapper(activity, R.style.Theme_GpsTracker)
    private val binding = BsheetSearchLimitBinding.inflate(LayoutInflater.from(themed))
    private val dialog = BottomSheetDialog(themed, R.style.Ds_BottomSheetDialog)
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false

    private val ticker = object : Runnable {
        override fun run() {
            val left = LookupAllowance.cooldownRemainingMs(activity)
            if (left <= 0L) {
                finishReady()
                return
            }
            binding.lblCountdown.text = LookupAllowance.formatRemaining(left)
            handler.postDelayed(this, 1_000L)
        }
    }

    fun show() {
        val limit = LookupAllowance.limit(activity)
        val used = LookupAllowance.used(activity).coerceAtMost(limit)
        binding.lblUsed.text = "$used/$limit"
        binding.ringVw.fraction = if (limit > 0) used.toFloat() / limit else 1f

        val hasTimer = SearchConfig.read(activity).cooldownMinutes > 0 && LookupAllowance.onCooldown(activity)
        binding.lblBody.text = themed.getString(
            if (hasTimer) R.string.ds_limit_body_timer else R.string.ds_limit_body, limit,
        )
        binding.countdownBox.isVisible = hasTimer
        binding.lblReset.isVisible = !hasTimer
        if (hasTimer) {
            binding.lblCountdown.text = LookupAllowance.formatRemaining(LookupAllowance.cooldownRemainingMs(activity))
            handler.post(ticker)
        }

        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.setOnDismissListener { handler.removeCallbacks(ticker) }
        dialog.show()
    }

    private fun finishReady() {
        if (fired) return
        fired = true
        runCatching { dialog.dismiss() }
        if (!activity.isFinishing) onReady()
    }
}
