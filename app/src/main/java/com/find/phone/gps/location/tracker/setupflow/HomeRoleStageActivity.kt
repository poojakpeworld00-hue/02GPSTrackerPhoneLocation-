package com.find.phone.gps.location.tracker.setupflow

import com.find.phone.gps.location.tracker.BuildConfig
import android.animation.Animator
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.find.phone.gps.location.tracker.databinding.PageOnboardingDefaultLauncherBinding
import com.find.phone.gps.location.tracker.grants.GrantOrchestrator
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.helpers.concealWhileAdVisible
import io.homedeck.grid.ext.isDefaultLauncher
import io.homedeck.grid.ext.roleManager
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.isQPlus

class HomeRoleStageActivity : ShellHubActivity(), WalkthroughHomeAdvance.Step {

    companion object {
        private const val REQ_HOME_SETTINGS = 7011
        private const val REQ_ROLE_HOME = 7012
        private const val TAG = "HomeRoleStep"

        const val EXTRA_ROLE_GRANTED = "role_granted"

        private var lastRequest = 0
        private var grantLogged = false
    }

    private val binding by viewBinding(PageOnboardingDefaultLauncherBinding::inflate)
    private val animators = mutableListOf<Animator>()

    private var leaving = false

    private var requestInFlight = false

    private var autoOpenPending = false

    private var askedOnDecline = false

    private var skipEnabled = true

    private var finishingGrant = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        excludeAppFromRecents()

        if (intent.getBooleanExtra(EXTRA_ROLE_GRANTED, false)) {
            finishingGrant = true
            binding.root.isInvisible = true
            return
        }

        if (savedInstanceState == null) trackEvent("default_home_view")

        VisualTweens.pressScale(binding.onboardingSetDefaultVw)
        binding.onboardingSetDefaultVw.setOnClickListener {
            if (isDefaultLauncher()) goHome() else unfoldHomeSettings()
        }
        binding.onboardingSkipVw.setOnClickListener {
            trackEvent("default_home_skip")
            goToNextStep()
        }

        val ui = HomeAdsSettings.onboardingUi(this, HomeAdsSettings.OnboardScreen.SET_DEFAULT)
        skipEnabled = ui.skipEnabled
        binding.onboardingSkipVw.isVisible = ui.skipEnabled

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (HomeRoleJourney.backMovesForward(this@HomeRoleStageActivity)) promptForRole()
                else HomeRoleJourney.passBackThrough(this@HomeRoleStageActivity, this)
            }
        })

        HomeAdsSettings.presentSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.SET_DEFAULT),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )

        binding.imageHeroVw.concealWhileAdVisible(binding.adNativeFrameVw, binding.heroCompactVw)

        HomeRoleJourney.bindStepHeader(this, HomeAdsSettings.OnboardScreen.SET_DEFAULT, binding.root)

        val qaHold = BuildConfig.DEBUG && intent.getBooleanExtra("qa_no_auto_open", false)

        if (savedInstanceState == null && !qaHold && !WalkthroughHomeAdvance.isSavedFor(this)) autoOpenPending = true

        playMotion()
    }

    private fun playMotion() {
        if (VisualTweens.animationsDisabled(this)) return
        animators += VisualTweens.floaty(binding.chooserCardVw)
        animators += VisualTweens.floaty(binding.flagSwipe, delayMs = 1400L)
    }

    private fun unfoldHomeSettings() {
        if (leaving || requestInFlight) return
        fireHomeSettings()

        HomeRoleNudgeDialog.presentAfterSettings(this)
    }

    private fun fireHomeSettings() {
        if (leaving) return
        val opened = fireForResult(Intent(Settings.ACTION_HOME_SETTINGS), REQ_HOME_SETTINGS) ||
            fireForResult(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), REQ_HOME_SETTINGS)

        if (!opened) promptForRole()
    }

    private fun promptForRole() {
        if (leaving || requestInFlight) return
        if (!isQPlus()) {
            goToNextStep()
            return
        }
        if (!fireForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE_HOME)) {
            goToNextStep()
        }
    }

    @Suppress("DEPRECATION")
    private fun fireForResult(intent: Intent, requestCode: Int): Boolean = try {
        startActivityForResult(intent, requestCode)
        requestInFlight = true
        lastRequest = requestCode
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        requestInFlight = false
        if (leaving) return

        if (isDefaultLauncher()) return
        when (requestCode) {
            REQ_HOME_SETTINGS -> promptForRole()
            REQ_ROLE_HOME -> {

                trackEvent("default_home_declined")
                if (resultCode == RESULT_OK || !skipEnabled) goToNextStep() else promptOnDecline()
            }
        }
    }

    private fun promptOnDecline() {
        if (askedOnDecline) return
        askedOnDecline = true
        GrantOrchestrator.check(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ROLE_GRANTED, false)) {
            finishingGrant = true
            binding.root.isInvisible = true
        }
    }

    override fun onResume() {
        super.onResume()
        HomeRoleNudgeDialog.dismiss()
        if (isDefaultLauncher()) {
            noteGrant()
            if (finishingGrant) goHome() else advanceAfterGrant()
            return
        }
        if (autoOpenPending) {
            autoOpenPending = false
            unfoldHomeSettings()
        }
    }

    private fun noteGrant() {
        if (grantLogged || lastRequest == 0) return
        grantLogged = true
        trackEvent(if (lastRequest == REQ_ROLE_HOME) "default_home_granted_dialog" else "default_home_granted_settings")
    }

    private fun advanceAfterGrant() {
        val (auto, delayMs) = HomeAdsSettings.onboardingAutoNext(this, HomeAdsSettings.OnboardScreen.SET_DEFAULT)
        if (!auto) return
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) goHome()
        }, delayMs)
    }

    private fun goHome() {
        if (leaving) return
        leaving = true
        val next = {
            HomeRoleJourney.advance(
                activity = this,
                skipRest = HomeAdsSettings.defaultHomeStep(this).skipRestOnGrant,
            )
        }

        if (finishingGrant && !FullscreenOnAction.hasReadyInter(this)) {
            Log.d(TAG, "grant finished, no interstitial ready → next step")
            return next()
        }
        HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.SET_DEFAULT) { next() }
    }

    override fun onHomeForward() = goToNextStep()

    private fun goToNextStep() {
        if (leaving) return
        leaving = true
        HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.SET_DEFAULT) {
            HomeRoleJourney.advance(this)
        }
    }

    override fun onDestroy() {
        VisualTweens.cancelAll(animators)
        HomeRoleNudgeDialog.dismiss()
        super.onDestroy()
    }
}
