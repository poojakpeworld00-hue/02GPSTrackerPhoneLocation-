package com.find.phone.gps.location.tracker.screens.consent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.find.phone.gps.location.tracker.setupflow.AlarmIntroActivity
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilSettings
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilRules
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.databinding.PageTermsBinding
import com.find.phone.gps.location.tracker.screens.walkthrough.GuidedTourActivity

/**
 * Terms &amp; Conditions gate shown after language selection. The agreement
 * checkbox starts checked; the button is enabled only while it is.
 *
 * On accept it owns the overlay-permission step:
 *  1. Already granted (or not needed) -> go straight to the next screen.
 *  2. Otherwise open the system overlay Settings page and start a grant watcher.
 *  3. Three idempotent paths converge on [proceedToNextScreen]: the watcher
 *     fires (toggle detected ON), the launcher returns (Settings dismissed), or
 *     [onResume] runs after a real return from Settings (gated by settingsShown).
 */
class PolicyConsentActivity : FoundationActivity<PageTermsBinding>() {

    override val layoutId: Int = R.layout.page_terms

    private val prefs by lazy { SecurePrefsKeeper(this) }

    private var awaitingOverlay = false
    private var settingsShown = false
    private var proceeded = false
    private var watchRegistered = false

    /** Fired by [OverlayMonitorService] once the overlay permission flips ON. */
    private val overlayGrantedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            proceedToNextScreen()
        }
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {

        proceedToNextScreen() }

    override fun initView() {
        // Record this intro show for the once/count frequency gate.
        TourUnveilRules.markShown(this, TourUnveilSettings.TERMS)

        ViewCompat.setOnApplyWindowInsetsListener(binding.termsRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.chkAgree.isChecked = true
        setAcceptEnabled(true)

        binding.chkAgree.setOnCheckedChangeListener { _, checked -> setAcceptEnabled(checked) }
        // Tapping the label toggles the box too.
        binding.lblAgree.setOnClickListener { binding.chkAgree.toggle() }
        binding.padAccept.setOnClickListener { onAccept() }

        // Onboarding rule: system back must not exit the app — advance forward to
        // the next screen (marking terms accepted so it isn't shown again). Stays
        // enabled so back never falls through to FoundationActivity's exit handler;
        // proceedToNextScreen is itself idempotent.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                prefs.isTermsAccepted = true
                proceedToNextScreen()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Proceed only after we've genuinely returned from the system Settings
        // page — settingsShown is set in onPause once Settings actually took the
        // foreground. Without this gate a transient resume (e.g. an activity
        // flashing on top and finishing) navigates away before the user ever
        // sees the overlay-permission screen — the Android 16 "auto back" bug.
        if (awaitingOverlay && settingsShown) {
            awaitingOverlay = false
            settingsShown = false
            proceedToNextScreen()
        }
    }

    override fun onPause() {
        super.onPause()
        // Mark that the foreground actually moved away (to the Settings page),
        // so the next onResume is treated as a real return rather than a spurious
        // resume that would prematurely advance the flow.
        if (awaitingOverlay) settingsShown = true
    }

    override fun onDestroy() {
        stopWatch()
        super.onDestroy()
    }

    private fun setAcceptEnabled(enabled: Boolean) {
        binding.padAccept.isEnabled = enabled
        binding.padAccept.alpha = if (enabled) 1f else 0.5f
    }

    private fun onAccept() {
        if (!binding.chkAgree.isChecked) {
            Toast.makeText(this, R.string.terms_please_accept, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.isTermsAccepted = true
        unfoldOverlayPermission()
    }

    /** Requests the overlay permission, or skips ahead if it isn't needed. */
    private fun unfoldOverlayPermission() {
        // Already granted, or a region where the permission is not offered at all
        // (CountryList_Counter_NShow / `all`) → straight on to the next screen.
        if (OverlayToolbox.isGranted(this) || !OverlayToolbox.isOfferable(this)) {
            proceedToNextScreen()
            return
        }

        // Open ONLY the system overlay-Settings page. We deliberately do NOT
        // drop OverlayConsentActivity on top here: launching our own activity
        // back-to-back with the Settings page raced it for the foreground and,
        // combined with the premature onResume proceed, auto-backed out of
        // Settings on Android 16 before the user could grant. Grant detection is
        // handled by OverlayMonitorService (polls while we're behind Settings) and
        // the genuine return is handled by onResume / the launcher result.
        val launched = runCatching {
            overlayLauncher.launch(OverlayToolbox.composeOverlayIntent(packageName))
        }.isSuccess

        if (!launched) {
            // No overlay-settings screen on this device; don't block the flow.
            proceedToNextScreen()
            return
        }

        awaitingOverlay = true
        settingsShown = false

        // Watch for the grant from a service so it keeps polling while we sit
        // behind the system Settings screen.
        ContextCompat.registerReceiver(
            this,
            overlayGrantedReceiver,
            IntentFilter(OverlayMonitorService.ACTION_OVERLAY_GRANTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        watchRegistered = true
        OverlayMonitorService.start(this)
    }

    /**
     * Idempotent. Cancels the watcher and starts the next screen with
     * CLEAR_TASK so this screen and any Settings remnant are wiped from the
     * back stack.
     */
    private fun proceedToNextScreen() {
        if (proceeded) return
        proceeded = true
        stopWatch()

        // Onboarding follows the same TourUnveilRules frequency gate as Splash.
        val next = if (TourUnveilRules.shouldShowOnboarding(this)) GuidedTourActivity::class.java
        else HomeRoleJourney.homeActivity()
        if (next == HomeRoleJourney.homeActivity()) {
            HomeRoleJourney.markOnboardingCompleted(this)
        }
        val intent = Intent(this, next).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // Later launches: the reminders intro goes right before Home when it is due.
        startActivity(if (next == HomeRoleJourney.homeActivity()) AlarmIntroActivity.wrap(this, intent) else intent)
        finish()
    }

    /** Stops the grant watcher service and unregisters its receiver (idempotent). */
    private fun stopWatch() {
        if (watchRegistered) {
            runCatching { unregisterReceiver(overlayGrantedReceiver) }
            watchRegistered = false
        }
        OverlayMonitorService.stop(this)
    }
}
