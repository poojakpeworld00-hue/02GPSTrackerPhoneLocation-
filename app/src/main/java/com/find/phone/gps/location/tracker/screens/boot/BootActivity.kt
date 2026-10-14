package com.find.phone.gps.location.tracker.screens.boot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.find.phone.gps.location.tracker.setupflow.AlarmIntroActivity
import com.find.phone.gps.location.tracker.alarms.AskGatekeeper
import io.lighthouse.push.LightHouse
import io.lighthouse.push.extended.LightHouseRichPush
import com.find.phone.gps.location.tracker.monetize.model.OnPayloadReady
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.monetize.render.presentAppRedirectPopup
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.databinding.PageSplashBinding
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.setupflow.WalkthroughHomeAdvance
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.reveal.TourUnveilRules
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity
import com.find.phone.gps.location.tracker.screens.walkthrough.GuidedTourActivity
import com.find.phone.gps.location.tracker.screens.consent.PolicyConsentActivity
import com.find.phone.gps.location.tracker.helpers.unfoldActivity
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class BootActivity : FoundationActivity<PageSplashBinding>() {

    override val layoutId: Int = R.layout.page_splash
    private val handler = Handler(Looper.getMainLooper())
    private var motion: BootTweens? = null

    private val prefs by lazy { SecurePrefsKeeper(this) }

    private val proceeded = AtomicBoolean(false)

    private val dataReady = AtomicBoolean(false)
    private val animMinElapsed = AtomicBoolean(false)

    private companion object {

        const val SPLASH_FLOW_TAG = "SplashFlow"

        const val ANIM_MIN_MS = BootTweens.CYCLE_MS

        const val ANIM_MIN_REDUCED_MS = 700L
    }

    override fun initView() {

        if (LightHouseRichPush.handleFromSplash(this, binding.root)) return

        prefs.appLaunchCount = prefs.appLaunchCount + 1

        AskGatekeeper.onAppLaunch(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.splashRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        motion = BootTweens(binding).also { it.start() }
        val animMin = if (VisualTweens.animationsDisabled(this)) ANIM_MIN_REDUCED_MS else ANIM_MIN_MS
        handler.postDelayed({
            Log.d(SPLASH_FLOW_TAG, "animation min elapsed (${animMin}ms) → perhapsProceed")
            animMinElapsed.set(true)
            perhapsProceed()
        }, animMin)

        printHashKey(this)
        Log.d(SPLASH_FLOW_TAG, "SplashActivity.initView → getData()")

        getData(this, true, object : OnPayloadReady {
            override fun onSuccess() {
                Log.d(SPLASH_FLOW_TAG, "getData onSuccess → dataReady")
                ResumeAdKeeper.loadAd(this@BootActivity)
                dataReady.set(true)
                perhapsProceed()
            }

            override fun onError() {
                Log.d(SPLASH_FLOW_TAG, "getData onError → dataReady")
                dataReady.set(true)
                perhapsProceed()
            }
        })
    }

    private fun perhapsProceed() {
        if (dataReady.get() && animMinElapsed.get()) {
            Log.d(SPLASH_FLOW_TAG, "both gates met (data + animation) → proceedNow")
            proceedNow()
        }
    }

    private fun proceedNow() {
        if (!proceeded.compareAndSet(false, true)) {
            Log.d(SPLASH_FLOW_TAG, "proceedNow ignored (already proceeded)")
            return
        }
        handler.removeCallbacksAndMessages(null)
        if (isFinishing || isDestroyed) {
            Log.d(SPLASH_FLOW_TAG, "proceedNow skipped (finishing/destroyed)")
            return
        }
        val redirectLink = AdPrefsStore.getInstance(this).getString("In_App_Update_Link")
        if (!redirectLink.isNullOrEmpty()) {
            Log.d(SPLASH_FLOW_TAG, "proceedNow → presentAppRedirectPopup")
            presentAppRedirectPopup { fireNext() }
        } else {
            Log.d(SPLASH_FLOW_TAG, "proceedNow → fireNext")
            fireNext()
        }
    }

    private fun fireNext() {
        if (isFinishing || isDestroyed) {
            Log.d(SPLASH_FLOW_TAG, "fireNext skipped (finishing/destroyed)")
            return
        }
        Log.d(SPLASH_FLOW_TAG, "fireNext → ensureDataDisclosure")

        ResumeAdKeeper.skipNextAppOpenAd = true
        LightHouse.ensureDataDisclosure(this) {
            if (isFinishing || isDestroyed) return@ensureDataDisclosure
            LightHouse.subscribeAsync()

            val launcherOnboarding = !HomeRoleJourney.wasOnboardingCompleted(this)
            val next = nextScreen()
            Log.d(SPLASH_FLOW_TAG, "ensureDataDisclosure done → launching ${next.simpleName}")

            val intent = when {
                launcherOnboarding && next != HomeRoleJourney.homeActivity() -> HomeRoleJourney.onboardingIntent(this, next)

                next == HomeRoleJourney.homeActivity() -> AlarmIntroActivity.wrap(this, Intent(this, next))
                else -> Intent(this, next)
            }

            unfoldActivity(intent, isShowAd = false)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        motion?.cancel()
        motion = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun nextScreen(): Class<*> = when {

        !HomeRoleJourney.wasOnboardingCompleted(this) ->
            WalkthroughHomeAdvance.resumeScreen(this) ?: HomeRoleJourney.firstScreen(this)
        TourUnveilRules.shouldShowLanguage(this) -> LocalePickActivity::class.java
        TourUnveilRules.shouldShowTerms(this) -> PolicyConsentActivity::class.java
        TourUnveilRules.shouldShowOnboarding(this) -> GuidedTourActivity::class.java
        else -> HomeRoleJourney.homeActivity()
    }

    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun printHashKey(context: Context) {
        try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            } ?: return

            signatures.forEach { sig ->
                val md = MessageDigest.getInstance("SHA")
                md.update(sig.toByteArray())
                Log.d("KeyHash", Base64.encodeToString(md.digest(), Base64.DEFAULT))
            }
        } catch (_: Exception) {
        }
    }
}
