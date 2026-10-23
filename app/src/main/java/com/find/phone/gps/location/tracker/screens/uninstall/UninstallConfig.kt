package com.find.phone.gps.location.tracker.screens.uninstall

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The `uninstall_flow` block of the audience root (stored raw by MonetizeConfigReader).
 * Gson, not org.json, so the parsing runs in plain JVM unit tests.
 */
data class UninstallConfig(
    val advanced: Boolean,
    val thanksDelayMs: Long,
    /** null = no `splash_ad` block: the uninstall launch keeps the normal splash switches. */
    val splashAd: SplashAd?,
    /** `<page>_ad` blocks; a page without one shows no funnel ad. */
    val pageAds: Map<String, PageAd>,
    /** null = no `inter_ad` block: no funnel interstitial. */
    val inter: InterAd?,
) {
    /** [type] is [SPLASH_INTER], [SPLASH_APP_OPEN] or null (normal is_splash_inter_show choice). */
    data class SplashAd(val show: Boolean, val type: String?)

    /** [type] is one of [PAGE_TYPES]. */
    data class PageAd(val show: Boolean, val type: String)

    data class InterAd(val show: Boolean, val pages: Set<String>)

    companion object {
        const val KEY = "uninstall_flow"

        const val PAGE_LANGUAGE = "language"
        const val PAGE_SORRY = "sorry"
        const val PAGE_REASON = "reason"
        const val PAGE_PROGRESS = "progress"
        const val PAGE_THANKS = "thanks"
        val ALL_PAGES = setOf(PAGE_LANGUAGE, PAGE_SORRY, PAGE_REASON, PAGE_PROGRESS, PAGE_THANKS)

        const val SPLASH_INTER = "inter"
        const val SPLASH_APP_OPEN = "appopen"
        val PAGE_TYPES = setOf("big", "mid", "small", "banner")

        /**
         * Null when the block is missing, unreadable or `enabled` is not true: the feature is off.
         * Organic users always get the simple route; paid users follow `type` (unknown → simple).
         */
        fun parse(raw: String?, paid: Boolean): UninstallConfig? {
            val o = raw?.takeIf { it.isNotBlank() }
                ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
                ?: return null
            if (!o.bool("enabled", false)) return null

            val splash = o.obj("splash_ad")?.let {
                SplashAd(it.bool("show", true), it.str("type")?.takeIf { t -> t == SPLASH_INTER || t == SPLASH_APP_OPEN })
            }
            val pages = ALL_PAGES.mapNotNull { page ->
                o.obj("${page}_ad")?.let { page to PageAd(it.bool("show", true), it.str("type")?.takeIf { t -> t in PAGE_TYPES } ?: "mid") }
            }.toMap()
            val inter = o.obj("inter_ad")?.let { block ->
                val on = block.get("on")?.takeIf { it.isJsonArray }?.asJsonArray
                val listed = on?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase()?.ifBlank { null } }?.toSet()
                InterAd(block.bool("show", true), listed ?: ALL_PAGES)
            }
            return UninstallConfig(
                advanced = paid && o.str("type") == "advance",
                thanksDelayMs = (o.int("thanks_delay") ?: 3).coerceIn(1, 10) * 1000L,
                splashAd = splash,
                pageAds = pages,
                inter = inter,
            )
        }

        private fun JsonObject.prim(key: String): JsonElement? = get(key)?.takeIf { it.isJsonPrimitive }
        private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.str(key: String): String? = prim(key)?.asString?.trim()?.lowercase()
        private fun JsonObject.int(key: String): Int? = prim(key)?.let { runCatching { it.asInt }.getOrNull() }
        private fun JsonObject.bool(key: String, default: Boolean): Boolean =
            prim(key)?.let { it.asJsonPrimitive.let { p -> if (p.isBoolean) p.asBoolean else p.asString.equals("true", true) } } ?: default
    }
}
