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
import com.find.phone.gps.location.tracker.monetize.engine.logKeyEvent
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

/**
 * The intro carousel (design 2a). Four hero-card pages; the card and CTA stay fixed while the
 * illustration parallaxes at 0.6× and the copy fades over ~200ms of the swipe. The native
 * banner slot (`launcher_ads.onboarding.intro.slot`) sits under the CTA on the third page.
 */
class GuidedTourActivity : FoundationActivity<PageOnboardingBinding>() {

    override val layoutId: Int = R.layout.page_onboarding

    private val prefs by lazy { SecurePrefsKeeper(this) }
    private val pages = TourSlides.all
    private val dots = mutableListOf<View>()

    // Spring settle used by the dot pill stretch and the CTA morph.
    private val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
    private var wasLast = false

    /** One-shot guard so a first-page back cannot fire the forward flow twice. */
    private var forwarding = false

    override fun initView() {
        TourUnveilRules.markShown(this, TourUnveilSettings.ONBOARDING)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // The slot loads once; its container is only revealed on the page that carries it.
        HomeAdsSettings.showSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.INTRO),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )

        binding.vuPager.adapter = TourPagerAdapter(pages)
        binding.vuPager.offscreenPageLimit = 1
        buildDots()
        updateDots(0)

        // Swipe: the illustration moves at 0.6× the page (parallax), the copy fades out over
        // the first fifth of the swipe (≈200ms of a 1s page change).
        binding.vuPager.setPageTransformer { page, position ->
            val w = page.width.toFloat()
            page.findViewById<View?>(R.id.artContainerVw)?.translationX = position * w * 0.4f
            page.findViewById<View?>(R.id.copyVw)?.alpha = (1f - abs(position) * 5f).coerceIn(0f, 1f)
        }

        binding.vuPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })

        val ui = HomeAdsSettings.onboardingUi(this, HomeAdsSettings.OnboardScreen.INTRO)
        binding.padSkip.isVisible = ui.skipEnabled

        VisualTweens.pressScale(binding.padNext)
        binding.padSkip.setOnClickListener { finishOnboarding() }
        binding.padNext.setOnClickListener {
            val current = binding.vuPager.currentItem
            if (current < pages.lastIndex) {
                binding.vuPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        // Back walks FORWARD through the pages for paid users (every page is seen before the
        // user can leave); organic users get a real Back. `back_action: "next_screen"` leaves
        // for the next screen straight away.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.vuPager.currentItem
                if (!HomeRoleJourney.backMovesForward(this@GuidedTourActivity)) {
                    when {
                        current > 0 -> binding.vuPager.setCurrentItem(current - 1, true)
                        // A later-launch intro (intro_display) has nothing behind it: land on Home
                        // instead of closing the app.
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

    private fun buildDots() {
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
            animateDotWidth(dot, if (i == active) activeWidth else size)
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

    /** Springs a dot's width to [target] (the pill-stretch effect). */
    private fun animateDotWidth(dot: View, target: Int) {
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

    /** Scale spring when the CTA morphs between Next and Get Started. */
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
        logKeyEvent("onboarding_completed")
        GrantOrchestrator.check(this) {
            // Permission done → this screen's interstitial (`onboarding.intro.inter_enabled`,
            // the callback fires immediately when there is nothing to show) → THEN navigate.
            HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.INTRO) {
                // The Premium paywall (design 9c) comes next; it continues this same
                // navigation once it is closed or a plan is bought.
                // Later launches: the reminders intro sits between this screen and Premium / Home
                // (wrap() passes the intent straight through during the first-run onboarding).
                if (ProUpgradeActivity.showsAfterIntro(this)) {
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
