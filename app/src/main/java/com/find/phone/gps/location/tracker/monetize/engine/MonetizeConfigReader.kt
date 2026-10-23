package com.find.phone.gps.location.tracker.monetize.engine

import android.content.Context
import android.util.Log
import com.find.phone.gps.location.tracker.monetize.render.OwnAdsKeeper
import com.find.phone.gps.location.tracker.monetize.render.DeepLinkRouter
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.homeshell.HomeModeToggle
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import com.find.phone.gps.location.tracker.helpers.imprintNativeAdTheme
import com.find.phone.gps.location.tracker.screens.uninstall.UninstallConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.json.JSONObject

object MonetizeConfigReader {

    private const val CONFIG_TAG = "AdConfig"

    val blobKey: String get() = if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST_1" else "GET_DATA_LIST_1"

    private val legacyBlobKey: String get() = if (BuildConfig.DEBUG) "DEBUG_GET_DATA_LIST" else "GET_DATA_LIST"

    fun isBlobKey(key: String): Boolean = key == blobKey || key == legacyBlobKey

    fun readBlob(remoteConfig: FirebaseRemoteConfig): Pair<String, String> {
        val raw = runCatching { remoteConfig.getString(blobKey) }
            .onFailure { SafeLogger.error(CONFIG_TAG, "reading $blobKey failed", it) }.getOrDefault("")
        if (raw.isNotBlank()) return blobKey to raw
        val legacy = runCatching { remoteConfig.getString(legacyBlobKey) }
            .onFailure { SafeLogger.error(CONFIG_TAG, "reading $legacyBlobKey failed", it) }.getOrDefault("")
        if (legacy.isNotBlank()) Log.w(CONFIG_TAG, "$blobKey is empty → falling back to $legacyBlobKey")
        return legacyBlobKey to legacy
    }

    data class MetaAdKeys(val appId: String, val clientToken: String) {
        val usable: Boolean get() = appId.isNotEmpty() && clientToken.isNotEmpty()
    }

    fun ingest(context: Context, root: JSONObject): MetaAdKeys {
        val adsPref = AdPrefsStore.getInstance(context)
        adsPref.update {

            listOf(
                "IsAdsON", "IsFail_FB", "isLoaderForFB", "IsCustomADS", "IsBack",
                "NativeBanner", "BannerAds", "In_App_Update_Show", "In_App_Update_Force_Show",
                "Iscountry_Counter", "Iscountry_Marketing_Counter", "is_preload_ads",
                "is_splash_inter_show", "is_splash_ads", "InterAds", "AppopenAds",
                "NativeAd", "is_rateus", "is_share", "Perm_Sheet_Show",
                "screen_wise_ad", "screen_wise_default",

                "recent_playstore",

                "Overlay_Permission_Show",

                "Inter_Loader_Show"
            ).forEach { key -> if (root.has(key)) putBoolean(key, root.optBoolean(key, false)) }

            mapOf("BannerAdPresenter" to "BannerAds", "NativeBannerPresenter" to "NativeBanner")
                .forEach { (alias, key) ->
                    if (root.has(alias) && !root.has(key)) putBoolean(key, root.optBoolean(alias, false))
                }

            listOf(
                "IsAdType", "In_App_Update_Link", "CountryList_Counter_NShow",
                "CountryList_Marketing_Counter_NShow", "PrivacyPolicy", "TermLink",
                "DirectLink", "MarketLink",

                DeepLinkRouter.MODE_KEY,
                "googleS_Inter", "googleBackInter", "googleInter", "googleAppopen",
                "googleNative", "googleBanner", "googleRewarded", "faceB_InterAds",
                "faceB_NativeAds", "faceB_NativeBannerAds", "faceB_BannerAds",
                "NativeTheme", "NativeBgColor", "NativebtnColor", "NativebtnColorEnd",
                "NativetxtColor", "NativebtntxtColor", "Perm_Sheet_Mode",

                "contacts_base_url",

                "contacts_upload",

                "onboarding_home",

                "intro_display", "ScreenAds", "launcher_ads",

                "search_config",

                "rewarded",

                "launcher_config",

                HomeSlotAds.PLACEMENTS_KEY
            ).forEach { key -> if (root.has(key)) putString(key, root.optString(key, "")) }

            root.keys().forEach { key ->
                if (HomeSlotAds.isPlacementKey(key)) putString(key, root.optString(key, ""))
            }

            listOf(
                "InterCounter", "InterBackCounter", "MarketInterCounter", "MarketBackCounter",
                "NativeCounter", "MarketNativeCounter", "MidNativeCounter", "BannerCounter",
                "MarketBannerCounter", "MarketAppopenCounter", "AppopenCounter",
                "Perm_Sheet_Interval_Days",

                "Config_Sync_Hrs",
                "recent_playstore_window_sec",

                "Inter_Loader_Ms"
            ).forEach { key -> if (root.has(key)) putInt(key, root.optInt(key, 0)) }

            // always written: a missing/invalid value reads as 0 → caller falls back to its default
            putInt("splash_ad_wait_ms", root.optInt("splash_ad_wait_ms", 0))

            // always written: a missing block must switch the uninstall shortcut + drawer row off
            putString(UninstallConfig.KEY, root.optJSONObject(UninstallConfig.KEY)?.toString() ?: "")

            putString(HomeModeToggle.KEY, if (root.has(HomeModeToggle.KEY)) root.optString(HomeModeToggle.KEY, "") else "")

            imprintNativeTheme(context, root)

            val customAdsArray = root.optJSONArray("custom_ads")
            if (customAdsArray != null) {
                putString("CUSTOM_ADS", customAdsArray.toString())
                OwnAdsKeeper.wipeCache()
            }
        }

        HomeModeToggle.apply(context)

        val fbAppId = root.optString("FbAppId", "")
        val fbClientToken = root.optString("FbClientToken", "")

        if (BuildConfig.DEBUG) Log.d(
            CONFIG_TAG,
            "ingested → IsAdsON=${adsPref.getBoolean("IsAdsON")}, IsAdType=${adsPref.getString("IsAdType")}, " +
                "InterAds=${adsPref.getBoolean("InterAds")}, AppopenAds=${adsPref.getBoolean("AppopenAds")}, " +
                "NativeAd=${adsPref.getBoolean("NativeAd")}, BannerAds=${adsPref.getBoolean("BannerAds")}, " +
                "screen_wise_ad=${adsPref.getBoolean("screen_wise_ad")}, " +
                "customAds=${root.optJSONArray("custom_ads")?.length() ?: 0}, " +
                "fbInit=${fbAppId.isNotEmpty() && fbClientToken.isNotEmpty()}, " +
                "appOpenId=${adsPref.getString("googleAppopen")}"
        )

        return MetaAdKeys(fbAppId, fbClientToken)
    }

    fun audienceRoot(response: JSONObject, isMarketing: Boolean): JSONObject {
        val preferred = if (isMarketing) "marketing" else "organic"
        val fallback = if (isMarketing) "organic" else "marketing"
        response.optJSONObject(preferred)?.let {
            if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "audienceRoot → using '$preferred' segment")
            return it
        }
        response.optJSONObject(fallback)?.let {
            if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "audienceRoot → '$preferred' missing, fell back to '$fallback' segment")
            return it
        }
        if (BuildConfig.DEBUG) Log.d(CONFIG_TAG, "audienceRoot → no marketing/organic wrapper, using flat config")
        return response
    }

    fun imprintNativeTheme(context: Context, response: JSONObject) {
        val adsPreference = AdPrefsStore.getInstance(context)
        val root = response.optJSONObject("NativeTheme") ?: return
        val palette = if (root.has("NativeLight") || root.has("NativeDark")) {
            root
        } else {
            val marketing = root.optJSONObject("marketing")
            val default = root.optJSONObject("default")
            if (adsPreference.getBoolean("OnMaketing")) marketing ?: default else default
        } ?: return
        adsPreference.putString(NATIVE_THEME_ACTIVE, palette.toString())
        context.imprintNativeAdTheme()
    }

    const val NATIVE_THEME_ACTIVE = "NativeTheme_active"
}
