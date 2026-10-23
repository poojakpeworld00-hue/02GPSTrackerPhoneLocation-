package com.find.phone.gps.location.tracker.repo

import android.content.Context
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import org.json.JSONObject

object SearchConfig {

    const val KEY = "search_config"

    data class Values(val freeSearchLimit: Int, val cooldownMinutes: Int)

    fun read(context: Context): Values {
        val raw = AdPrefsStore.getInstance(context).getString(KEY, null)
        val o = raw?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return Values(3, 0)
        return Values(
            freeSearchLimit = o.optInt("free_search_limit", 3),
            cooldownMinutes = o.optInt("cooldown_minutes", 0).coerceAtLeast(0),
        )
    }
}
