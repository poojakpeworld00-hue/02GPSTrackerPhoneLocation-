package com.find.phone.gps.location.tracker.alarms

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import java.util.concurrent.TimeUnit

object AskGatekeeper {

    private const val STORE = "reminder_prompt_gate"
    private const val SEEN_FLAG = "shown_once"
    private const val SEEN_AT = "last_shown_at"
    private const val OPEN_TOTAL = "launch_count"
    private const val OPEN_MARK = "launch_at_last_shown"
    private const val MADE_TOTAL = "created_count"
    private const val MADE_MARK = "created_at_last_shown"

    private fun store(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun increment(ctx: Context, counter: String) {
        val sp = store(ctx)
        sp.edit().putInt(counter, sp.getInt(counter, 0) + 1).apply()
    }

    private fun gap(sp: SharedPreferences, total: String, mark: String): Int =
        sp.getInt(total, 0) - sp.getInt(mark, 0)

    private fun dueByDays(sp: SharedPreferences, days: Int): Boolean {
        val stamp = sp.getLong(SEEN_AT, 0L)
        if (stamp == 0L) return true
        return System.currentTimeMillis() - stamp >= TimeUnit.DAYS.toMillis(days.toLong())
    }

    fun onAppLaunch(c: Context) = increment(c, OPEN_TOTAL)

    fun onReminderCreated(c: Context) = increment(c, MADE_TOTAL)

    fun shouldShow(c: Context): Boolean {
        val settings = AlarmSettings.get(c)
        val step = settings.promptInterval.coerceAtLeast(1)
        val sp = store(c)
        val mode = settings.promptFrequency.trim().lowercase(Locale.ROOT)
        return when (mode) {
            "never" -> false
            "once" -> !sp.getBoolean(SEEN_FLAG, false)
            "every_days" -> dueByDays(sp, step)
            "app_launches" -> gap(sp, OPEN_TOTAL, OPEN_MARK) >= step
            "reminder_creations" -> gap(sp, MADE_TOTAL, MADE_MARK) >= step
            else -> true
        }
    }

    fun markShown(c: Context) {
        val sp = store(c)
        val opens = sp.getInt(OPEN_TOTAL, 0)
        val made = sp.getInt(MADE_TOTAL, 0)
        sp.edit()
            .putBoolean(SEEN_FLAG, true)
            .putLong(SEEN_AT, System.currentTimeMillis())
            .putInt(OPEN_MARK, opens)
            .putInt(MADE_MARK, made)
            .apply()
    }
}
