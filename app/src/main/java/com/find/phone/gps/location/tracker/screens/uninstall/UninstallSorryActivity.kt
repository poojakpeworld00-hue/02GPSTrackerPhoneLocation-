package com.find.phone.gps.location.tracker.screens.uninstall

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.PageUninstallSorryBinding
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens

/** "Sorry for the trouble": three quick fixes back into the app, "Don't uninstall yet", and the way on. */
class UninstallSorryActivity : UninstallPage<PageUninstallSorryBinding>() {

    override val layoutId: Int = R.layout.page_uninstall_sorry
    override val page = UninstallConfig.PAGE_SORRY

    private val loops = mutableListOf<ValueAnimator>()

    override fun initPage() {
        loops += ObjectAnimator.ofFloat(binding.headerTileVw, View.SCALE_X, 1f, 1.08f).loop()
        loops += ObjectAnimator.ofFloat(binding.headerTileVw, View.SCALE_Y, 1f, 1.08f).loop()
        loops += ObjectAnimator.ofFloat(binding.headerIconVw, View.ROTATION, -6f, 6f).loop()
        if (!VisualTweens.animationsDisabled(this)) loops.forEach { it.start() }
        listOf(binding.fixCard1Vw, binding.fixCard2Vw, binding.fixCard3Vw)
            .forEachIndexed { i, card -> enterUp(card, 80L + i * 70L) }

        binding.padFix1.setOnClickListener { keep("search", MainHubActivity.TARGET_SEARCH) }
        binding.padFix2.setOnClickListener { keep("my_number", MainHubActivity.TARGET_MY_NUMBER) }
        binding.padFix3.setOnClickListener { keep("reminders", MainHubActivity.TARGET_REMINDERS) }
        binding.padDontUninstall.setOnClickListener { keep("keep", null) }
        binding.padStillUninstall.setOnClickListener { onFunnelBack() }
        VisualTweens.pressScale(binding.padDontUninstall)
    }

    private fun ObjectAnimator.loop() = apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }

    private fun keep(event: String, target: String?) {
        track("un_sorry_$event")
        UninstallFlow.keepApp(this, target)
    }

    override fun onFunnelBack() {
        track("un_sorry_still")
        UninstallAds.showInterThen(this, page) {
            startActivity(UninstallReasonActivity.newIntent(this, advanced))
        }
    }

    override fun onDestroy() {
        loops.forEach { it.cancel() }
        super.onDestroy()
    }

    companion object {
        fun newIntent(ctx: Context, advanced: Boolean): Intent =
            Intent(ctx, UninstallSorryActivity::class.java).putExtra(UninstallFlow.EXTRA_ADVANCED, advanced)
    }
}
