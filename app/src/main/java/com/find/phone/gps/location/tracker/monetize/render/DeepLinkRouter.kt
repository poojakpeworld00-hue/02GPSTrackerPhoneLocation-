package com.find.phone.gps.location.tracker.monetize.render

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.BuildConfig

object DeepLinkRouter {

    private const val TAG = "DeepLinkRouter"

    const val MODE_KEY = "DirectLinkType"

    enum class Mode { WEBVIEW, CUSTOM_TAB, BROWSER }

    fun mode(context: Context): Mode = modeOf(AdPrefsStore.getInstance(context).getString(MODE_KEY))
        ?: Mode.WEBVIEW

    fun modeOf(name: String?): Mode? = when (name?.trim()?.lowercase()) {
        "custom_tab", "customtab", "chrome", "tab" -> Mode.CUSTOM_TAB
        "browser", "external", "external_browser" -> Mode.BROWSER
        "webview", "web_view", "in_app", "inapp" -> Mode.WEBVIEW
        else -> null
    }

    @JvmOverloads
    fun open(context: Context, url: String?, override: Mode? = null): Boolean {
        val u = url?.trim().orEmpty()
        if (u.isBlank()) return false

        if (isStoreLink(u)) {
            log("store link — straight to ACTION_VIEW: '$u'")
            return unfoldBrowser(context, u)
        }

        return when (override ?: mode(context)) {

            Mode.WEBVIEW -> SponsorBrowserActivity.open(context, u)
            Mode.CUSTOM_TAB -> unfoldCustomTab(context, u) || unfoldBrowser(context, u)
            Mode.BROWSER -> unfoldBrowser(context, u)
        }.also { if (!it) log("nothing opened for '$u'") }
    }

    fun isWebScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    fun isStoreLink(url: String): Boolean {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() == "market") return true
        val host = uri.host?.lowercase() ?: return false
        return host == "play.google.com" || host.endsWith(".play.google.com")
    }

    private fun unfoldCustomTab(context: Context, url: String): Boolean = runCatching {
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, url.toUri())
        true
    }.getOrDefault(false)

    private fun unfoldBrowser(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
