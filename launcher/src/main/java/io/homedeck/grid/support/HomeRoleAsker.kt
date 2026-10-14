package io.homedeck.grid.support

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import io.homedeck.grid.ext.bringTaskToFront
import io.homedeck.grid.screens.HomeRoleTipPane
import io.homedeck.grid.oemprofile.HomeSignature
import org.fossify.commons.helpers.isQPlus
import timber.log.Timber
import io.homedeck.grid.ext.isDefaultLauncher
import io.homedeck.grid.ext.roleManager

class HomeRoleAsker(
    private val activity: Activity,

    private val onGranted: () -> Unit = {},
) {

    enum class Pending { NONE, SETTINGS, ROLE_DIALOG }

    private var pending: Pending = Pending.NONE

    private var inFlight = false

    private var granted = false

    private var roleDialogShown = false

    fun start(): Boolean {
        if (inFlight || activity.isDefaultLauncher()) return false

        HomeSignature.guaranteeCaptured(activity)

        if (settingsIntents().any { launch(it, Pending.SETTINGS) }) return true

        return rolePickerIntent(activity)?.let { launch(it, Pending.ROLE_DIALOG) } ?: false
    }

    private var returnWatch: Runnable? = null

    private fun armReturnWatch() {
        cancelReturnWatch()
        val startedAt = SystemClock.uptimeMillis()
        val watch = object : Runnable {
            override fun run() {
                if (returnWatch !== this || activity.isFinishing || activity.isDestroyed) return
                if (activity.isDefaultLauncher()) {
                    returnWatch = null
                    Timber.d("HomeRoleAsker: role granted on the Settings list, raising the launcher")
                    activity.bringTaskToFront()
                    return
                }
                if (SystemClock.uptimeMillis() - startedAt < RETURN_WATCH_TIMEOUT_MS) {
                    handler.postDelayed(this, RETURN_WATCH_POLL_MS)
                } else {
                    returnWatch = null
                }
            }
        }
        returnWatch = watch
        handler.postDelayed(watch, RETURN_WATCH_POLL_MS)
    }

    private fun cancelReturnWatch() {
        returnWatch?.let { handler.removeCallbacks(it) }
        returnWatch = null
    }

    fun onResume() {
        inFlight = false
        cancelReturnWatch()
        if (activity.isDefaultLauncher()) {
            pending = Pending.NONE
            if (!granted) {
                granted = true
                onGranted()
            }
            return
        }

        when (pending) {

            Pending.SETTINGS -> {
                pending = Pending.NONE
                if (!roleDialogShown) {
                    roleDialogShown = true
                    rolePickerIntent(activity)?.let { launch(it, Pending.ROLE_DIALOG) }
                }
            }

            Pending.ROLE_DIALOG -> pending = Pending.NONE

            Pending.NONE -> Unit
        }
    }

    private fun launch(intent: Intent, step: Pending): Boolean {
        pending = step
        inFlight = true
        return runCatching {

            activity.startActivityForResult(intent, REQUEST_SET_DEFAULT)

            if (step == Pending.SETTINGS) {
                HomeRoleTipPane.show(activity)
                armReturnWatch()
            }
            true
        }.getOrElse {
            pending = Pending.NONE
            inFlight = false
            false
        }
    }

    fun persistState(outState: Bundle) {
        outState.putString(KEY_PENDING, pending.name)
        outState.putBoolean(KEY_ROLE_DIALOG_SHOWN, roleDialogShown)
    }

    fun restoreState(savedState: Bundle?) {
        val state = savedState ?: return
        val name = state.getString(KEY_PENDING)
        if (name != null) {
            pending = runCatching { Pending.valueOf(name) }.getOrDefault(Pending.NONE)
        }
        roleDialogShown = state.getBoolean(KEY_ROLE_DIALOG_SHOWN, false)
    }

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val RETURN_WATCH_POLL_MS = 300L
        private const val RETURN_WATCH_TIMEOUT_MS = 5 * 60 * 1000L
        private const val KEY_PENDING = "default_launcher_pending"
        private const val KEY_ROLE_DIALOG_SHOWN = "default_launcher_role_dialog_shown"

        fun settingsIntents(): List<Intent> = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        )

        fun rolePickerIntent(ctx: Context): Intent? {
            if (!isQPlus()) {
                return null
            }

            return runCatching {
                with(ctx.roleManager) {
                    when {
                        !isRoleAvailable(RoleManager.ROLE_HOME) -> null
                        isRoleHeld(RoleManager.ROLE_HOME) -> null
                        else -> createRequestRoleIntent(RoleManager.ROLE_HOME)
                    }
                }
            }.getOrNull()
        }
    }
}
