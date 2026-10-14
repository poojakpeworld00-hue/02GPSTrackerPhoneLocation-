package com.find.phone.gps.location.tracker.setupflow

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.find.phone.gps.location.tracker.helpers.SafeLogger

object HomeRoleNudgeDialog {

    private const val SHOW_DELAY_MS = 600L

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun presentAfterSettings(context: Context) {
        cancelPending()
        val app = context.applicationContext
        val task = Runnable {
            pending = null
            runCatching {
                app.startActivity(HomeRoleTipActivity.intent(app))
            }.onFailure { SafeLogger.error("DefaultHomeHint", "hint could not be started", it) }
        }
        pending = task
        main.postDelayed(task, SHOW_DELAY_MS)
    }

    fun dismiss() {
        cancelPending()
        HomeRoleTipActivity.dismiss()
    }

    private fun cancelPending() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }
}
