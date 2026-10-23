package com.find.phone.gps.location.tracker.screens.uninstall

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.screens.boot.BootActivity
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity

/**
 * Launcher "Uninstall" shortcut + Settings row and the retention funnel behind them (rulebook R21).
 *
 * ```
 * simple  (organic always, paid type=simple):  Sorry → Reason →            Thanks → App info
 * advance (paid type=advance):      Language → Sorry → Reason → Progress → Thanks → app closes
 * ```
 * The app never uninstalls itself. Config: [UninstallConfig] (`uninstall_flow`, per audience).
 */
object UninstallFlow {

    private const val TAG = "UninstallFlow"
    private const val SHORTCUT_ID = "uninstall"

    const val EXTRA_SHORTCUT = "SHORTCUT"
    const val SHORTCUT_UNINSTALL = "uninstall"

    /** Forwarded by every funnel screen: true = advance route. */
    const val EXTRA_ADVANCED = "uninstall_advanced"
    const val EXTRA_REASON = "uninstall_reason"

    fun config(context: Context): UninstallConfig? {
        val prefs = AdPrefsStore.getInstance(context)
        return UninstallConfig.parse(prefs.getString(UninstallConfig.KEY, null), paid = prefs.getBoolean("OnMaketing"))
    }

    fun isEnabled(context: Context): Boolean = config(context) != null

    fun isUninstallLaunch(intent: Intent?): Boolean = intent?.getStringExtra(EXTRA_SHORTCUT) == SHORTCUT_UNINSTALL

    /** Adds or removes the dynamic "Uninstall" shortcut to match the stored config. Safe on every launch. */
    fun syncShortcut(context: Context) {
        val app = context.applicationContext
        runCatching {
            if (!isEnabled(app)) {
                ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(SHORTCUT_ID))
                return
            }
            val intent = Intent(app, BootActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_SHORTCUT, SHORTCUT_UNINSTALL)
            val label = app.getString(R.string.uninstall)
            ShortcutManagerCompat.pushDynamicShortcut(
                app,
                ShortcutInfoCompat.Builder(app, SHORTCUT_ID)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(app, R.drawable.ic_shortcut_uninstall))
                    .setIntent(intent)
                    .build(),
            )
        }.onFailure { SafeLogger.error(TAG, "syncShortcut failed", it) }
    }

    /** Language (from-uninstall mode) on the advance route, Sorry on the simple one. */
    fun firstScreen(context: Context): Intent =
        if (config(context)?.advanced == true) LocalePickActivity.newIntent(context, fromUninstall = true)
        else UninstallSorryActivity.newIntent(context, advanced = false)

    /** Back into the app, optionally on one of [MainHubActivity]'s targets. */
    fun keepApp(activity: Activity, target: String? = null) {
        activity.startActivity(Intent(activity, MainHubActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            target?.let { putExtra(MainHubActivity.EXTRA_OPEN_TARGET, it) }
        })
        activity.finish()
    }

    /** End of the simple route: the system App-info page, which holds the real Uninstall button. */
    fun openAppInfo(activity: Activity) {
        ResumeAdKeeper.skipNextAppOpenAd = true
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure { SafeLogger.error(TAG, "App info unavailable", it) }
        activity.finishAffinity()
    }

    /** End of the advance route: closes every task and takes the app out of Recents. */
    fun closeApp(activity: Activity) {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        runCatching { am?.appTasks?.forEach { it.finishAndRemoveTask() } }
            .onFailure { SafeLogger.error(TAG, "closeApp: appTasks failed", it) }
        activity.finishAndRemoveTask()
    }

    /** Funnel event with `route` (simple|advance) and `segment` (paid|organic). */
    fun track(context: Context, event: String, advanced: Boolean? = null) {
        val prefs = AdPrefsStore.getInstance(context)
        val adv = advanced ?: (config(context)?.advanced == true)
        context.trackEvent(
            event,
            mapOf(
                "route" to if (adv) "advance" else "simple",
                "segment" to if (prefs.getBoolean("OnMaketing")) "paid" else "organic",
            ),
        )
    }
}
