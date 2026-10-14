package com.find.phone.gps.location.tracker.alarms

import android.content.Context
import java.util.Locale
import java.util.concurrent.TimeUnit

object AskGatekeeper {

    private const val PREFS = "reminder_prompt_gate"
    private const val KEY_SHOWN_ONCE = "shown_once"
    private const val KEY_LAST_SHOWN_AT = "last_shown_at"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LAUNCH_AT_LAST_SHOWN = "launch_at_last_shown"
    private const val KEY_CREATED_COUNT = "created_count"
    private const val KEY_CREATED_AT_LAST_SHOWN = "created_at_last_shown"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun onAppLaunch(c: Context) {
        val p = prefs(c)
        p.edit().putInt(KEY_LAUNCH_COUNT, p.getInt(KEY_LAUNCH_COUNT, 0) + 1).apply()
    }

    fun onReminderCreated(c: Context) {
        val p = prefs(c)
        p.edit().putInt(KEY_CREATED_COUNT, p.getInt(KEY_CREATED_COUNT, 0) + 1).apply()
    }

    fun shouldShow(c: Context): Boolean {
        val cfg = AlarmSettings.get(c)
        val x = cfg.promptInterval.coerceAtLeast(1)
        val p = prefs(c)
        return when (cfg.promptFrequency.trim().lowercase(Locale.ROOT)) {
            "never" -> false
            "once" -> !p.getBoolean(KEY_SHOWN_ONCE, false)
            "every_days" -> {
                val last = p.getLong(KEY_LAST_SHOWN_AT, 0L)
                last == 0L || System.currentTimeMillis() - last >= TimeUnit.DAYS.toMillis(x.toLong())
            }
            "app_launches" -> p.getInt(KEY_LAUNCH_COUNT, 0) - p.getInt(KEY_LAUNCH_AT_LAST_SHOWN, 0) >= x
            "reminder_creations" -> p.getInt(KEY_CREATED_COUNT, 0) - p.getInt(KEY_CREATED_AT_LAST_SHOWN, 0) >= x
            else -> true
        }
    }

    fun markShown(c: Context) {
        val p = prefs(c)
        p.edit()
            .putBoolean(KEY_SHOWN_ONCE, true)
            .putLong(KEY_LAST_SHOWN_AT, System.currentTimeMillis())
            .putInt(KEY_LAUNCH_AT_LAST_SHOWN, p.getInt(KEY_LAUNCH_COUNT, 0))
            .putInt(KEY_CREATED_AT_LAST_SHOWN, p.getInt(KEY_CREATED_COUNT, 0))
            .apply()
    }
}
