package com.find.phone.gps.location.tracker.screens.reveal

import android.content.Context
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import org.json.JSONObject

enum class AskRhythm {
    ALWAYS, ONCE, EVERY_DAYS, APP_LAUNCHES, NEVER;

    companion object {
        fun from(raw: String?): AskRhythm = when (raw?.trim()?.lowercase()) {
            "always" -> ALWAYS
            "once" -> ONCE
            "every_days" -> EVERY_DAYS
            "app_launches" -> APP_LAUNCHES
            "never" -> NEVER
            else -> ONCE
        }
    }
}

data class TourUnveil(
    val enabled: Boolean,
    val frequency: AskRhythm,
    val interval: Int
)

object TourUnveilSettings {

    private const val RC_KEY = "intro_display"

    const val LANGUAGE = "language"
    const val TERMS = "terms"
    const val ONBOARDING = "onboarding"
    const val PERMISSION_SHEET = "permission_sheet"
    const val REMINDERS_INTRO = "reminders_intro"

    fun remindersIntro(context: Context): TourUnveil =
        load(context, REMINDERS_INTRO, defaultEnabled = true, defaultFrequency = AskRhythm.ONCE)

    fun language(context: Context): TourUnveil =
        load(context, LANGUAGE, defaultEnabled = true, defaultFrequency = AskRhythm.ONCE)

    fun terms(context: Context): TourUnveil =
        load(context, TERMS, defaultEnabled = false, defaultFrequency = AskRhythm.ONCE)

    fun onboarding(context: Context): TourUnveil =
        load(context, ONBOARDING, defaultEnabled = false, defaultFrequency = AskRhythm.ONCE)

    fun permissionSheet(context: Context): TourUnveil =
        load(context, PERMISSION_SHEET, defaultEnabled = true, defaultFrequency = AskRhythm.ALWAYS)

    private fun load(
        context: Context,
        key: String,
        defaultEnabled: Boolean,
        defaultFrequency: AskRhythm
    ): TourUnveil {
        val ads = AdPrefsStore.getInstance(context)
        val raw = ads.getString(RC_KEY)
        val obj = if (raw.isNullOrBlank()) null else runCatching {
            val root = JSONObject(raw)
            audienceContainer(root, isMarketing = ads.getBoolean("OnMaketing")).optJSONObject(key)
        }.getOrNull()

        if (obj == null) return TourUnveil(defaultEnabled, defaultFrequency, 0)

        return TourUnveil(
            enabled = obj.optBoolean("enabled", defaultEnabled),
            frequency = AskRhythm.from(
                obj.optString("prompt_frequency", defaultFrequency.name.lowercase())
            ),
            interval = obj.optInt("prompt_interval", 0)
        )
    }

    private fun audienceContainer(root: JSONObject, isMarketing: Boolean): JSONObject {
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        root.optJSONObject(preferred)?.let { return it }
        root.optJSONObject(fallback)?.let { return it }
        return root
    }
}
