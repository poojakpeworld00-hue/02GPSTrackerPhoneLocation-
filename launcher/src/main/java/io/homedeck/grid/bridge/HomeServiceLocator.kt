package io.homedeck.grid.bridge

import android.content.Context
import io.homedeck.grid.support.HelpIntentFilter
import io.homedeck.grid.setup.HomeOptions

object HomeServiceLocator {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var installedBridge: HomeHostPort = HomeHostPort.Default

    @Volatile
    private var installedAds: HomeAdsPort = HomeAdsPort.NoOp

    @Volatile
    var launchingPackage: String? = null
        internal set

    val bridge: HomeHostPort get() = installedBridge

    val ads: HomeAdsPort get() = installedAds

    @JvmStatic
    @JvmOverloads
    fun install(
        context: Context,
        bridge: HomeHostPort = HomeHostPort.Default,
        ads: HomeAdsPort = HomeAdsPort.NoOp,
    ) {
        appContext = context.applicationContext
        HelpIntentFilter.register(context)
        installedBridge = bridge
        installedAds = ads
    }

    fun ensure(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        HelpIntentFilter.register(context)
    }

    fun context(): Context = requireNotNull(appContext) {
        "HomeServiceLocator has no context: an entry point did not call ensure(context)"
    }

    fun setup(): HomeOptions = HomeOptions.parse(
        raw = installedBridge.configString(HomeConfigKeys.LAUNCHER_CONFIG, ""),
        organic = installedBridge.isOrganicAudience(),
    )
}

object HomeConfigKeys {

    const val LAUNCHER_CONFIG = "launcher_config"

    const val ADS_CONFIG = "ads_config"

    const val HOME_PROFILE_RULES = "home_profile_rules"

    const val HOME_PROFILE_PROBES = "home_profile_probes"
}
