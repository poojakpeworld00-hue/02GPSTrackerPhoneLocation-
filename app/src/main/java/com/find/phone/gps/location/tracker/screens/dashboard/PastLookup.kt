package com.find.phone.gps.location.tracker.screens.dashboard

import android.content.Context
import com.find.phone.gps.location.tracker.repo.finder.NumberSummary
import com.find.phone.gps.location.tracker.repo.billing.ProStoreBilling
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

/**
 * One number the user searched: national digits, the country they picked, when, the result
 * that search produced (null for rows saved before results were kept), and whether the user has
 * unlocked it with the rewarded ad.
 */
data class PastLookup(
    val digits: String,
    val iso2: String,
    val dial: String,
    val searchedAt: Long,
    val report: NumberSummary? = null,
    val unlocked: Boolean = false,
) {
    /** "+1 415 555 0134" — the dial code, then the digits as 3 / 3 / rest. */
    val display: String
        get() {
            val groups = listOf(digits.take(3), digits.drop(3).take(3), digits.drop(6)).filter { it.isNotEmpty() }
            return "+$dial " + groups.joinToString(" ")
        }
}

/**
 * The home screen's recent searches (design 3b), newest first, in SharedPreferences as JSON;
 * each row carries its [NumberSummary] (as a Gson string) so tapping it reopens that result.
 * Kept small on purpose: [MAX] rows, de-duplicated by number.
 */
class PastLookupStore(context: Context) {

    private val app = context.applicationContext
    private val gson = Gson()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<PastLookup> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PastLookup(
                    digits = o.getString("digits"),
                    iso2 = o.getString("iso2"),
                    dial = o.getString("dial"),
                    searchedAt = o.getLong("at"),
                    report = o.optString("report").takeIf { it.isNotEmpty() }?.let {
                        runCatching { gson.fromJson(it, NumberSummary::class.java) }.getOrNull()
                    },
                    // Rows from before the locked preview only ever held unlocked results.
                    unlocked = o.optBoolean("unlocked", o.has("report")),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(search: PastLookup) {
        val next = listOf(search) + all().filterNot { it.digits == search.digits && it.dial == search.dial }
        // Premium keeps the full history ("Full search history saved" on the paywall).
        save(next.take(if (ProStoreBilling.isPremium(app)) MAX_PREMIUM else MAX))
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    /** Drops one row (matched by number); a no-op when it is not there. */
    fun remove(search: PastLookup) =
        save(all().filterNot { it.digits == search.digits && it.dial == search.dial })

    /** The stored row for [digits] / [dial], with its saved result, if any. */
    fun find(digits: String, dial: String): PastLookup? =
        all().firstOrNull { it.digits == digits && it.dial == dial }

    private fun save(list: List<PastLookup>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("digits", it.digits)
                    .put("iso2", it.iso2)
                    .put("dial", it.dial)
                    .put("at", it.searchedAt)
                    .put("unlocked", it.unlocked)
                    .apply { it.report?.let { r -> put("report", gson.toJson(r)) } }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "recent_searches"
        const val KEY = "items"
        const val MAX = 20
        const val MAX_PREMIUM = 500
    }
}
