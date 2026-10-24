package com.find.phone.gps.location.tracker.monetize.render

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent

object RecentsAdObserver : Application.ActivityLifecycleCallbacks {

    private const val TAG = "RecentsAdObserver"
    private const val REASON_RECENT_APPS = "recentapps"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    private const val LEAVE_GRACE_MS = 1_000L

    private const val DEBOUNCE_MS = 3_000L

    private const val DONE_KEY = "recent_playstore_done"

    private var startedCount = 0
    private var lastStoppedAt = 0L
    private var lastOpenedAt = 0L

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        runCatching {
            ContextCompat.registerReceiver(
                app, receiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { log("receiver not registered: ${it.message}") }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            if (intent.getStringExtra("reason") != REASON_RECENT_APPS) return
            onRecentsPressed(context.applicationContext)
        }
    }

    private fun onRecentsPressed(context: Context) {
        val now = System.currentTimeMillis()
        val ours = startedCount > 0 || now - lastStoppedAt < LEAVE_GRACE_MS
        if (!ours) return

        val vault = AdPrefsStore.getInstance(context)
        if (!vault.getBoolean("recent_playstore")) return
        if (vault.getBoolean(DONE_KEY)) return
        val windowSec = vault.getInt("recent_playstore_window_sec")
        if (windowSec > 0) {
            val age = now - firstInstallTime(context)
            if (age > windowSec * 1000L) return log("recents after ${age / 1000}s — outside the ${windowSec}s window")
        }
        if (now - lastOpenedAt < DEBOUNCE_MS) return
        lastOpenedAt = now

        val playStore = context.packageManager.getLaunchIntentForPackage(PLAY_STORE_PACKAGE)
            ?: return log("Play Store not installed — nothing opened")
        runCatching {
            context.startActivity(playStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            vault.putBoolean(DONE_KEY, true)
            context.trackEvent("recent_playstore_open")
            log("recents pressed → Play Store home")
        }.onFailure { log("Play Store not opened: ${it.message}") }
    }

    private fun firstInstallTime(context: Context): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }
            .getOrDefault(0L)

    override fun onActivityStarted(activity: Activity) {
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        lastStoppedAt = System.currentTimeMillis()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
