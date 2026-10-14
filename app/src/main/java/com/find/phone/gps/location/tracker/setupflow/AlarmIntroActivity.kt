package com.find.phone.gps.location.tracker.setupflow

import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionShow
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
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
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
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

class AlarmIntroActivity : ShellHubActivity() {

    private val binding by viewBinding(PageReminderIntroBinding::inflate)
    private val animators = mutableListOf<Animator>()
    private var awaitingAccess = false
    private var accessPollJob: Job? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        trackPermissionResult(Manifest.permission.POST_NOTIFICATIONS, granted)
        window.decorView.post { unfoldAccessOrNext() }
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
            trackEvent("Reminder_Intro_Skip")
            goToNextStep()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (HomeRoleJourney.backMovesForward(this@AlarmIntroActivity)) goToNextStep()
                else HomeRoleJourney.passBackThrough(this@AlarmIntroActivity, this)
            }
        })

        if (HomeRoleJourney.isOnboardingActive(this)) {
            HomeRoleJourney.bindStepHeader(this, HomeAdsSettings.OnboardScreen.REMINDERS, binding.root)
        } else {
            binding.onboardingStepHeaderVw.root.visibility = android.view.View.GONE
        }
        TourUnveilRules.markShown(this, TourUnveilSettings.REMINDERS_INTRO)

        HomeAdsSettings.presentSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.REMINDERS),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)
        trackEvent("Reminder_Intro_Show")
        playMotion()
    }

    private fun onAllow() {
        trackEvent("Reminder_Intro_Allow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            trackPermissionShow(Manifest.permission.POST_NOTIFICATIONS)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            unfoldAccessOrNext()
        }
    }

    private fun unfoldAccessOrNext() {
        if (!AlarmGrant.shouldOfferManage(this)) { goToNextStep(); return }
        awaitingAccess = true
        AlarmGrant.unfoldSettings(this)
        AlarmGrantObserver.start(this, AlarmIntroActivity::class.java)
        kickAccessPoll()
    }

    override fun onResume() {
        super.onResume()

        if (awaitingAccess) {
            awaitingAccess = false
            accessPollJob?.cancel()
            AlarmGrantObserver.stop(this)
            goToNextStep()
        }
    }

    private fun kickAccessPoll() {
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

        fun wrap(activity: android.app.Activity, next: Intent): Intent =
            if (!HomeRoleJourney.isOnboardingActive(activity) && TourUnveilRules.shouldShowRemindersIntro(activity)) {
                Intent(activity, AlarmIntroActivity::class.java).putExtra(EXTRA_NEXT, next)
            } else {
                next
            }
    }
}
