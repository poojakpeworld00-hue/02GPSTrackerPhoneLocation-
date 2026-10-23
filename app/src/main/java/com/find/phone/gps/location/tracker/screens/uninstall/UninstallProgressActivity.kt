package com.find.phone.gps.location.tracker.screens.uninstall

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.PageUninstallProgressBinding

/**
 * Advance route only: a ring fills around a shaking bin while three steps tick off, then the
 * thank-you screen follows. Wording is "closing", never "uninstalling": nothing is removed here.
 * Back is swallowed; the ring always finishes.
 */
class UninstallProgressActivity : UninstallPage<PageUninstallProgressBinding>() {

    override val layoutId: Int = R.layout.page_uninstall_progress
    override val page = UninstallConfig.PAGE_PROGRESS

    private val running = mutableListOf<Animator>()
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false

    private val steps by lazy {
        listOf(binding.imgStep1 to binding.lblStep1, binding.imgStep2 to binding.lblStep2, binding.imgStep3 to binding.lblStep3)
    }

    override fun initPage() {
        binding.lblProgressTitle.text = getString(R.string.un_progress_title, getString(R.string.app_name))
        render(0)
        start(ObjectAnimator.ofFloat(binding.imgBin, View.ROTATION, -10f, 10f).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        })
        start(ValueAnimator.ofInt(0, 100).apply {
            duration = PROGRESS_MS
            interpolator = LinearInterpolator()
            addUpdateListener { render(it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onReady()
            })
        })
    }

    override fun onFunnelBack() = Unit

    private fun render(pct: Int) {
        binding.ringVw.progress = pct
        binding.lblPercent.text = getString(R.string.un_percent, pct)
        steps.forEachIndexed { i, (icon, label) -> paintStep(icon, label, done = pct >= STEP_AT[i]) }
    }

    private fun paintStep(icon: ImageView, label: TextView, done: Boolean) {
        if (icon.tag == done) return
        icon.tag = done
        icon.setImageResource(if (done) R.drawable.ic_un_g_check_circle else R.drawable.ic_un_g_radio_off)
        icon.imageTintList = ContextCompat.getColorStateList(this, if (done) R.color.ds_success else R.color.ds_track)
        label.setTextColor(ContextCompat.getColor(this, if (done) R.color.ds_ink else R.color.ds_muted))
    }

    private fun onReady() {
        if (ready || isFinishing || isDestroyed) return
        ready = true
        running.firstOrNull()?.cancel()
        binding.imgBin.rotation = 0f
        start(ValueAnimator.ofObject(
            ArgbEvaluator(), ContextCompat.getColor(this, R.color.ds_primary), ContextCompat.getColor(this, R.color.ds_success),
        ).apply {
            duration = 300
            addUpdateListener { binding.ringVw.arcColor = it.animatedValue as Int }
        })
        binding.imgBin.setImageResource(R.drawable.ic_un_tick)
        binding.imgBin.imageTintList = ContextCompat.getColorStateList(this, R.color.ds_success)
        binding.imgBin.scaleX = 0.4f
        binding.imgBin.scaleY = 0.4f
        binding.imgBin.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(2f)).start()
        binding.lblProgressTitle.setText(R.string.un_progress_done)
        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            UninstallAds.showInterThen(this, page) {
                startActivity(
                    UninstallThanksActivity.newIntent(this, advanced = true, reason = intent.getStringExtra(UninstallFlow.EXTRA_REASON))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }, HOLD_MS)
    }

    private fun start(animator: Animator) {
        running += animator
        animator.start()
    }

    override fun onDestroy() {
        // listeners first, so a cancelled animator cannot navigate from a dead screen
        running.forEach { it.removeAllListeners(); it.cancel() }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val PROGRESS_MS = 3500L
        private const val HOLD_MS = 1200L
        private val STEP_AT = intArrayOf(20, 60, 100)

        fun newIntent(ctx: Context, reason: String?): Intent =
            Intent(ctx, UninstallProgressActivity::class.java)
                .putExtra(UninstallFlow.EXTRA_ADVANCED, true)
                .putExtra(UninstallFlow.EXTRA_REASON, reason)
    }
}
