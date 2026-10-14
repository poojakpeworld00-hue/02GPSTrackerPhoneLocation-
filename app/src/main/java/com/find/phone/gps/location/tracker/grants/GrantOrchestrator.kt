package com.find.phone.gps.location.tracker.grants

import android.app.Activity
import android.content.Context
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import java.lang.ref.WeakReference

object GrantOrchestrator {

    private const val TAG = "GrantOrchestrator"

    private val scheduler = GrantTimer()

    private var activeRef: WeakReference<Activity>? = null
    private var running = false

    private var pendingOnComplete: (() -> Unit)? = null

    fun init(context: Context) {
        try {
            GrantOrigin.renewFromRemote()
        } catch (e: Exception) {
            SafeLogger.error(TAG, "init failed", e)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun check(activity: Activity, onComplete: (() -> Unit)? = null) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                fireComplete(onComplete)
                return
            }
            val name = activity::class.java.simpleName

            val current = activeRef?.get()
            if (running && current === activity) return

            if (running && current !== activity) abort()

            pendingOnComplete = onComplete

            val allRules = GrantOrigin.rules()
            val matched = if (allRules.isEmpty()) emptyList()
            else PageMatcher.rulesFor(name, allRules)
            if (matched.isEmpty()) {
                SafeLogger.log(TAG, "No permission rules for $name")
                complete()
                return
            }

            val prefs = GrantLedger(activity)
            val pending = matched.filter { rule -> isStillNeeded(activity, rule, prefs) }
            if (pending.isEmpty()) {
                SafeLogger.log(TAG, "All configured permissions already satisfied on $name")
                complete()
                return
            }

            SafeLogger.log(TAG, "Queueing ${pending.size} permission(s) for $name")
            running = true
            activeRef = WeakReference(activity)
            processNext(GrantBacklog(pending))
        } catch (e: Exception) {
            SafeLogger.error(TAG, "check() failed", e)
            abort()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun request(activity: Activity, key: String, onComplete: (() -> Unit)? = null) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                fireComplete(onComplete); return
            }
            val spec = GrantToolbox.spec(key)
            if (spec == null) {
                SafeLogger.log(TAG, "request(): unknown permission key '$key' — skipped")
                fireComplete(onComplete); return
            }
            if (!GrantToolbox.isApplicableOnThisSdk(spec) || GrantToolbox.isGranted(activity, spec)) {

                activity.trackEvent("Permission_${spec.androidPermission.substringAfterLast('.')}_Bypass")
                fireComplete(onComplete); return
            }
            if (!GrantToolbox.isPrefGateOpen(activity, spec)) {
                fireComplete(onComplete); return
            }

            val rule = GrantOrigin.rules().firstOrNull { it.key == key }
            if (rule != null && !rule.enabled) {
                SafeLogger.log(TAG, "request(): '$key' disabled in config — skipped")
                fireComplete(onComplete); return
            }
            val prefs = GrantLedger(activity)
            if (rule?.showOnce == true && prefs.wasShown(key)) {
                fireComplete(onComplete); return
            }

            prefs.markAsked(key)
            if (rule?.showOnce == true) prefs.markShown(key)

            val shortName = spec.androidPermission.substringAfterLast('.')
            activity.trackEvent("Permission_${shortName}_Show")
            SafeLogger.log(TAG, "request(): asking '$key' on ${activity::class.java.simpleName}")

            GrantRequester.launch(activity, spec.androidPermission) { granted ->
                activity.trackPermissionResult(spec.androidPermission, granted)
                SafeLogger.log(TAG, "request(): '$key' granted=$granted")
                fireComplete(onComplete)
            }
        } catch (e: Exception) {
            SafeLogger.error(TAG, "request() failed", e)
            fireComplete(onComplete)
        }
    }

    private fun isStillNeeded(
        activity: Activity,
        rule: GrantPolicy,
        prefs: GrantLedger,
    ): Boolean {
        val spec = GrantToolbox.spec(rule.key)
        if (spec == null) {
            SafeLogger.log(TAG, "Unknown permission key '${rule.key}' — skipped")
            return false
        }
        if (!GrantToolbox.isApplicableOnThisSdk(spec)) return false
        if (!GrantToolbox.isPrefGateOpen(activity, spec)) {
            SafeLogger.log(TAG, "Pref gate '${spec.enabledPrefGate}' closed — '${rule.key}' skipped")
            return false
        }
        if (GrantToolbox.isGranted(activity, spec)) return false
        if (rule.showOnce && prefs.wasShown(rule.key)) return false
        return true
    }

    private fun processNext(queue: GrantBacklog) {
        val rule = queue.poll() ?: run { complete(); return }
        val spec = GrantToolbox.spec(rule.key) ?: run { processNext(queue); return }

        val activity = activeRef?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            abort(); return
        }

        if (GrantToolbox.isGranted(activity, spec)) {
            processNext(queue); return
        }

        scheduler.schedule(rule.delayMs) {
            val act = activeRef?.get()
            if (act == null || act.isFinishing || act.isDestroyed) {
                abort(); return@schedule
            }
            if (GrantToolbox.isGranted(act, spec)) {
                processNext(queue); return@schedule
            }

            val prefs = GrantLedger(act)
            prefs.markAsked(rule.key)
            if (rule.showOnce) prefs.markShown(rule.key)

            val shortName = spec.androidPermission.substringAfterLast('.')
            act.trackEvent("Permission_${shortName}_Show")

            SafeLogger.log(TAG, "Requesting '${rule.key}' on ${act::class.java.simpleName}")
            GrantRequester.launch(act, spec.androidPermission) { granted ->

                (activeRef?.get() ?: act).trackPermissionResult(spec.androidPermission, granted)
                SafeLogger.log(TAG, "Result '${rule.key}' granted=$granted")

                processNext(queue)
            }
        }
    }

    private fun complete() {
        scheduler.clear()
        running = false
        activeRef = null
        val cb = pendingOnComplete
        pendingOnComplete = null
        fireComplete(cb)
    }

    private fun abort() {
        scheduler.clear()
        running = false
        activeRef = null
        pendingOnComplete = null
    }

    private fun fireComplete(cb: (() -> Unit)?) {
        if (cb == null) return
        try {
            cb()
        } catch (e: Exception) {
            SafeLogger.error(TAG, "onComplete callback threw", e)
        }
    }
}
