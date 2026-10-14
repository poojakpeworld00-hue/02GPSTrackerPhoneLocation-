package com.find.phone.gps.location.tracker.setupflow

import android.Manifest
import android.animation.Animator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.logKeyEvent
import com.find.phone.gps.location.tracker.databinding.PageReminderIntroBinding
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrantObserver
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilSettings
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilRules
import com.find.phone.gps.location.tracker.helpers.followAdContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.viewBinding

/**
 * Reminders intro (reminders design 5). Two ways in:
 * - first run: the `reminders` step of `launcher_ads.onboarding.order` (default after Welcome);
 * - later launches: the Splash → Language → Terms → Intro chain, right after Intro and before
 *   Premium / Home, gated by `intro_display.reminders_intro` ([wrap]); the screen that would have
 *   come next travels along as [EXTRA_NEXT].
 * "Turn on reminders" asks for notifications (13+), then opens the reminders
 * setting with auto-return; either way the flow then moves on. Skip moves on straight away.
 * Only reached while [HomeRoleJourney] finds the step applicable (`is_intro_screen`, something to
 * allow, country not excluded).
 */
class AlarmIntroActivity : ShellHubActivity() {

    private val binding by viewBinding(PageReminderIntroBinding::inflate)
    private val animators = mutableListOf<Animator>()
    private var awaitingAccess = false
    private var accessPollJob: Job? = null

    /** Whatever the answer, continue to the reminders setting (deferred past the resume). */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        window.decorView.post { openAccessOrNext() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        excludeAppFromRecents()

        val cfg = AlarmSettings.get(this)
        cfg.introTitle.takeIf { it.isNotBlank() }?.let { binding.introTitleVw.text = it }
        cfg.introDesc.takeIf { it.isNotBlank() }?.let { binding.introDescVw.text = it }

        VisualTweens.pressScale(binding.introAllowVw)
        binding.introAllowVw.setOnClickListener { onAllow() }
        binding.introSkipVw.setOnClickListener {
            logKeyEvent("Reminder_Intro_Skip")
            goToNextStep()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (HomeRoleJourney.backMovesForward(this@AlarmIntroActivity)) goToNextStep()
                else HomeRoleJourney.passBackThrough(this@AlarmIntroActivity, this)
            }
        })

        // The "Step n of m" header belongs to the first-run onboarding only.
        if (HomeRoleJourney.isOnboardingActive(this)) {
            HomeRoleJourney.bindStepHeader(this, HomeAdsSettings.OnboardScreen.REMINDERS, binding.root)
        } else {
            binding.onboardingStepHeaderVw.root.visibility = android.view.View.GONE
        }
        TourUnveilRules.markShown(this, TourUnveilSettings.REMINDERS_INTRO)

        // Bottom mid native, `launcher_ads.onboarding.reminders.slot` (off there → frame hidden).
        HomeAdsSettings.showSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.REMINDERS),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)
        logKeyEvent("Reminder_Intro_Show")
        playMotion()
    }

    private fun onAllow() {
        logKeyEvent("Reminder_Intro_Allow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAccessOrNext()
        }
    }

    private fun openAccessOrNext() {
        if (!AlarmGrant.shouldOfferManage(this)) { goToNextStep(); return }
        awaitingAccess = true
        AlarmGrant.openSettings(this)
        AlarmGrantObserver.start(this, AlarmIntroActivity::class.java)
        startAccessPoll()
    }

    override fun onResume() {
        super.onResume()
        // Back from Settings: brought forward once granted, or the user pressed Back.
        if (awaitingAccess) {
            awaitingAccess = false
            accessPollJob?.cancel()
            AlarmGrantObserver.stop(this)
            goToNextStep()
        }
    }

    /** The primary return path: Settings sits in this task, so this Activity may bring itself back. */
    private fun startAccessPoll() {
        accessPollJob?.cancel()
        accessPollJob = lifecycleScope.launch {
            while (isActive) {
                if (AlarmGrant.isGranted(this@AlarmIntroActivity)) {
                    runCatching { startActivity(AlarmGrant.returnIntent(this@AlarmIntroActivity, AlarmIntroActivity::class.java)) }
                    break
                }
                delay(500L)
            }
        }
    }

    /** The phone floats, the chips drift out of phase (the onboarding's `floaty`). */
    private fun playMotion() {
        if (VisualTweens.animationsDisabled(this)) return
        animators += VisualTweens.floaty(binding.introPhoneVw, amplitudeDp = 6f)
        animators += VisualTweens.floaty(binding.introChipTonightVw, delayMs = 600L)
        animators += VisualTweens.floaty(binding.introChipDoneVw, delayMs = 1400L)
    }

    private var leaving = false

    private fun goToNextStep() {
        if (leaving) return
        leaving = true
        accessPollJob?.cancel()
        // The step's exit interstitial, if `launcher_ads.onboarding.reminders` asks for one.
        HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.REMINDERS) {
            val next = nextIntent()
            when {
                next != null -> { startActivity(next); finish() }
                HomeRoleJourney.isOnboardingActive(this) -> HomeRoleJourney.advance(this)
                else -> HomeRoleJourney.goHome(this)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun nextIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_NEXT, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_NEXT)

    override fun onDestroy() {
        accessPollJob?.cancel()
        VisualTweens.cancelAll(animators)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NEXT = "reminders_intro_next"

        /**
         * The later-launches chain: [next] itself, or this intro first (carrying [next] on) when
         * [TourUnveilRules.shouldShowRemindersIntro] says so. Never during the first-run
         * onboarding, which has its own `reminders` step.
         */
        fun wrap(activity: android.app.Activity, next: Intent): Intent =
            if (!HomeRoleJourney.isOnboardingActive(activity) && TourUnveilRules.shouldShowRemindersIntro(activity)) {
                Intent(activity, AlarmIntroActivity::class.java).putExtra(EXTRA_NEXT, next)
            } else {
                next
            }
    }
}
