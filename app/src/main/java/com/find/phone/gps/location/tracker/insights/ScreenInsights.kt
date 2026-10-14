package com.find.phone.gps.location.tracker.insights

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import java.lang.ref.WeakReference

object ScreenInsights : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null
    private var lastLeft: WeakReference<Activity>? = null
    private var lastLeftAt = 0L
    private var lastSystemKeyAt = 0L
    private val observed = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    private const val LEAVE_GRACE_MS = 1_000L
    private const val DEDUPE_MS = 700L

    fun currentToken(): String? =
        (current?.get() ?: lastLeft?.get())?.let { ScreenTokens.of(it) }

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        runCatching {
            ContextCompat.registerReceiver(
                app, systemKeys,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private val systemKeys = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            val suffix = when (intent.getStringExtra("reason")) {
                "homekey" -> "home_press"
                "recentapps" -> "recents_press"
                else -> return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastSystemKeyAt < DEDUPE_MS) return
            lastSystemKeyAt = now
            val screen = current?.get()
                ?: lastLeft?.get()?.takeIf { now - lastLeftAt < LEAVE_GRACE_MS }
                ?: return
            screen.trackEvent("${ScreenTokens.of(screen)}_$suffix")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) activity.trackEvent("${ScreenTokens.of(activity)}_open")
        (activity as? FragmentActivity)?.supportFragmentManager
            ?.registerFragmentLifecycleCallbacks(fragmentOpens, true)
    }

    override fun onActivityStarted(activity: Activity) {

        val owner = activity as? ComponentActivity ?: return
        if (!observed.add(activity)) return
        owner.onBackPressedDispatcher.addCallback(owner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                owner.trackEvent("${ScreenTokens.of(owner)}_back_press")
                isEnabled = false
                try {
                    owner.onBackPressedDispatcher.onBackPressed()
                } finally {
                    isEnabled = true
                }
            }
        })
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
        lastLeft = WeakReference(activity)
        lastLeftAt = SystemClock.elapsedRealtime()
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        observed.remove(activity)
    }

    private val fragmentOpens = object : FragmentManager.FragmentLifecycleCallbacks() {
        private val logged = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Fragment, Boolean>())

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f.javaClass !in ScreenTokens.FRAGMENTS || f.isHidden) return
            if (!logged.add(f)) return
            f.context?.trackEvent("${ScreenTokens.of(f)}_open")
        }
    }
}
