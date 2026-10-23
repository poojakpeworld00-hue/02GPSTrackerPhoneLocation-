package com.find.phone.gps.location.tracker.screens.walkthrough

import android.animation.ValueAnimator
import android.content.Intent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.databinding.PageOnboardingBinding
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.setupflow.AlarmIntroActivity
import com.find.phone.gps.location.tracker.screens.pro.ProUpgradeActivity
import com.find.phone.gps.location.tracker.grants.GrantOrchestrator
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilSettings
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilRules
import kotlin.math.abs

class GuidedTourActivity : FoundationActivity<PageOnboardingBinding>() {

    override val layoutId: Int = R.layout.page_onboarding

    private val prefs by lazy { SecurePrefsKeeper(this) }
    private val pages = TourSlides.all
    private val dots = mutableListOf<View>()

    private val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
    private var wasLast = false

    private var forwarding = false

    override fun initView() {
        TourUnveilRules.markShown(this, TourUnveilSettings.ONBOARDING)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        HomeAdsSettings.presentSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.INTRO),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )

        binding.vuPager.adapter = TourPagerAdapter(pages)
        binding.vuPager.offscreenPageLimit = 1
        composeDots()
        updateDots(0)

        binding.vuPager.setPageTransformer { page, position ->
            val w = page.width.toFloat()
            page.findViewById<View?>(R.id.artContainerVw)?.translationX = position * w * 0.4f
            page.findViewById<View?>(R.id.copyVw)?.alpha = (1f - abs(position) * 5f).coerceIn(0f, 1f)
        }

        binding.vuPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                trackEvent("onboarding_page${position + 1}")
            }
        })

        val ui = HomeAdsSettings.onboardingUi(this, HomeAdsSettings.OnboardScreen.INTRO)
        binding.padSkip.isVisible = ui.skipEnabled

        VisualTweens.pressScale(binding.padNext)
        binding.padSkip.setOnClickListener { trackEvent("onboarding_skip_click"); finishOnboarding() }
        binding.padNext.setOnClickListener {
            val current = binding.vuPager.currentItem
            trackEvent("onboarding_next_click", mapOf("page" to current + 1))
            if (current < pages.lastIndex) {
                binding.vuPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.vuPager.currentItem
                if (!HomeRoleJourney.backMovesForward(this@GuidedTourActivity)) {
                    when {
                        current > 0 -> binding.vuPager.setCurrentItem(current - 1, true)

                        !HomeRoleJourney.isOnboardingActive(this@GuidedTourActivity) ->
                            HomeRoleJourney.goHome(this@GuidedTourActivity)
                        else -> HomeRoleJourney.passBackThrough(this@GuidedTourActivity, this)
                    }
                    return
                }
                if (!ui.backAdvances && current < pages.lastIndex) {
                    binding.vuPager.setCurrentItem(current + 1, true)
                } else if (!forwarding) {
                    forwarding = true
                    finishOnboarding()
                }
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun composeDots() {
        val size = dp(6)
        val gap = dp(6)
        pages.indices.forEach { i ->
            val dot = View(this)
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                if (i < pages.lastIndex) marginEnd = gap
            }
            binding.dotsVw.addView(dot)
            dots.add(dot)
        }
    }

    private fun updateDots(active: Int) {
        val activeWidth = dp(24)
        val size = dp(6)
        dots.forEachIndexed { i, dot ->
            tweenDotWidth(dot, if (i == active) activeWidth else size)
            dot.setBackgroundResource(if (i == active) R.drawable.vs_dot_active else R.drawable.vs_dot)
        }
        val last = active == pages.lastIndex
        binding.padNextLabel.setText(if (last) R.string.ds_get_started else R.string.ds_next)
        binding.padNextIcon.isVisible = !last
        if (last != wasLast) {
            wasLast = last
            popCta()
        }
    }

    private fun tweenDotWidth(dot: View, target: Int) {
        val lp = dot.layoutParams as LinearLayout.LayoutParams
        if (lp.width == target) return
        (dot.getTag(R.id.tag_dot_anim) as? ValueAnimator)?.cancel()
        ValueAnimator.ofInt(lp.width, target).apply {
            duration = 320L
            interpolator = spring
            addUpdateListener {
                lp.width = it.animatedValue as Int
                dot.layoutParams = lp
            }
            dot.setTag(R.id.tag_dot_anim, this)
            start()
        }
    }

    private fun popCta() {
        binding.padNext.animate().cancel()
        binding.padNext.scaleX = 0.94f
        binding.padNext.scaleY = 0.94f
        binding.padNext.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(spring)
            .setDuration(340L)
            .start()
    }

    private fun finishOnboarding() {
        prefs.isOnboardingDone = true
        trackEvent("onboarding_completed")
        GrantOrchestrator.check(this) {

            HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.INTRO) {

                if (!HomeRoleJourney.placesPremium(this) && ProUpgradeActivity.showsAfterIntro(this)) {
                    startActivity(AlarmIntroActivity.wrap(this, ProUpgradeActivity.afterIntro(this, HomeRoleJourney.isOnboarding(this))))
                    finish()
                } else if (HomeRoleJourney.isOnboardingActive(this)) {
                    HomeRoleJourney.advance(this)
                } else {
                    val home = Intent(this, HomeRoleJourney.homeActivity())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    val wrapped = AlarmIntroActivity.wrap(this, home)
                    if (wrapped === home) HomeRoleJourney.goHome(this) else { startActivity(wrapped); finish() }
                }
            }
        }
    }
}
