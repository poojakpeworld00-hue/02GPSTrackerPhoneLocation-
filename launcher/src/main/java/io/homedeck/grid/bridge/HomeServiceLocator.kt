package io.homedeck.grid.bridge

import android.content.Context
import io.homedeck.grid.support.HelpIntentFilter
import io.homedeck.grid.setup.HomeOptions

/**
 * The single hand-off point between a host app and the launcher.
 *
 * A host calls [install] once, from its `Application.onCreate`:
 *
 * ```kotlin
 * HomeServiceLocator.install(this)                       // plain launcher, no ads, no panel
 *
 * HomeServiceLocator.install(                            // or wire what this app has
 *     context = this,
 *     bridge  = AppLauncherBridge(this),
 *     ads     = AppLauncherAds(),
 * )
 * ```
 *
 * ### Why install is optional
 *
 * The launcher's Activities can be started by the system — the user pressed Home — before any host
 * code has run in that process, and on some OEM builds before `Application.onCreate` has finished.
 * A registry that could be un-installed at that moment would have to be null-checked at every call
 * site, and the one call site that forgot would crash the home screen. So [ensure] fills in an
 * application context and the do-nothing defaults on first touch, and [install] simply replaces
 * them. Nothing here is ever null.
 */
object HomeServiceLocator {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var installedBridge: HomeHostPort = HomeHostPort.Default

    @Volatile
    private var installedAds: HomeAdsPort = HomeAdsPort.NoOp

    /**
     * The app the user just tapped, while its launch waits behind the `appLaunch` promo. Set by
     * the launcher right before the promo runs, so the host's ad layer can show which app is
     * about to open (its loader carries that app's icon and name). Null outside a launch.
     */
    @Volatile
    var launchingPackage: String? = null
        internal set

    /** The host's bridge, or the do-nothing default when it installed none. */
    val bridge: HomeHostPort get() = installedBridge

    /** The host's ad wiring, or [HomeAdsPort.NoOp] when it installed none. */
    val ads: HomeAdsPort get() = installedAds

    /**
     * Wires the launcher to its host. Call once from `Application.onCreate`.
     *
     * Safe to call again — a host that re-installs (a test, a process restart, a late-arriving
     * dependency) replaces what was there rather than stacking on it.
     */
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

    /**
     * Records an application context if [install] has not run yet. Called by the launcher's own
     * entry points, never by a host.
     */
    fun ensure(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        HelpIntentFilter.register(context)
    }

    /** The application context, for the launcher's own use. */
    fun context(): Context = requireNotNull(appContext) {
        "HomeServiceLocator has no context: an entry point did not call ensure(context)"
    }

    /**
     * `launcher_config`, resolved for this install's audience.
     *
     * Parsed on every call rather than cached, so a config that lands seconds into a cold start is
     * picked up by the next thing that asks, instead of by the next process. Parsing is a few
     * hundred bytes of JSON and every caller is already on a user-visible path that does more work
     * than this.
     */
    fun setup(): HomeOptions = HomeOptions.parse(
        raw = installedBridge.configString(HomeConfigKeys.LAUNCHER_CONFIG, ""),
        organic = installedBridge.isOrganicAudience(),
    )
}

/** Remote Config keys the launcher reads through [HomeHostPort.configString]. */
object HomeConfigKeys {

    /** Behaviour switches: panels, guide steps, gesture ads, unlock ads. */
    const val LAUNCHER_CONFIG = "launcher_config"

    /** The ads blob, for the two launcher-only fields the ad SDK model drops. */
    const val ADS_CONFIG = "ads_config"

    /** OEM home-layout match rules — which profile applies to which device. */
    const val HOME_PROFILE_RULES = "home_profile_rules"

    /** OEM home-layout probes — where to look inside each OEM launcher's APK. */
    const val HOME_PROFILE_PROBES = "home_profile_probes"
}
