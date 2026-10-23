package com.find.phone.gps.location.tracker.screens.uninstall

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.Lifecycle
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.PageUninstallThanksBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens

/**
 * Thank-you, echoing the picked reason; last in-app screen on both routes. After `thanks_delay`,
 * or on Continue / Back: simple opens the system App-info page, advance closes the app.
 */
class UninstallThanksActivity : UninstallPage<PageUninstallThanksBinding>() {

    override val layoutId: Int = R.layout.page_uninstall_thanks
    override val page = UninstallConfig.PAGE_THANKS

    private val handler = Handler(Looper.getMainLooper())
    private val loops = mutableListOf<ValueAnimator>()
    private var left = false
    private var pendingAuto = false

    override fun initPage() {
        val reason = intent.getStringExtra(UninstallFlow.EXTRA_REASON)
        if (reason.isNullOrBlank()) binding.lblSaid.visibility = View.GONE
        else binding.lblSaid.text = getString(R.string.un_you_said, reason)
        if (advanced) binding.lblSub.setText(R.string.un_thanks_sub_close)

        binding.heartTileVw.apply {
            scaleX = 0.4f; scaleY = 0.4f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(600).setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        loops += ObjectAnimator.ofPropertyValuesHolder(
            binding.heartRingVw,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.35f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.35f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0f),
        ).apply { duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = DecelerateInterpolator() }
        loops += ObjectAnimator.ofPropertyValuesHolder(
            binding.imgHeart,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f),
        ).apply { duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE }
        if (!VisualTweens.animationsDisabled(this)) loops.forEach { it.start() }
        listOf(binding.lblTitle, binding.lblSub, binding.lblSaid).forEachIndexed { i, v -> enterUp(v, 200L + i * 100L) }

        binding.padContinue.setOnClickListener { leave("un_thanks_continue") }
        handler.postDelayed({
            // an ad click may have taken the user elsewhere: wait until they are back
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) leave("un_thanks_auto") else pendingAuto = true
        }, UninstallFlow.config(this)?.thanksDelayMs ?: DEFAULT_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        if (pendingAuto) leave("un_thanks_auto")
    }

    override fun onFunnelBack() = leave("un_thanks_continue")

    private fun leave(event: String) {
        if (left || isFinishing || isDestroyed) return
        left = true
        handler.removeCallbacksAndMessages(null)
        track(event)
        UninstallAds.showInterThen(this, page) {
            if (advanced) {
                track("un_app_closed")
                UninstallFlow.closeApp(this)
            } else {
                track("un_app_info_opened")
                UninstallFlow.openAppInfo(this)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        loops.forEach { it.cancel() }
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_DELAY_MS = 3_000L

        fun newIntent(ctx: Context, advanced: Boolean, reason: String? = null): Intent =
            Intent(ctx, UninstallThanksActivity::class.java)
                .putExtra(UninstallFlow.EXTRA_ADVANCED, advanced)
                .putExtra(UninstallFlow.EXTRA_REASON, reason)
    }
}
