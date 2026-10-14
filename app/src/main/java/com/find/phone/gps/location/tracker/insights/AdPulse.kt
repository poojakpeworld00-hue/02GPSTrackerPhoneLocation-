package com.find.phone.gps.location.tracker.insights

import android.app.Activity
import android.content.Context
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent

/**
 * Ad lifecycle analytics for the shared ad managers (one manager serves many screens), as a
 * bounded set: `ad_{type}_{phase}` with `screen`, `network` and, on failures, `error_code`.
 *
 * type:  banner · native · native_banner · interstitial · back_interstitial · app_open · rewarded
 * phase: req · loaded · no_fill · failed · impression · clicked · show · close
 *
 * Load failures split into `no_fill` (AdMob code 3 / Facebook 1001 — valid request, no demand)
 * and `failed` (anything else). Revenue stays on the reserved `ad_impression` event logged by
 * EarningsTracker; these are separate lifecycle events.
 */
object AdPulse {

    const val GOOGLE = "google"
    const val FACEBOOK = "facebook"

    fun req(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "req", network)
    fun loaded(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "loaded", network)
    fun impression(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "impression", network)
    fun clicked(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "clicked", network)
    fun show(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "show", network)
    fun close(context: Context?, type: String, network: String = GOOGLE) = log(context, type, "close", network)

    /** A load failure: `no_fill` for AdMob 3 / Facebook 1001, else `failed`. */
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
