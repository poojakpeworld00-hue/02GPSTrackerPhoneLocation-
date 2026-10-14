package com.find.phone.gps.location.tracker.screens.locale

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

/** Language picker (design 4c): Done confirms, the big native ad is pinned below. */
class LocalePickActivity : FoundationActivity<PageLanguageBinding>() {

    override val layoutId: Int = R.layout.page_language

    private val viewModel: LanguageViewModel by viewModels()
    private val prefs by lazy { SecurePrefsKeeper(this) }

    /** True when opened from Settings to change language (vs. the first-run flow). */
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

        // Big native under the list, `launcher_ads.onboarding.language.slot`.
        HomeAdsSettings.showSlot(
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

        // Resolve the region FIRST, so the Suggested list is right on the very first frame.
        resolveRegion()

        val onPick: (LanguageEntry) -> Unit = { viewModel.select(it.tag) }
        suggestedAdapter = LanguageListAdapter(onPick).apply { setCurrent(current) }
        allAdapter = LanguageListAdapter(onPick).apply { setCurrent(current) }
        binding.rollSuggested.layoutManager = LinearLayoutManager(this)
        binding.rollSuggested.adapter = suggestedAdapter
        binding.rollLanguages.layoutManager = LinearLayoutManager(this)
        binding.rollLanguages.adapter = allAdapter

        binding.padInfo.setOnClickListener { showInfoDialog() }
        VisualTweens.pressScale(binding.padContinue)
        binding.padContinue.setOnClickListener { onContinue() }

        // First-run flow: Back must not exit the app for paid users; it advances like Done.
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

    private fun resolveRegion() {
        val device = deviceCountry()
        SafeLogger.log(TAG, "resolveRegion: device=$device (sync seed)")
        viewModel.applyCountry(device)
        detectCountryByIp()
    }

    private fun deviceCountry(): String? {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = tm?.simCountryIso?.takeIf { it.isNotBlank() }
        val network = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        val locale = resources.configuration.locales[0].country.takeIf { it.isNotBlank() }
        return (sim ?: network ?: locale)?.uppercase()
    }

    private fun detectCountryByIp() {
        lifecycleScope.launch {
            val geo = TerritoryFinder.detectCountry(this@LocalePickActivity) ?: run {
                SafeLogger.log(TAG, "IP geo unavailable → keeping device seed")
                return@launch
            }
            SafeLogger.log(TAG, "IP refine → country=${geo.iso}")
            viewModel.applyCountry(geo.iso)
        }
    }

    /** Small spring on the Done pill each time the selection changes. */
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

    private fun showInfoDialog() {
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
        AppPrefsStore.setLanguage(this, tag)
        prefs.isLanguageSelected = true

        if (standalone) {
            LanguageKeeper.apply(tag)
            finish()
            return
        }

        // First-run flow: permission(s) first, then the exit interstitial, then navigate, then
        // apply the locale (applying it recreates this Activity).
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
                // Later launches: the reminders intro goes right before Home when it is due.
                next == HomeRoleJourney.homeActivity() -> AlarmIntroActivity.wrap(this, Intent(this, next))
                else -> Intent(this, next)
            }
            HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.LANGUAGE) {
                if (navigated) return@runOnboardingInter
                navigated = true
                // Apply the locale BEFORE starting the next screen, so it inflates in the chosen
                // language instead of the old one (the recreate this triggers never lands: this
                // Activity finishes in the same block).
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

        /** Standalone = opened from Settings to change language (returns on Done). */
        fun newIntent(context: Context, standalone: Boolean = false): Intent =
            Intent(context, LocalePickActivity::class.java).putExtra(EXTRA_STANDALONE, standalone)
    }
}
