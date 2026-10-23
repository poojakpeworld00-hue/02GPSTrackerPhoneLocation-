package com.find.phone.gps.location.tracker.insights

import android.app.Activity
import android.content.Context
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent

object AdPulse {

    const val GOOGLE = "google"
    const val FACEBOOK = "facebook"

    fun req(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "req", network)
    fun loaded(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "loaded", network)
    fun impression(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "impression", network)
    fun clicked(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "clicked", network)
    fun show(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "show", network)
    fun close(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "close", network)
    fun timeout(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "timeout", network)
    fun showFailed(context: Context?, type: String, errorCode: Int?, network: String = GOOGLE) = log(context, type, "show_failed", network, errorCode)

    fun loadFailed(context: Context?, type: String, errorCode: Int?, network: String = GOOGLE) {
        val noFill = (network == GOOGLE && errorCode == 3) || (network == FACEBOOK && errorCode == 1001)
        log(context, type, if (noFill) "no_fill" else "failed", network, errorCode)
    }

    private fun log(context: Context?, type: String, phase: String, network: String, errorCode: Int? = null) {
        val ctx = context ?: return
        val screen = (ctx as? Activity)?.let { ScreenTokens.of(it) } ?: ScreenInsights.currentToken() ?: "unknown"
        runCatching {
            ctx.trackEvent(
                "ad_${type}_$phase",
                mapOf("screen" to screen, "network" to network, "error_code" to errorCode),
            )
        }
    }
}
