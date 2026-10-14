package com.find.phone.gps.location.tracker.monetize.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics
import  com.find.phone.gps.location.tracker.BuildConfig

const val TAG_EVENT = "AdEvents"

object EarningsTracker {

    private const val TAG = "EarningsTracker"

    fun trackPaidEvent(context: Context, adValue: AdValue?) {
        if (adValue == null) {
            Log.e(TAG, "🔥 REAL PAID EVENT → $adValue ")
            return
        }
        val revenue = adValue.valueMicros / 1_000_000.0
        val currency = adValue.currencyCode ?: "USD"

        context.trackAdRevenue(revenue, currency)

        Log.e(TAG, "🔥 REAL PAID EVENT → $revenue $currency (${adValue.valueMicros})")
    }

    fun simulateDebugRevenue(context: Context) {
        if (!BuildConfig.DEBUG) return

        val revenue = 1.00
        val currency = "USD"

        context.trackAdRevenue(revenue, currency)

        Log.e(TAG, "🧪 DEBUG SIM → $revenue $currency")
    }

}

fun Context.trackEvent(key: String) {
    val bundle = Bundle().apply { putString(key, key) }

    if (isDebuggable()) {
        Log.w(TAG_EVENT, "📌 KeyEvent (Debug): $key")
    } else {
        FirebaseAnalytics.getInstance(this)
            .logEvent(key, bundle)
    }
}

fun Context.trackEvent(key: String, params: Map<String, Any?>) {
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
    val shortName = permission.substringAfterLast('.')
    trackEvent("Permission_${shortName}_${if (granted) "Allow" else "Deny"}")
}

fun Context.isDebuggable(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

fun Context.trackAdRevenue(revenue: Double, currency: String) {
    val params = Bundle().apply {
        putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
        putString(FirebaseAnalytics.Param.CURRENCY, currency)
        putDouble(FirebaseAnalytics.Param.VALUE, revenue)
    }

    if (BuildConfig.DEBUG) {
        Log.d("AdPaid", "🧪 DEBUG AD_IMPRESSION → $params")
    } else {
        FirebaseAnalytics.getInstance(this)
            .logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
    }

}
