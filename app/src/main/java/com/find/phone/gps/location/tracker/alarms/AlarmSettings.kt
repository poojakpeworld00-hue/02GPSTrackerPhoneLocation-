package com.find.phone.gps.location.tracker.alarms

import android.content.Context
import android.telephony.TelephonyManager
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.Locale

data class AlarmSeedPlan(
    @SerializedName("title") val title: String = "",
    @SerializedName("note") val note: String = "",
    @SerializedName("daysAhead") val daysAhead: Int = 1,
)

data class AlarmPreferences(
    @SerializedName("feature_title") val featureTitle: String = "",

    @SerializedName("feature_desc") val featureDesc: String = "",
    @SerializedName("feature_action") val featureAction: String = "",

    @SerializedName("action_delay_options") val actionDelayOptions: String = "5,10,30,60",

    @SerializedName("is_default_config") val isDefaultConfig: Boolean = false,
    @SerializedName("default_config_list") val defaultConfigList: List<AlarmSeedPlan> = emptyList(),

    @SerializedName("is_feature_prompt") val isFeaturePrompt: Boolean = false,

    @SerializedName("is_enhanced_mode") val isEnhancedMode: Boolean = true,

    @SerializedName("is_intro_screen") val isIntroScreen: Boolean = false,

    @SerializedName("prompt_frequency") val promptFrequency: String = "always",
    @SerializedName("prompt_interval") val promptInterval: Int = 0,

    @SerializedName("is_country_filter") val isCountryFilter: Boolean = false,

    @SerializedName("excluded_countries") val excludedCountries: String = "",

    @SerializedName("is_settings_row") val isSettingsRow: Boolean = false,
    @SerializedName("settings_row_excluded_countries") val settingsRowExcludedCountries: String = "",

    @SerializedName("intro_title") val introTitle: String = "",
    @SerializedName("intro_desc") val introDesc: String = "",
)

private data class AlarmSettingsDocument(
    @SerializedName("marketing") val marketing: AlarmPreferences? = null,
    @SerializedName("organic") val organic: AlarmPreferences? = null,
)

object AlarmSettings {

    const val RC_KEY = "feature_config"
    const val MARKETING = "marketing"
    const val ORGANIC = "organic"

    @Volatile private var cachedRaw: String? = null
    @Volatile private var cachedAudience: String? = null
    @Volatile private var cachedBranch: AlarmPreferences = AlarmPreferences()

    @Synchronized
    fun get(context: Context): AlarmPreferences {
        val raw = runCatching { FirebaseRemoteConfig.getInstance().getString(RC_KEY) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_CONFIG
        val audience = currentAudience(context)
        if (raw == cachedRaw && audience == cachedAudience) return cachedBranch

        val doc = runCatching { Gson().fromJson(raw, AlarmSettingsDocument::class.java) }.getOrNull()
            ?: runCatching { Gson().fromJson(DEFAULT_CONFIG, AlarmSettingsDocument::class.java) }.getOrNull()
        val branch = (if (audience == MARKETING) doc?.marketing else doc?.organic) ?: AlarmPreferences()

        cachedRaw = raw
        cachedAudience = audience
        cachedBranch = branch
        return branch
    }

    fun currentAudience(context: Context): String =
        if (runCatching { AdPrefsStore.getInstance(context).getBoolean("OnMaketing") }.getOrDefault(false)) MARKETING else ORGANIC

    fun isCountryAllowed(context: Context): Boolean {
        val cfg = get(context)
        if (!cfg.isCountryFilter) return true
        return !isListed(country(context), cfg.excludedCountries)
    }

    fun isSettingsRowAllowed(context: Context): Boolean {
        val cfg = get(context)
        if (!cfg.isSettingsRow) return false
        if (!isCountryAllowed(context)) return false
        return !isListed(country(context), cfg.settingsRowExcludedCountries)
    }

    private fun isListed(iso: String?, csv: String): Boolean {
        if (iso.isNullOrBlank() || csv.isBlank()) return false
        return csv.split(',').any { it.trim().equals(iso, ignoreCase = true) }
    }

    private fun country(context: Context): String? {
        val vault = SecurePrefsKeeper(context)
        return vault.geoCountryIso.ifBlank { vault.homeCountryIso }.ifBlank {
            runCatching {
                (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                    ?.networkCountryIso.orEmpty()
            }.getOrDefault("")
        }.uppercase(Locale.ROOT).ifBlank { null }
    }

    private const val DEFAULT_CONFIG = """
{
  "marketing": {
    "feature_title": "Get your reminders on time",
    "feature_desc": "Allow reminders so \"{title}\" reaches you at {time}, even when your phone is asleep.",
    "feature_action": "Turn on reminders",
    "action_delay_options": "5,10,30,60",
    "is_default_config": false,
    "default_config_list": [],
    "is_feature_prompt": true,
    "is_enhanced_mode": true,
    "is_intro_screen": true,
    "intro_title": "Never forget a call back",
    "intro_desc": "Set a reminder for any number and we'll remind you right on time, even when your phone is asleep.",
    "is_country_filter": false,
    "excluded_countries": "",
    "is_settings_row": true,
    "settings_row_excluded_countries": "",
    "prompt_frequency": "always",
    "prompt_interval": 3
  },
  "organic": {
    "feature_title": "Get your reminders on time",
    "feature_desc": "Allow reminders so \"{title}\" reaches you at {time}, even when your phone is asleep.",
    "feature_action": "Turn on reminders",
    "action_delay_options": "5,10,30,60",
    "is_default_config": false,
    "default_config_list": [],
    "is_feature_prompt": true,
    "is_enhanced_mode": true,
    "is_intro_screen": false,
    "intro_title": "Never forget a call back",
    "intro_desc": "Set a reminder for any number and we'll remind you right on time, even when your phone is asleep.",
    "is_country_filter": false,
    "excluded_countries": "",
    "is_settings_row": true,
    "settings_row_excluded_countries": "",
    "prompt_frequency": "always",
    "prompt_interval": 3
  }
}
"""
}
