package com.find.phone.gps.location.tracker.setupflow

import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import android.animation.Animator
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.databinding.CardGestureTileBinding
import com.find.phone.gps.location.tracker.databinding.PageOnboardingWelcomeBinding
import com.find.phone.gps.location.tracker.grants.GrantOrchestrator
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.helpers.followAdContainer
import com.find.phone.gps.location.tracker.helpers.concealWhileAdVisible
import org.fossify.commons.extensions.viewBinding

class WelcomeStageActivity : ShellHubActivity() {

    private val binding by viewBinding(PageOnboardingWelcomeBinding::inflate)
    private val animators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        excludeAppFromRecents()

        VisualTweens.pressScale(binding.onboardingContinueVw)
        binding.onboardingContinueVw.setOnClickListener { trackEvent("welcome_continue_click"); seekOnboardingPermissions() }
        binding.onboardingSkipVw.setOnClickListener { trackEvent("welcome_skip_click"); goToNextStep() }

        val ui = HomeAdsSettings.onboardingUi(this, HomeAdsSettings.OnboardScreen.WELCOME)
        binding.onboardingSkipVw.isVisible = ui.skipEnabled

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (HomeRoleJourney.backMovesForward(this@WelcomeStageActivity)) goToNextStep()
                else HomeRoleJourney.passBackThrough(this@WelcomeStageActivity, this)
            }
        })

        HomeAdsSettings.presentSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.WELCOME),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        binding.onboardingHeroVw.concealWhileAdVisible(binding.adNativeFrameVw, binding.heroCompactVw)

        HomeRoleJourney.bindStepHeader(this, HomeAdsSettings.OnboardScreen.WELCOME, binding.root)

        wireGestureTiles()
        playMotion()
    }

    private fun wireGestureTiles() = with(binding) {
        bindTile(tileSwipeUpVw, -90f, R.string.gesture_swipe_up, R.string.gesture_swipe_up_body, active = false)
        bindTile(tileSwipeRightVw, 0f, R.string.gesture_swipe_right, R.string.gesture_swipe_right_body, active = true)
        bindTile(tileSwipeDownVw, 90f, R.string.gesture_swipe_down, R.string.gesture_swipe_down_body, active = false)
        bindTile(tileSwipeLeftVw, 180f, R.string.gesture_swipe_left, R.string.gesture_swipe_left_body, active = false)
    }

    private fun bindTile(tile: CardGestureTileBinding, rotation: Float, title: Int, body: Int, active: Boolean) {
        tile.tileIconVw.rotation = rotation
        tile.tileTitleVw.setText(title)
        tile.tileBodyVw.setText(body)
        if (active) {
            tile.tileRootVw.setBackgroundResource(R.drawable.vs_gesture_active_r16)
            tile.tileRootVw.elevation = resources.displayMetrics.density * 6f
            tile.tileIconBoxVw.setBackgroundResource(R.drawable.vs_tile_white20_r12)
            ImageViewCompat.setImageTintList(tile.tileIconVw, ContextCompat.getColorStateList(this, R.color.white))
            tile.tileTitleVw.setTextColor(ContextCompat.getColor(this, R.color.white))
            tile.tileBodyVw.setTextColor(ContextCompat.getColor(this, R.color.ds_white90))
        }
    }

    private fun playMotion() {
        if (VisualTweens.animationsDisabled(this)) return
        animators += VisualTweens.fadeIn(binding.onboardingBadgeVw, durationMs = 600L, delayMs = 400L)
        animators += VisualTweens.floaty(binding.flagDial)
        animators += VisualTweens.floaty(binding.flagGps, delayMs = 1200L)
    }

    override fun onDestroy() {
        VisualTweens.cancelAll(animators)
        super.onDestroy()
    }

    private fun seekOnboardingPermissions() {

        GrantOrchestrator.check(this) { goToNextStep() }
    }

    private fun goToNextStep() {
        HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.WELCOME) {
            HomeRoleJourney.advance(this)
        }
    }
}
