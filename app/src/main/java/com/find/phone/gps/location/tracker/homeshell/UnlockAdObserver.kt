package com.find.phone.gps.location.tracker.homeshell

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.monetize.engine.HomeSlotAds
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import io.homedeck.grid.screens.HomeDeskPane
import io.homedeck.grid.bridge.HomeServiceLocator
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UnlockAdObserver {

    private const val TAG = "UnlockAd"
    private const val PLACEMENT = "unlock"

    private const val PENDING_TTL_MS = 10_000L

    private const val SHOW_DELAY_MS = 400L

    private const val LAST_SHOWN_KEY = "__unlock_ad_last_shown"
    private const val DAY_KEY = "__unlock_ad_day"
    private const val DAY_COUNT_KEY = "__unlock_ad_day_count"

    private var registered = false
    private var resumed = WeakReference<Activity>(null)

    private var pendingAt = 0L

    fun register(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(Callbacks)

        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_USER_PRESENT) onUnlock(context.applicationContext)
                }
            },
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun onUnlock(context: Context) {
        if (settings(context) == null) return
        val home = resumed.get()
        if (home != null && isHome(home) && !home.isFinishing && !home.isDestroyed) {
            show(home)
        } else {
            pendingAt = SystemClock.uptimeMillis()
            Log.d(TAG, "unlocked — waiting for a home screen")
        }
    }

    private fun isHome(activity: Activity) = activity is HomeDeskPane || activity is MainHubActivity

    private fun show(activity: Activity) {
        pendingAt = 0L
        activity.window.decorView.postDelayed({
            if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) return@postDelayed
            if (FullscreenOnAction.isInterShow) return@postDelayed

            val cfg = settings(activity) ?: return@postDelayed
            record(activity)
            Log.d(TAG, "showing (${cfg.optString("format", "inter")})")
            if (cfg.optString("format", "inter").trim().lowercase() in setOf("full_native", "fullnative", "full")) {
                HomeSlotAds.showFullNative(activity, PLACEMENT) {}
            } else {
                HomeSlotAds.showInterstitial(activity, PLACEMENT) {}
            }
        }, SHOW_DELAY_MS)
    }

    private fun settings(context: Context): JSONObject? {
        return runCatching {
        val vault = AdPrefsStore.getInstance(context)
        if (!vault.getBoolean("IsAdsON")) return null
        if (!HomeSlotAds.placementEnabled(context, PLACEMENT)) return null
        if (!HomeRoleJourney.wasOnboardingCompleted(context)) return null

        val raw = HomeServiceLocator.setup().unlockAdsJson.takeIf { it.isNotBlank() } ?: return null
        val cfg = JSONObject(raw)
        country(context)?.let { cc -> cfg.optJSONObject("countries")?.optJSONObject(cc) }
            ?.let { override -> override.keys().forEach { cfg.put(it, override.get(it)) } }
        if (!cfg.optBoolean("enabled", false)) return null

        val now = System.currentTimeMillis()
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        if (now - installedAt < cfg.optLong("start_after_hours", 24L) * 3_600_000L) return null

        val gapMs = cfg.optLong("gap_minutes", 30L).coerceAtLeast(0L) * 60_000L
        if (now - vault.getLong(LAST_SHOWN_KEY, 0L) < gapMs) return null

        val max = cfg.optInt("max_per_day", 5)
        if (max <= 0 || shownToday(vault) >= max) return null
        cfg
        }.getOrElse { Log.w(TAG, "settings failed", it); null }
    }

    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun shownToday(vault: AdPrefsStore): Int =
        if (vault.getString(DAY_KEY) == today()) vault.getInt(DAY_COUNT_KEY, 0) else 0

    private fun record(context: Context) {
        val vault = AdPrefsStore.getInstance(context)
        val count = shownToday(vault) + 1
        vault.putString(DAY_KEY, today())
        vault.putInt(DAY_COUNT_KEY, count)
        vault.putLong(LAST_SHOWN_KEY, System.currentTimeMillis())
    }

    private fun country(context: Context): String? {
        val data = SecurePrefsKeeper(context)
        return listOf(
            data.homeCountryIso,
            data.geoCountryIso,
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simCountryIso,
        ).firstOrNull { !it.isNullOrBlank() && it.length == 2 }?.uppercase()
    }

    private object Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumed = WeakReference(activity)
            if (pendingAt == 0L || !isHome(activity)) return
            val fresh = SystemClock.uptimeMillis() - pendingAt <= PENDING_TTL_MS
            pendingAt = 0L
            if (fresh && settings(activity) != null) show(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumed.get() === activity) resumed.clear()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (resumed.get() === activity) resumed.clear()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
