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

class PolicyConsentActivity : FoundationActivity<PageTermsBinding>() {

    override val layoutId: Int = R.layout.page_terms

    private val prefs by lazy { SecurePrefsKeeper(this) }

    private var awaitingOverlay = false
    private var settingsShown = false
    private var proceeded = false
    private var watchRegistered = false

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

        TourUnveilRules.markShown(this, TourUnveilSettings.TERMS)

        ViewCompat.setOnApplyWindowInsetsListener(binding.termsRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.chkAgree.isChecked = true
        setAcceptEnabled(true)

        binding.chkAgree.setOnCheckedChangeListener { _, checked -> setAcceptEnabled(checked) }

        binding.lblAgree.setOnClickListener { binding.chkAgree.toggle() }
        binding.padAccept.setOnClickListener { onAccept() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                prefs.isTermsAccepted = true
                proceedToNextScreen()
            }
        })
    }

    override fun onResume() {
        super.onResume()

        if (awaitingOverlay && settingsShown) {
            awaitingOverlay = false
            settingsShown = false
            proceedToNextScreen()
        }
    }

    override fun onPause() {
        super.onPause()

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

    private fun unfoldOverlayPermission() {

        if (OverlayToolbox.isGranted(this) || !OverlayToolbox.isOfferable(this)) {
            proceedToNextScreen()
            return
        }

        val launched = runCatching {
            overlayLauncher.launch(OverlayToolbox.composeOverlayIntent(packageName))
        }.isSuccess

        if (!launched) {

            proceedToNextScreen()
            return
        }

        awaitingOverlay = true
        settingsShown = false

        ContextCompat.registerReceiver(
            this,
            overlayGrantedReceiver,
            IntentFilter(OverlayMonitorService.ACTION_OVERLAY_GRANTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        watchRegistered = true
        OverlayMonitorService.start(this)
    }

    private fun proceedToNextScreen() {
        if (proceeded) return
        proceeded = true
        stopWatch()

        val next = if (TourUnveilRules.shouldShowOnboarding(this)) GuidedTourActivity::class.java
        else HomeRoleJourney.homeActivity()
        if (next == HomeRoleJourney.homeActivity()) {
            HomeRoleJourney.markOnboardingCompleted(this)
        }
        val intent = Intent(this, next).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        startActivity(if (next == HomeRoleJourney.homeActivity()) AlarmIntroActivity.wrap(this, intent) else intent)
        finish()
    }

    private fun stopWatch() {
        if (watchRegistered) {
            runCatching { unregisterReceiver(overlayGrantedReceiver) }
            watchRegistered = false
        }
        OverlayMonitorService.stop(this)
    }
}
