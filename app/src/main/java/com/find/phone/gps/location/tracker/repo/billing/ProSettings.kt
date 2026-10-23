package com.find.phone.gps.location.tracker.repo.billing

import android.content.Context
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import org.json.JSONObject

data class ProTier(
    val productId: String,
    val kind: Kind,
    val tag: String,
    val preselected: Boolean,
) {
    enum class Kind { WEEKLY, MONTHLY, YEARLY }
}

data class ProSettings(
    val enabled: Boolean,
    val showAfterIntro: Boolean,
    val closeDelaySec: Int,
    val plans: List<ProTier>,

    val closeButtonOpacity: Float = 1f,
) {
    companion object {
        const val KEY = "premium"
        private const val MIN_CLOSE_OPACITY = 0.3

        private val DEFAULT_PLANS = listOf(
            ProTier("gps_premium_weekly", ProTier.Kind.WEEKLY, "SUGGESTED", preselected = true),
            ProTier("gps_premium_monthly", ProTier.Kind.MONTHLY, "", preselected = false),
            ProTier("gps_premium_yearly", ProTier.Kind.YEARLY, "", preselected = false),
        )

        fun read(context: Context): ProSettings {
            val raw = AdPrefsStore.getInstance(context).getString(KEY, null)
            val o = raw?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return ProSettings(true, true, 5, DEFAULT_PLANS)
            val plans = o.optJSONArray("products")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val p = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = p.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val kind = when {
                        p.optString("period").equals("P1Y", ignoreCase = true) -> ProTier.Kind.YEARLY
                        p.optString("period").equals("P1M", ignoreCase = true) -> ProTier.Kind.MONTHLY
                        else -> ProTier.Kind.WEEKLY
                    }
                    ProTier(id, kind, p.optString("tag"), p.optBoolean("default"))
                }
            }?.takeIf { it.isNotEmpty() } ?: DEFAULT_PLANS
            return ProSettings(
                enabled = o.optBoolean("enabled", true),
                showAfterIntro = o.optBoolean("show_after_intro", true),
                closeDelaySec = o.optInt("close_delay_sec", 5).coerceIn(0, 30),
                plans = plans,
                closeButtonOpacity = o.optDouble("close_button_opacity", 1.0)
                    .let { if (it.isNaN()) 1.0 else it }.coerceIn(MIN_CLOSE_OPACITY, 1.0).toFloat(),
            )
        }
    }
}
