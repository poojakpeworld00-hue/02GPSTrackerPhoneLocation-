package com.find.phone.gps.location.tracker.monetize.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.insights.ScreenInsights

const val TAG_EVENT = "AdEvents"

object EarningsTracker {

    private const val TAG = "EarningsTracker"

    fun trackPaidEvent(
        context: Context,
        adValue: AdValue?,
        format: String,
        unitId: String?,
        info: ResponseInfo?,
        placement: String = ScreenInsights.currentToken() ?: "unknown",
    ) {
        if (adValue == null) return
        val source = info?.loadedAdapterResponseInfo?.adSourceName?.takeIf { it.isNotBlank() } ?: "AdMob"
        val segment = if (AdPrefsStore.getInstance(context).getBoolean("OnMaketing")) "paid" else "organic"
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
            putString(FirebaseAnalytics.Param.AD_SOURCE, source)
            putString(FirebaseAnalytics.Param.AD_FORMAT, format)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, unitId.orEmpty())
            putDouble(FirebaseAnalytics.Param.VALUE, adValue.valueMicros / 1_000_000.0)
            putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode.ifBlank { "USD" })
            putInt("precision", adValue.precisionType)
            putString("placement", placement)
            putString("segment", segment)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "ad_impression → $params")
        } else {
            FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
        }
    }

}

private val RESERVED_EVENT_NAMES = setOf(
    "ad_activeview", "ad_click", "ad_exposure", "ad_query", "ad_reward", "adunit_exposure",
    "app_background", "app_clear_data", "app_exception", "app_remove", "app_store_refund",
    "app_store_subscription_cancel", "app_store_subscription_convert", "app_store_subscription_renew",
    "app_update", "app_upgrade", "dynamic_link_app_open", "dynamic_link_app_update",
    "dynamic_link_first_open", "error", "first_open", "first_visit", "in_app_purchase",
    "notification_dismiss", "notification_foreground", "notification_open", "notification_receive",
    "os_update", "session_start", "session_start_with_rollout", "user_engagement",
)

fun analyticsName(raw: String): String {
    var name = raw.replace(Regex("[^A-Za-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
    name = when {
        name.startsWith("google_", ignoreCase = true) -> "admob_" + name.substring(7)
        name.startsWith("firebase_", ignoreCase = true) -> "app_" + name.substring(9)
        name.startsWith("ga_", ignoreCase = true) -> "app_" + name.substring(3)
        else -> name
    }
    if (name.isEmpty() || !name[0].isLetter()) name = "e_$name"
    if (name.lowercase() in RESERVED_EVENT_NAMES) name += "_evt"
    return name.take(40).trimEnd('_')
}

fun permissionEventName(permission: String, outcome: String): String {
    var short = permission.substringAfterLast('.')
    val budget = 40 - "Permission__".length - outcome.length
    if (short.length > budget) short = short.removePrefix("ACCESS_").removePrefix("REQUEST_").removePrefix("MANAGE_")
    return "Permission_${short.take(budget)}_$outcome"
}

fun Context.trackEvent(rawKey: String) {
    val key = analyticsName(rawKey)
    val bundle = Bundle().apply { putString(key, key) }

    if (isDebuggable()) {
        Log.w(TAG_EVENT, "📌 KeyEvent (Debug): $key")
    } else {
        FirebaseAnalytics.getInstance(this)
            .logEvent(key, bundle)
    }
}

fun Context.trackEvent(rawKey: String, params: Map<String, Any?>) {
    val key = analyticsName(rawKey)
    val bundle = Bundle()
    params.forEach { (k, v) ->
        when (v) {
            null -> Unit
            is Int -> bundle.putInt(k, v)
            is Long -> bundle.putLong(k, v)
            is Double -> bundle.putDouble(k, v)
            is Boolean -> bundle.putString(k, v.toString())
            else -> bundle.putString(k, v.toString())
        }
    }
    if (isDebuggable()) {
        Log.w(TAG_EVENT, "📌 KeyEvent (Debug): $key $params")
    } else {
        FirebaseAnalytics.getInstance(this).logEvent(key, bundle)
    }
}

fun Context.trackPermissionResult(permission: String, granted: Boolean) {
    trackEvent(permissionEventName(permission, if (granted) "Allow" else "Deny"))
}

fun Context.trackPermissionShow(permission: String) {
    trackEvent(permissionEventName(permission, "Show"))
}

fun Context.trackPermissionResults(result: Map<String, Boolean>) {
    result.forEach { (permission, granted) -> trackPermissionResult(permission, granted) }
}

fun Context.isDebuggable(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
