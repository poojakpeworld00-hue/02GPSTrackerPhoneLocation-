package com.find.phone.gps.location.tracker.setupflow

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.OnboardScreen
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.TrackerPhoneApp
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant
import io.homedeck.grid.screens.HomeDeskPane
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import org.fossify.commons.extensions.getSharedPrefs
import io.homedeck.grid.ext.isDefaultLauncher
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity
import com.find.phone.gps.location.tracker.screens.walkthrough.GuidedTourActivity
import com.find.phone.gps.location.tracker.screens.pro.ProUpgradeActivity
import com.find.phone.gps.location.tracker.homeshell.HomeModeToggle

object HomeRoleJourney {

    private const val TAG = "HomeRoleJourney"

    private const val WAS_ONBOARDING_COMPLETED = "was_onboarding_completed"
    private const val ONBOARDING_STEP = "onboarding_step"
    private const val ALREADY_DEFAULT_LOGGED = "default_home_already_logged"

    const val EXTRA_LAUNCHER_ONBOARDING = "extra_launcher_onboarding"

    fun isOnboarding(activity: Activity): Boolean =
        activity.intent.getBooleanExtra(EXTRA_LAUNCHER_ONBOARDING, false)

    fun isOnboardingActive(activity: Activity): Boolean =
        isOnboarding(activity) || !wasOnboardingCompleted(activity)

    fun backMovesForward(context: Context): Boolean =
        AdPrefsStore.getInstance(context).getBoolean("OnMaketing")

    fun passBackThrough(activity: ComponentActivity, callback: OnBackPressedCallback) {
        callback.isEnabled = false
        activity.onBackPressedDispatcher.onBackPressed()
    }

    fun wasOnboardingCompleted(context: Context): Boolean =
        context.getSharedPrefs().getBoolean(WAS_ONBOARDING_COMPLETED, false)

    fun onboardingIntent(context: Context, target: Class<*>): Intent =
        Intent(context, target).putExtra(EXTRA_LAUNCHER_ONBOARDING, true)

    fun markOnboardingCompleted(context: Context) {
        context.getSharedPrefs().edit().putBoolean(WAS_ONBOARDING_COMPLETED, true).apply()
    }

    fun goHome(activity: Activity) {
        markOnboardingCompleted(activity)
        activity.startActivity(
            Intent(activity, homeActivity()).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        activity.finish()
    }

    fun homeActivity(): Class<*> =
        if (!HomeModeToggle.isEnabled(TrackerPhoneApp.appContext) ||
            AdPrefsStore.getInstance(TrackerPhoneApp.appContext).getString(HOME_KEY)
                ?.trim()?.lowercase() == "app"
        ) {
            MainHubActivity::class.java
        } else {
            HomeDeskPane::class.java
        }

    private const val HOME_KEY = "onboarding_home"

    private val NUMBERED_STEPS = setOf(
        OnboardScreen.SET_DEFAULT,
        OnboardScreen.LANGUAGE,
        OnboardScreen.WELCOME,
        OnboardScreen.REMINDERS,
    )

    private val LAUNCHER_ONLY_STEPS = setOf(OnboardScreen.SET_DEFAULT, OnboardScreen.WELCOME)

    data class StepPosition(val index: Int, val total: Int)

    fun stepPosition(context: Context, screen: OnboardScreen): StepPosition? {
        val launcher = HomeModeToggle.isEnabled(context)
        val numbered = HomeAdsSettings.onboardingOrder(context)
            .filter { it in NUMBERED_STEPS && (launcher || it !in LAUNCHER_ONLY_STEPS) }

            .filter { it != OnboardScreen.REMINDERS || isApplicable(context, it) }
        val pos = numbered.indexOf(screen)
        return if (pos < 0) null else StepPosition(pos + 1, numbered.size)
    }

    fun bindStepHeader(context: Context, screen: OnboardScreen, root: View) {
        val header = root.findViewById<View>(R.id.onboarding_step_headerVw) ?: return
        val position = stepPosition(context, screen)
        if (position == null) {
            header.visibility = View.GONE
            return
        }
        header.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.onboarding_step_pillVw).text =
            context.getString(R.string.onboarding_step_counter, position.index, position.total)
        root.findViewById<ProgressBar>(R.id.onboarding_step_progressVw).progress =
            position.index * 100 / position.total
    }

    fun firstScreen(context: Context): Class<*> = settleFrom(context, 0)

    fun currentStep(context: Context): Int = context.getSharedPrefs().getInt(ONBOARDING_STEP, 0)

    fun resumeScreen(context: Context, step: Int): Class<*> = settleFrom(context, step)

    fun advance(activity: Activity, skipRest: Boolean = false) {
        if (skipRest) {
            log("skipping the rest of the order")
            return goHome(activity)
        }

        val next = nextActivity(activity)
        if (next == homeActivity()) {
            return goHome(activity)
        }

        activity.startActivity(onboardingIntent(activity, next))
        activity.finish()
    }

    fun nextActivity(context: Context): Class<*> =
        settleFrom(context, context.getSharedPrefs().getInt(ONBOARDING_STEP, 0) + 1)

    fun resumeIfUnfinished(activity: Activity): Boolean {
        if (wasOnboardingCompleted(activity)) {
            return false
        }

        val step = activity.getSharedPrefs().getInt(ONBOARDING_STEP, 0)
        if (HomeAdsSettings.onboardingOrder(activity).getOrNull(step) == OnboardScreen.SET_DEFAULT &&
            activity.isDefaultLauncher()
        ) {
            log("home role granted mid-run → back to set_default to finish it")
            activity.startActivity(
                onboardingIntent(activity, HomeRoleStageActivity::class.java)
                    .putExtra(HomeRoleStageActivity.EXTRA_ROLE_GRANTED, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            return true
        }

        val next = settleFrom(activity, step)
        if (next == homeActivity()) {
            return false
        }

        log("home reached mid-run → resuming onboarding")
        activity.startActivity(onboardingIntent(activity, next))
        return true
    }

    private fun settleFrom(context: Context, from: Int): Class<*> {
        val order = HomeAdsSettings.onboardingOrder(context)
        var index = from

        while (index < order.size) {
            val screen = order[index]
            if (isApplicable(context, screen)) {
                context.getSharedPrefs().edit().putInt(ONBOARDING_STEP, index).apply()
                log("→ [$index] ${screen.key}")
                return activityFor(screen)
            }

            if (screen == OnboardScreen.SET_DEFAULT && alreadyGranted(context)) {
                log("[$index] ${screen.key}: role already held → home")
                break
            }

            if (screen == OnboardScreen.SET_DEFAULT) noteAlreadyDefault(context)
            log("skipping [$index] ${screen.key}")
            index++
        }

        log("order finished → home")
        markOnboardingCompleted(context)
        return homeActivity()
    }

    private fun noteAlreadyDefault(context: Context) {
        if (!context.isDefaultLauncher()) return
        val prefs = context.getSharedPrefs()
        if (prefs.getBoolean(ALREADY_DEFAULT_LOGGED, false)) return
        prefs.edit().putBoolean(ALREADY_DEFAULT_LOGGED, true).apply()
        context.trackEvent("default_home_already")
    }

    private fun isApplicable(context: Context, screen: OnboardScreen): Boolean = when (screen) {

        OnboardScreen.WELCOME -> HomeModeToggle.isEnabled(context)

        OnboardScreen.REMINDERS -> AlarmSettings.get(context).isIntroScreen &&
            AlarmGrant.shouldOfferManage(context) && AlarmSettings.isCountryAllowed(context)

        OnboardScreen.SET_DEFAULT -> {

            val step = HomeAdsSettings.defaultHomeStep(context)
            HomeModeToggle.isEnabled(context) && step.enabled && !(step.skipIfDefault && context.isDefaultLauncher())
        }

        OnboardScreen.PREMIUM -> ProUpgradeActivity.offeredInWelcome(context)

        else -> true
    }

    // onboarding.order places the paywall itself, so the intro slides must not open it too
    fun placesPremium(context: Context): Boolean =
        OnboardScreen.PREMIUM in HomeAdsSettings.onboardingOrder(context)

    private fun alreadyGranted(context: Context): Boolean {
        val step = HomeAdsSettings.defaultHomeStep(context)
        return step.enabled && step.skipIfDefault && step.skipRestOnGrant &&
                context.isDefaultLauncher()
    }

    private fun activityFor(screen: OnboardScreen): Class<*> = when (screen) {
        OnboardScreen.WELCOME -> WelcomeStageActivity::class.java
        OnboardScreen.SET_DEFAULT -> HomeRoleStageActivity::class.java
        OnboardScreen.INTRO -> GuidedTourActivity::class.java
        OnboardScreen.LANGUAGE -> LocalePickActivity::class.java
        OnboardScreen.REMINDERS -> AlarmIntroActivity::class.java
        OnboardScreen.PREMIUM -> ProUpgradeActivity::class.java
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

fun Activity.excludeAppFromRecents() {
    try {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        manager.appTasks
            .filter { task ->
                val info = task.taskInfo
                val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else @Suppress("DEPRECATION") info.id
                id == taskId
            }
            .forEach { task -> task.setExcludeFromRecents(true) }
    } catch (e: Exception) {
        SafeLogger.error("Recents", "could not exclude task from recents", e)
    }
}
