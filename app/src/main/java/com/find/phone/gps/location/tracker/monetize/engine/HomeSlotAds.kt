package com.find.phone.gps.location.tracker.monetize.engine

import android.app.Activity
import android.content.Context
import android.util.Log
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.SideMenuAdSequence
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.SideMenuAdPlan
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings.SideMenuAdFormat
import com.find.phone.gps.location.tracker.monetize.render.DeepLinkRouter
import com.find.phone.gps.location.tracker.monetize.render.SideMenuAdDriver
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

object HomeSlotAds {

    private const val TAG = "HomeSlotAds"

    private val PLACEMENTS = listOf(
        "leftPanel", "rightPanel", "drawer", "onboarding", "recent", "appExit", "unlock",
        "splash", "back", "appOpen", "install", "uninstall", "charge", "discharge",
    )

    private val GLOBAL_KEYS = setOf(
        "link_first_then", "link_first_show_all", "link_open_in", "inter_fallback", "RewardedAds",
        "googleFullNative", "ad_flow", "ad_flow_show_all",
    )

    private val NOT_PLACEMENT_KEYS = setOf("recent_playstore", "recent_playstore_window_sec", "onboarding_home")

    fun isPlacementKey(key: String): Boolean =
        key !in NOT_PLACEMENT_KEYS && (key in GLOBAL_KEYS || PLACEMENTS.any { key.startsWith("${it}_") })

    private var placementsRaw: String? = null
    private var placementsJson: JSONObject? = null

    private fun placementsBlock(vault: AdPrefsStore): JSONObject? {
        val raw = vault.getString(PLACEMENTS_KEY).orEmpty()
        synchronized(this) {
            if (raw != placementsRaw) {
                placementsRaw = raw
                placementsJson = raw.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            }
            return placementsJson
        }
    }

    private fun own(vault: AdPrefsStore, name: String, key: String): String? {
        val nested = placementsBlock(vault)?.optJSONObject(name)
            ?.takeIf { it.has(key) && !it.isNull(key) }?.get(key)?.toString()
        return nested?.takeIf { it.isNotBlank() }
            ?: vault.getString("${name}_$key")?.takeIf { it.isNotBlank() }
    }

    const val PLACEMENTS_KEY = "placements"

    private fun normalize(placement: String?): String? = when (placement) {
        "leftSwipe", "leftPanel" -> "leftPanel"
        "rightSwipe", "rightPanel" -> "rightPanel"
        "appLaunch", "drawerOpen", "drawer" -> "drawer"
        else -> placement
    }

    private fun names(placement: String?): List<String> {
        val name = normalize(placement)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return if (name.startsWith("onboarding_")) listOf(name, "onboarding") else listOf(name)
    }

    private fun resolve(vault: AdPrefsStore, placement: String?, key: String): String {
        names(placement).forEach { name -> own(vault, name, key)?.let { return it } }
        return vault.getString(key).orEmpty()
    }

    private fun settleFullNative(vault: AdPrefsStore, placement: String?): String =
        resolve(vault, placement, "googleFullNative").ifBlank { vault.getString("googleNative").orEmpty() }

    fun placementEnabled(context: Context, placement: String?): Boolean {
        val vault = AdPrefsStore.getInstance(context)
        val value = names(placement).firstNotNullOfOrNull { name -> own(vault, name, "ads_on")?.trim() }
        return value?.lowercase() != "false"
    }

    private fun directLinks(vault: AdPrefsStore, placement: String?): List<String> {
        val raw = resolve(vault, placement, "DirectLink").trim()
        if (raw.isEmpty()) return emptyList()
        if (!raw.startsWith("[")) return listOf(raw)
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private fun followUp(vault: AdPrefsStore, placement: String?, step: String): SideMenuAdPlan? =
        when (step.trim().lowercase()) {
            "reward", "rewarded" ->
                if (vault.getString("RewardedAds")?.trim()?.lowercase() == "false") null
                else SideMenuAdPlan(SideMenuAdFormat.REWARDED, resolve(vault, placement, "googleRewarded"))
            "inter", "interstitial" -> SideMenuAdPlan(SideMenuAdFormat.INTER, resolve(vault, placement, "googleInter"))
            "app_open", "appopen" -> SideMenuAdPlan(SideMenuAdFormat.APPOPEN, resolve(vault, placement, "googleAppopen"))
            "full_native", "fullscreen_native", "fullnative", "full" ->
                SideMenuAdPlan(SideMenuAdFormat.FULLSCREEN_NATIVE, settleFullNative(vault, placement))

            "custom", "custom_ads" ->
                if (vault.getBoolean("IsCustomADS")) SideMenuAdPlan(SideMenuAdFormat.CUSTOM, "custom") else null
            "directlink", "direct_link", "browser" -> directLinks(vault, placement).takeIf { it.isNotEmpty() }
                ?.let { SideMenuAdPlan(SideMenuAdFormat.DIRECTLINK, it.first(), it, resolve(vault, placement, "link_open_in")) }
            else -> null
        }?.takeIf { it.adUnitId.isNotBlank() }

    private class LinkFirst(val links: List<String>, val openIn: DeepLinkRouter.Mode?, val then: SideMenuAdSequence)

    private fun linkFirst(vault: AdPrefsStore, placement: String?): LinkFirst? {
        val order = resolve(vault, placement, "link_first_then")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val links = directLinks(vault, placement)
        if (order.isEmpty() || links.isEmpty() || !vault.getBoolean("IsCustomADS")) return null
        val openIn = DeepLinkRouter.modeOf(resolve(vault, placement, "link_open_in"))
        val showAll = resolve(vault, placement, "link_first_show_all").trim().toBoolean()
        val then = SideMenuAdSequence(
            enabled = true,
            counter = 0,
            sequence = order.mapNotNull { followUp(vault, placement, it) },
            showAll = showAll,
            startFromFirst = true,
        )
        return LinkFirst(links, openIn, then)
    }

    private fun fallbacks(vault: AdPrefsStore, placement: String?): List<String> =
        resolve(vault, placement, "inter_fallback").split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun adFlow(vault: AdPrefsStore, placement: String?): SideMenuAdSequence? {
        val steps = resolve(vault, placement, "ad_flow").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.isEmpty()) return null
        val sequence = steps.mapNotNull { followUp(vault, placement, it) }
        val showAll = resolve(vault, placement, "ad_flow_show_all").trim().toBoolean()
        return SideMenuAdSequence(true, 0, sequence, showAll = showAll, startFromFirst = true, onDemand = true)
    }

    fun hasOwnFlow(context: Context, placement: String): Boolean {
        val vault = AdPrefsStore.getInstance(context)
        fun set(key: String) = names(placement).any { own(vault, it, key) != null }
        return set("ad_flow") || (set("link_first_then") && linkFirst(vault, placement) != null)
    }

    private fun interFlow(vault: AdPrefsStore, placement: String?): SideMenuAdSequence {
        adFlow(vault, placement)?.let { return it }
        val sequence = buildList {
            resolve(vault, placement, "googleInter").takeIf { it.isNotBlank() }
                ?.let { add(SideMenuAdPlan(SideMenuAdFormat.INTER, it)) }
            fallbacks(vault, placement).forEach { name -> followUp(vault, placement, name)?.let { add(it) } }
        }
        return SideMenuAdSequence(true, 0, sequence, showAll = false, startFromFirst = true, onDemand = true)
    }

    private fun fullNativeFlow(vault: AdPrefsStore, placement: String?): SideMenuAdSequence {
        adFlow(vault, placement)?.let { return it }
        val sequence = buildList {
            settleFullNative(vault, placement).takeIf { it.isNotBlank() }
                ?.let { add(SideMenuAdPlan(SideMenuAdFormat.FULLSCREEN_NATIVE, it)) }
            resolve(vault, placement, "googleInter").takeIf { it.isNotBlank() }
                ?.let { add(SideMenuAdPlan(SideMenuAdFormat.INTER, it)) }
        }
        return SideMenuAdSequence(true, 0, sequence, showAll = false, startFromFirst = true, onDemand = true)
    }

    fun hasOwnInter(context: Context, placement: String?): Boolean {
        val vault = AdPrefsStore.getInstance(context)
        if (linkFirst(vault, placement) != null) return true
        if (adFlow(vault, placement) != null) return true
        val unit = resolve(vault, placement, "googleInter")
        return (unit.isNotBlank() && unit != vault.getString("googleInter").orEmpty()) ||
            fallbacks(vault, placement).any { followUp(vault, placement, it) != null }
    }

    fun showInterstitial(activity: Activity, placement: String?, proceed: () -> Unit) {
        val vault = AdPrefsStore.getInstance(activity)
        if (!vault.getBoolean("IsAdsON") || !placementEnabled(activity, placement)) return proceed()

        if (runLinkFirst(activity, vault, placement, proceed)) return

        val flow = interFlow(vault, placement)
        log("$placement: inter chain ${flow.sequence.map { "${it.type.key}(${it.adUnitId})" }}")
        SideMenuAdDriver.run(activity, flow, pointerKey(placement), proceed)
    }

    fun showFullNative(activity: Activity, placement: String?, proceed: () -> Unit) {
        val vault = AdPrefsStore.getInstance(activity)
        if (!vault.getBoolean("IsAdsON") || !placementEnabled(activity, placement)) return proceed()

        if (runLinkFirst(activity, vault, placement, proceed)) return
        val flow = fullNativeFlow(vault, placement)
        log("$placement: full-native chain ${flow.sequence.map { "${it.type.key}(${it.adUnitId})" }}")
        SideMenuAdDriver.run(activity, flow, pointerKey(placement), proceed)
    }

    private fun runLinkFirst(activity: Activity, vault: AdPrefsStore, placement: String?, proceed: () -> Unit): Boolean {
        val chain = linkFirst(vault, placement) ?: return false

        SideMenuAdDriver.preload(activity, chain.then)
        log("$placement: link-first → ${chain.links.size} link(s), then ${chain.then.sequence.map { it.type.key }}")
        val afterLinks = {

            ResumeAdKeeper.skipNextAppOpenAd = false
            if (chain.then.sequence.isEmpty()) proceed()
            else SideMenuAdDriver.run(activity, chain.then, pointerKey(placement), proceed)
        }

        ResumeAdKeeper.skipNextAppOpenAd = true
        val opened = chain.links.asReversed().count { DeepLinkRouter.open(activity, it, chain.openIn) }
        if (opened > 0) SideMenuAdDriver.onReturnTo(activity, afterLinks) else afterLinks()
        return true
    }

    fun presentStep(activity: Activity, placement: String?, step: String, proceed: () -> Unit) {
        val vault = AdPrefsStore.getInstance(activity)
        val spec = followUp(vault, placement, step) ?: return proceed()
        SideMenuAdDriver.run(activity, SideMenuAdSequence(true, 0, listOf(spec), startFromFirst = true, onDemand = true), pointerKey(placement), proceed)
    }

    fun hasFallback(context: Context, placement: String?): Boolean {
        val vault = AdPrefsStore.getInstance(context)
        return fallbacks(vault, placement).any { followUp(vault, placement, it) != null }
    }

    fun runFallback(activity: Activity, placement: String?, proceed: () -> Unit) {
        val vault = AdPrefsStore.getInstance(activity)
        val sequence = fallbacks(vault, placement).mapNotNull { followUp(vault, placement, it) }
        if (sequence.isEmpty()) return proceed()
        log("$placement: inter_fallback ${sequence.map { it.type.key }}")
        SideMenuAdDriver.run(activity, SideMenuAdSequence(true, 0, sequence, startFromFirst = true), pointerKey(placement), proceed)
    }

    fun presentAdFlow(activity: Activity, placement: String, proceed: () -> Unit): Boolean {
        val vault = AdPrefsStore.getInstance(activity)
        val flow = adFlow(vault, placement) ?: return false
        log("$placement: ad_flow ${flow.sequence.map { it.type.key }}${if (flow.showAll) " [show_all]" else ""}")
        SideMenuAdDriver.run(activity, flow, pointerKey(placement), proceed)
        return true
    }

    fun presentLinkFirst(activity: Activity, placement: String, proceed: () -> Unit): Boolean {
        val vault = AdPrefsStore.getInstance(activity)
        if (!vault.getBoolean("IsAdsON") || !placementEnabled(activity, placement)) return false
        return runLinkFirst(activity, vault, placement, proceed)
    }

    private fun pointerKey(placement: String?) = "__launcher_placement_ptr_${normalize(placement)}"

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
