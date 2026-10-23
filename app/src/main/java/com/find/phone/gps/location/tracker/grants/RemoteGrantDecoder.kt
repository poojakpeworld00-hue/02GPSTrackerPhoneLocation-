package com.find.phone.gps.location.tracker.grants

import com.find.phone.gps.location.tracker.helpers.SafeLogger
import org.json.JSONObject

object RemoteGrantDecoder {

    private const val TAG = "GrantOrchestrator"
    private const val ROOT_KEY = "permission_engine"

    fun parseSkipFlag(json: String?): Boolean {
        if (json.isNullOrBlank()) return true
        return try {
            val root = JSONObject(json)
            val engine = root.optJSONObject(ROOT_KEY) ?: root
            engine.optBoolean("show_skip", true)
        } catch (e: Exception) {
            true
        }
    }

    fun parse(json: String?): List<GrantPolicy> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(json)

            val engine = root.optJSONObject(ROOT_KEY) ?: root

            val rules = ArrayList<GrantPolicy>()
            val keys = engine.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = engine.optJSONObject(key) ?: continue

                val activitiesArr = obj.optJSONArray("activities")
                val activities = if (activitiesArr != null) {
                    (0 until activitiesArr.length())
                        .mapNotNull { activitiesArr.optString(it).takeIf(String::isNotBlank) }
                } else emptyList()

                rules += GrantPolicy(
                    key = key,
                    enabled = obj.optBoolean("enabled", false),
                    activities = activities,
                    delayMs = obj.optLong("delay", 0L),
                    priority = obj.optInt("priority", Int.MAX_VALUE),
                    showOnce = obj.optBoolean("show_once", obj.optBoolean("showOnce", false)),
                )
            }
            SafeLogger.log(TAG, "Parsed ${rules.size} permission rule(s) from Remote Config")
            rules
        } catch (e: Exception) {
            SafeLogger.error(TAG, "Failed to parse permission_engine config", e)
            emptyList()
        }
    }
}
