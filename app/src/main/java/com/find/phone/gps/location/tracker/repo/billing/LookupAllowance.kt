package com.find.phone.gps.location.tracker.repo.billing

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LookupAllowance {

    private const val QUOTA_FILE = "search_quota"
    private const val FIELD_DATE = "day"
    private const val FIELD_SPENT = "used"
    private const val FIELD_EXTRA = "bonus"

    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(QUOTA_FILE, Context.MODE_PRIVATE)

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun counts(context: Context): Pair<Int, Int> {
        val sp = store(context)
        val isToday = sp.getString(FIELD_DATE, null) == stamp()
        return if (isToday) Pair(sp.getInt(FIELD_SPENT, 0), sp.getInt(FIELD_EXTRA, 0)) else Pair(0, 0)
    }

    private fun write(context: Context, spent: Int, extra: Int) {
        store(context).edit()
            .putString(FIELD_DATE, stamp())
            .putInt(FIELD_SPENT, spent)
            .putInt(FIELD_EXTRA, extra)
            .apply()
    }

    fun isUnlimited(context: Context): Boolean {
        if (ProStoreBilling.isPremium(context)) return true
        return ProSettings.read(context).freeSearchLimit <= 0
    }

    fun limit(context: Context): Int = ProSettings.read(context).freeSearchLimit

    fun used(context: Context): Int = counts(context).first

    fun remaining(context: Context): Int {
        val (spent, extra) = counts(context)
        return maxOf(limit(context) + extra - spent, 0)
    }

    fun canSearch(context: Context): Boolean = when {
        isUnlimited(context) -> true
        else -> remaining(context) > 0
    }

    fun consume(context: Context) {
        if (isUnlimited(context)) return
        val (spent, extra) = counts(context)
        write(context, spent + 1, extra)
    }

    fun grantRewarded(context: Context) {
        val (spent, extra) = counts(context)
        val reward = ProSettings.read(context).rewardedExtraSearches
        write(context, spent, extra + reward)
    }
}
