package com.find.phone.gps.location.tracker.screens.locale

import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.LanguageKeeper
import com.find.phone.gps.location.tracker.repo.TerritoryFinder
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.databinding.PageLanguageBinding
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.setupflow.AlarmIntroActivity
import com.find.phone.gps.location.tracker.grants.GrantOrchestrator
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilSettings
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilRules
import com.find.phone.gps.location.tracker.screens.walkthrough.GuidedTourActivity
import com.find.phone.gps.location.tracker.screens.consent.PolicyConsentActivity
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.find.phone.gps.location.tracker.helpers.followAdContainer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LocalePickActivity : FoundationActivity<PageLanguageBinding>() {

    override val layoutId: Int = R.layout.page_language

    private val viewModel: LanguageViewModel by viewModels()
    private val prefs by lazy { SecurePrefsKeeper(this) }

    private val standalone by lazy { intent.getBooleanExtra(EXTRA_STANDALONE, false) }

    private lateinit var suggestedAdapter: LanguageListAdapter
    private lateinit var allAdapter: LanguageListAdapter

    private var forwarding = false
    private var navigated = false

    override fun initView() {
        if (!standalone) TourUnveilRules.markShown(this, TourUnveilSettings.LANGUAGE)

        ViewCompat.setOnApplyWindowInsetsListener(binding.languageRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val current = AppPrefsStore.language(this) ?: AppPrefsStore.LANGUAGE_DEFAULT
        viewModel.init(current)

        HomeAdsSettings.presentSlot(
            activity = this,
            slot = HomeAdsSettings.onboardingSlot(this, HomeAdsSettings.OnboardScreen.LANGUAGE),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        if (!standalone) {
            HomeRoleJourney.bindStepHeader(this, HomeAdsSettings.OnboardScreen.LANGUAGE, binding.root)
        } else {
            binding.root.findViewById<View>(R.id.onboarding_step_headerVw).isVisible = false
        }

        settleRegion()

        val onPick: (LanguageEntry) -> Unit = { viewModel.select(it.tag) }
        suggestedAdapter = LanguageListAdapter(onPick).apply { setCurrent(current) }
        allAdapter = LanguageListAdapter(onPick).apply { setCurrent(current) }
        binding.rollSuggested.layoutManager = LinearLayoutManager(this)
        binding.rollSuggested.adapter = suggestedAdapter
        binding.rollLanguages.layoutManager = LinearLayoutManager(this)
        binding.rollLanguages.adapter = allAdapter

        binding.padInfo.setOnClickListener { trackEvent("language_info_click"); presentInfoDialog() }
        VisualTweens.pressScale(binding.padContinue)
        binding.padContinue.setOnClickListener { onContinue() }

        if (!standalone) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!HomeRoleJourney.backMovesForward(this@LocalePickActivity)) {
                        HomeRoleJourney.passBackThrough(this@LocalePickActivity, this)
                        return
                    }
                    if (forwarding) return
                    forwarding = true
                    onContinue()
                }
            })
        }
    }

    override fun initObservers() {
        viewModel.selectedTag.observe(this) { tag ->
            suggestedAdapter.setSelected(tag)
            allAdapter.setSelected(tag)
            popConfirm()
        }
        viewModel.suggested.observe(this) {
            suggestedAdapter.submitList(it)
            binding.lblSuggested.isVisible = it.isNotEmpty()
            binding.rollSuggested.isVisible = it.isNotEmpty()
        }
        viewModel.others.observe(this) {
            allAdapter.submitList(it)
            binding.lblAll.isVisible = it.isNotEmpty()
            binding.rollLanguages.isVisible = it.isNotEmpty()
        }
    }

    private fun settleRegion() {
        val device = deviceCountry()
        SafeLogger.log(TAG, "settleRegion: device=$device (sync seed)")
        viewModel.imprintCountry(device)
        senseCountryByIp()
    }

    private fun deviceCountry(): String? {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = tm?.simCountryIso?.takeIf { it.isNotBlank() }
        val network = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        val locale = resources.configuration.locales[0].country.takeIf { it.isNotBlank() }
        return (sim ?: network ?: locale)?.uppercase()
    }

    private fun senseCountryByIp() {
        lifecycleScope.launch {
            val geo = TerritoryFinder.senseCountry(this@LocalePickActivity) ?: run {
                SafeLogger.log(TAG, "IP geo unavailable → keeping device seed")
                return@launch
            }
            SafeLogger.log(TAG, "IP refine → country=${geo.iso}")
            viewModel.imprintCountry(geo.iso)
        }
    }

    private fun popConfirm() {
        binding.padContinue.animate().cancel()
        binding.padContinue.scaleX = 0.8f
        binding.padContinue.scaleY = 0.8f
        binding.padContinue.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3f))
            .setDuration(260L)
            .start()
    }

    private fun presentInfoDialog() {
        val view = layoutInflater.inflate(R.layout.overlay_language_info, binding.languageRootVw, false)
        val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<View>(R.id.padGotIt).setOnClickListener { dialog.dismiss() }
        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.85f).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun onContinue() {
        val tag = viewModel.selectedTag.value ?: AppPrefsStore.LANGUAGE_DEFAULT
        trackEvent(
            if (viewModel.userPicked) "language_selected" else "language_default_selected",
            mapOf("language" to tag),
        )
        trackEvent("language_done_click")
        AppPrefsStore.setLanguage(this, tag)
        prefs.isLanguageSelected = true

        if (standalone) {
            LanguageKeeper.apply(tag)
            finish()
            return
        }

        GrantOrchestrator.check(this) {
            val next = when {
                !standalone && HomeRoleJourney.isOnboardingActive(this) -> HomeRoleJourney.nextActivity(this)
                TourUnveilRules.shouldShowTerms(this) -> PolicyConsentActivity::class.java
                TourUnveilRules.shouldShowOnboarding(this) -> GuidedTourActivity::class.java
                else -> HomeRoleJourney.homeActivity()
            }
            if (next == HomeRoleJourney.homeActivity()) {
                HomeRoleJourney.markOnboardingCompleted(this)
            }
            val intent = when {
                !standalone && HomeRoleJourney.isOnboardingActive(this) && next != HomeRoleJourney.homeActivity() ->
                    HomeRoleJourney.onboardingIntent(this, next)

                next == HomeRoleJourney.homeActivity() -> AlarmIntroActivity.wrap(this, Intent(this, next))
                else -> Intent(this, next)
            }
            HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.LANGUAGE) {
                if (navigated) return@runOnboardingInter
                navigated = true

                if (tag != AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                    LanguageKeeper.apply(tag)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "LocalePickActivity"
        private const val EXTRA_STANDALONE = "extra_standalone"

        fun newIntent(context: Context, standalone: Boolean = false): Intent =
            Intent(context, LocalePickActivity::class.java).putExtra(EXTRA_STANDALONE, standalone)
    }
}
