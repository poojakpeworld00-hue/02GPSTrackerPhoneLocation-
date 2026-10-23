package com.find.phone.gps.location.tracker.alarms.gate

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.alarms.AskGatekeeper
import com.find.phone.gps.location.tracker.alarms.AlarmSettings

enum class AskSkipCause {

    DISABLED,

    ALREADY_GRANTED,

    FREQUENCY_SUPPRESSED,
}

object AlarmGrant {

    private const val ACTION_MANAGE = "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"

    private var armed = false

    private fun manageIntent(c: Context) = Intent(ACTION_MANAGE, Uri.parse("package:" + c.packageName))

    fun alertAccessAvailable(c: Context): Boolean =
        runCatching { manageIntent(c).resolveActivity(c.packageManager) != null }.getOrDefault(false)

    fun isGranted(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return nm.canUseFullScreenIntent()
        }
        return false
    }

    fun shouldOfferManage(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) !isGranted(c) else alertAccessAvailable(c)

    fun isAllowed(c: Context): Boolean = !shouldOfferManage(c)

    fun linkAlertIntent(builder: NotificationCompat.Builder, target: PendingIntent) {
        builder.setFullScreenIntent(target, true)
    }

    fun unfoldSettings(c: Context) {
        ResumeAdKeeper.skipNextAppOpenAd = true
        c.trackEvent("Reminder_Access_Settings")
        val flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            (if (c is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(manageIntent(c).addFlags(flags)) }
    }

    fun returnIntent(c: Context, host: Class<out Activity>): Intent =
        Intent(c, host).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun shouldShowHint(reason: AskSkipCause): Boolean = reason == AskSkipCause.FREQUENCY_SUPPRESSED

    fun prime(
        activity: Activity,
        returnTo: Class<out Activity>,
        reminderTitle: String? = null,
        reminderTime: String? = null,
        onReady: ((AskSkipCause) -> Unit)? = null,
        onSettingsOpened: (() -> Unit)? = null,
        onClosed: (() -> Unit)? = null,
        onManage: ((proceed: () -> Unit) -> Unit)? = null,
    ) {
        val cfg = AlarmSettings.get(activity)
        if (!cfg.isFeaturePrompt || !AlarmSettings.isCountryAllowed(activity)) {
            onReady?.invoke(AskSkipCause.DISABLED); return
        }
        if (!shouldOfferManage(activity)) { onReady?.invoke(AskSkipCause.ALREADY_GRANTED); return }
        if (!AskGatekeeper.shouldShow(activity)) { onReady?.invoke(AskSkipCause.FREQUENCY_SUPPRESSED); return }

        val title = cfg.featureTitle.ifBlank { activity.getString(R.string.rem_prompt_title) }
        val desc = describe(activity, cfg.featureDesc, reminderTitle, reminderTime)
        val button = cfg.featureAction.ifBlank { activity.getString(R.string.rem_prompt_action) }

        AskGatekeeper.markShown(activity)
        activity.trackEvent("Reminder_Prompt_Show")
        var allowed = false
        AlarmAskDialog.show(
            activity, title, desc, button,
            onDismiss = {
                // the dialog is also retired after Allow → Settings; only a plain close counts as dismissed
                if (!allowed) activity.trackEvent("reminder_prompt_dismiss")
                onClosed?.invoke()
            },
            onButton = {
                allowed = true
                activity.trackEvent("Reminder_Prompt_Allow")
                val open: () -> Unit = {
                    unfoldSettings(activity)
                    AlarmGrantObserver.start(activity, returnTo)
                    armed = true
                    onSettingsOpened?.invoke()
                }
                if (onManage != null) onManage(open) else open()
            },
        )
    }

    private fun describe(c: Context, remote: String, title: String?, time: String?): String {
        val generic = c.getString(R.string.rem_prompt_desc)
        if (remote.isBlank()) return generic
        val usesPlaceholders = "{title}" in remote || "{time}" in remote
        if (usesPlaceholders && (title.isNullOrBlank() || time.isNullOrBlank())) return generic
        return remote.replace("{title}", title.orEmpty()).replace("{time}", time.orEmpty())
    }

    fun onHostResume(activity: Activity) {
        if (!armed) return
        armed = false
        activity.trackEvent(if (isGranted(activity)) "reminder_fsi_granted" else "reminder_fsi_denied")
        AlarmGrantObserver.stop(activity)
        AlarmAskDialog.retireIfShowing()
    }
}
