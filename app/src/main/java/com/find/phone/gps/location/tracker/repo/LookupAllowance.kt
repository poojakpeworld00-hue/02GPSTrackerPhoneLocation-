package com.find.phone.gps.location.tracker.repo

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LookupAllowance {

    private const val QUOTA_FILE = "search_quota"
    private const val FIELD_DATE = "day"
    private const val FIELD_SPENT = "used"
    private const val FIELD_COOLDOWN_END = "cooldown_end"

    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(QUOTA_FILE, Context.MODE_PRIVATE)

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun spent(context: Context): Int {
        val sp = store(context)
        // a running cooldown decides when the allowance refreshes, not the midnight rollover
        val cooling = sp.getLong(FIELD_COOLDOWN_END, 0L) > System.currentTimeMillis()
        return if (cooling || sp.getString(FIELD_DATE, null) == stamp()) sp.getInt(FIELD_SPENT, 0) else 0
    }

    private fun write(context: Context, spent: Int) {
        store(context).edit()
            .putString(FIELD_DATE, stamp())
            .putInt(FIELD_SPENT, spent)
            .apply()
    }

    fun isUnlimited(context: Context): Boolean = SearchConfig.read(context).freeSearchLimit <= 0

    fun limit(context: Context): Int = SearchConfig.read(context).freeSearchLimit

    fun used(context: Context): Int = spent(context)

    fun remaining(context: Context): Int {
        refreshCooldown(context)
        return maxOf(limit(context) - spent(context), 0)
    }

    fun canSearch(context: Context): Boolean = when {
        isUnlimited(context) -> true
        else -> remaining(context) > 0
    }

    fun consume(context: Context) {
        if (isUnlimited(context)) return
        write(context, spent(context) + 1)
        maybeStartCooldown(context)
    }

    fun cooldownRemainingMs(context: Context): Long {
        refreshCooldown(context)
        val end = store(context).getLong(FIELD_COOLDOWN_END, 0L)
        if (end == 0L) return 0L
        return maxOf(0L, end - System.currentTimeMillis())
    }

    fun onCooldown(context: Context): Boolean = cooldownRemainingMs(context) > 0L

    private fun maybeStartCooldown(context: Context) {
        val minutes = SearchConfig.read(context).cooldownMinutes
        if (minutes <= 0) return
        if (limit(context) - spent(context) > 0) return
        if (store(context).getLong(FIELD_COOLDOWN_END, 0L) > System.currentTimeMillis()) return
        store(context).edit()
            .putLong(FIELD_COOLDOWN_END, System.currentTimeMillis() + minutes * 60_000L)
            .apply()
    }

    private fun refreshCooldown(context: Context) {
        val end = store(context).getLong(FIELD_COOLDOWN_END, 0L)
        if (end != 0L && System.currentTimeMillis() >= end) {
            store(context).edit()
                .remove(FIELD_COOLDOWN_END)
                .putString(FIELD_DATE, stamp())
                .putInt(FIELD_SPENT, 0)
                .apply()
        }
    }

    fun formatRemaining(ms: Long): String {
        val total = (ms + 999L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
