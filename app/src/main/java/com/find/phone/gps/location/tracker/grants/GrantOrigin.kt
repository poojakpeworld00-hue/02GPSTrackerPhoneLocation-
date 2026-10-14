package com.find.phone.gps.location.tracker.grants

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import org.json.JSONObject
import com.find.phone.gps.location.tracker.monetize.engine.RemoteValuesRules

object GrantOrigin {

    private const val TAG = "GrantOrchestrator"
    private const val RC_KEY = "permission_engine"

    private const val DEFAULT_CONFIG = """
        {
          "permission_engine": {
            "notification": { "enabled": true, "activities": ["BootActivity", "WelcomeStageActivity", "MainHubActivity", "HomeStageActivity"], "delay": 0, "priority": 1 }
          }
        }
    """

    @Volatile
    private var cached: List<GrantPolicy>? = null

    fun rules(): List<GrantPolicy> = cached ?: reload()

    @Synchronized
    fun reload(): List<GrantPolicy> {
        val parsed = RemoteGrantDecoder.parse(rawConfig())
        cached = parsed
        return parsed
    }

    fun renewFromRemote(onReady: (() -> Unit)? = null) {
        try {
            val rc = FirebaseRemoteConfig.getInstance()

            RemoteValuesRules.withSettings(rc) {
                rc.fetchAndActivate().addOnCompleteListener { task ->
                    SafeLogger.log(TAG, "Remote Config fetch success=${task.isSuccessful}")
                    reload()
                    onReady?.invoke()
                }
            }
        } catch (e: Exception) {
            SafeLogger.error(TAG, "renewFromRemote failed; using cached config", e)
            reload()
            onReady?.invoke()
        }
    }

    private fun rawConfig(): String {
        return try {
            val rc = FirebaseRemoteConfig.getInstance()

            rc.getString(RC_KEY).takeIf { it.isNotBlank() }?.let { return it }

            val (_, blob) = com.find.phone.gps.location.tracker.monetize.engine.MonetizeConfigReader.readBlob(rc)
            if (blob.isNotBlank()) {
                val obj = JSONObject(blob)

                val root = audienceRoot(obj)
                if (root.has(RC_KEY)) return root.getJSONObject(RC_KEY).toString()
                if (obj.has(RC_KEY)) return obj.getJSONObject(RC_KEY).toString()
            }

            SafeLogger.log(TAG, "No remote permission_engine config; using compiled-in default")
            DEFAULT_CONFIG
        } catch (e: Exception) {
            SafeLogger.error(TAG, "Failed to read Remote Config; using compiled-in default", e)
            DEFAULT_CONFIG
        }
    }

    private fun audienceRoot(obj: JSONObject): JSONObject {
        val isMarketing = AdPrefsStore.getOrNull()?.getBoolean("OnMaketing") ?: false
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        obj.optJSONObject(preferred)?.let { return it }
        obj.optJSONObject(fallback)?.let { return it }
        return obj
    }
}
