package com.find.phone.gps.location.tracker.setupflow

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
import com.find.phone.gps.location.tracker.helpers.hideWhileAdVisible
import org.fossify.commons.extensions.viewBinding

/**
 * Welcome step (design 4a): the gesture guide. Continue hands off to [GrantOrchestrator], which
 * asks whatever `permission_engine` has configured for this Activity, then the flow moves on
 * to whatever `onboarding.order` puts next. Skip goes to the same place without asking.
 */
class WelcomeStageActivity : ShellHubActivity() {

    private val binding by viewBinding(PageOnboardingWelcomeBinding::inflate)
    private val animators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        excludeAppFromRecents()

        VisualTweens.pressScale(binding.onboardingContinue)
        binding.onboardingContinue.setOnClickListener { requestOnboardingPermissions() }
        binding.onboardingSkip.setOnClickListener { goToNextStep() }

        // `onboarding.welcome.skip_enabled: false` makes the screen a required step.
        val ui = HomeAdsSettings.onboardingUi(this, HomeAdsSettings.OnboardScreen.WELCOME)
        binding.onboardingSkip.isVisible = ui.skipEnabled

        // Paid users: Back behaves like Skip and moves the flow on. Organic users: Back is Back.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (HomeRoleJourney.backMovesForward(this@WelcomeStageActivity)) goToNextStep()
                else HomeRoleJourney.passBackThrough(this@WelcomeStageActivity, this)
            }
        })

        // Ad frame pinned at the bottom, `launcher_ads.onboarding.welcome.slot`. showSlot hides
        // the frame outright when the slot is off, and followAdContainer drops the hairline.
        HomeAdsSettings.showSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.WELCOME),
            container = binding.adNativeFrame,
            shimmer = binding.adShimmer,
        )
        binding.adNativeDivider.followAdContainer(binding.adNativeFrame)
        // Small phones: the hero card gives way to the ad while one is on screen.
        binding.onboardingHero.hideWhileAdVisible(binding.adNativeFrame, binding.heroCompact)

        HomeRoleJourney.bindStepHeader(this, HomeAdsSettings.OnboardScreen.WELCOME, binding.root)

        bindGestureTiles()
        playMotion()
    }

    /** The four launcher gestures: one arrow glyph rotated per direction; "Swipe right" is active. */
    private fun bindGestureTiles() = with(binding) {
        bindTile(tileSwipeUp, -90f, R.string.gesture_swipe_up, R.string.gesture_swipe_up_body, active = false)
        bindTile(tileSwipeRight, 0f, R.string.gesture_swipe_right, R.string.gesture_swipe_right_body, active = true)
        bindTile(tileSwipeDown, 90f, R.string.gesture_swipe_down, R.string.gesture_swipe_down_body, active = false)
        bindTile(tileSwipeLeft, 180f, R.string.gesture_swipe_left, R.string.gesture_swipe_left_body, active = false)
    }

    private fun bindTile(tile: CardGestureTileBinding, rotation: Float, title: Int, body: Int, active: Boolean) {
        tile.tileIcon.rotation = rotation
        tile.tileTitle.setText(title)
        tile.tileBody.setText(body)
        if (active) {
            tile.tileRoot.setBackgroundResource(R.drawable.vs_gesture_active_r16)
            tile.tileRoot.elevation = resources.displayMetrics.density * 6f
            tile.tileIconBox.setBackgroundResource(R.drawable.vs_tile_white20_r12)
            ImageViewCompat.setImageTintList(tile.tileIcon, ContextCompat.getColorStateList(this, R.color.white))
            tile.tileTitle.setTextColor(ContextCompat.getColor(this, R.color.white))
            tile.tileBody.setTextColor(ContextCompat.getColor(this, R.color.ds_white90))
        }
    }

    /** The design's loops: badge `fadein .6s .4s`, chips `floaty 4s` (the second offset 1.2s). */
    private fun playMotion() {
        if (VisualTweens.animationsDisabled(this)) return
        animators += VisualTweens.fadeIn(binding.onboardingBadge, durationMs = 600L, delayMs = 400L)
        animators += VisualTweens.floaty(binding.chipDial)
        animators += VisualTweens.floaty(binding.chipGps, delayMs = 1200L)
    }

    override fun onDestroy() {
        VisualTweens.cancelAll(animators)
        super.onDestroy()
    }

    private fun requestOnboardingPermissions() {
        // onComplete fires once the whole configured queue is done — or straight away when
        // there is nothing to ask.
        GrantOrchestrator.check(this) { goToNextStep() }
    }

    /** Whatever `launcher_ads.onboarding.order` puts after this screen, behind its exit interstitial. */
    private fun goToNextStep() {
        HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.WELCOME) {
            HomeRoleJourney.advance(this)
        }
    }
}
