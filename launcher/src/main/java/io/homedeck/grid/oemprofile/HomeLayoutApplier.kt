package io.homedeck.grid.oemprofile

import android.content.Context
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.support.Config
import io.homedeck.grid.support.GlyphMasker
import io.homedeck.grid.support.MAX_COLUMN_COUNT
import io.homedeck.grid.support.MAX_DOCK_SLOTS
import io.homedeck.grid.support.MAX_ROW_COUNT
import io.homedeck.grid.support.MIN_COLUMN_COUNT
import io.homedeck.grid.support.MIN_ROW_COUNT
import io.homedeck.grid.bridge.HomeServiceLocator
import org.json.JSONObject
import timber.log.Timber

/**
 * Seeds the launcher's knobs from the [HomeLayoutProfile] once, on the very first home-screen build.
 * Runs before the workspace is inflated so the grid is born at the right size; nothing here
 * touches an install whose home screen already exists, and the user remains free to change any
 * of it in the launcher's settings afterwards.
 */
object HomeLayoutApplier {

    /** Written even when nothing is applied, so the decision is taken exactly once per install. */
    private const val APPLIED_NONE = "{}"

    /**
     * Re-sizes the icons of a profile whose sizes are a share of the screen width, from the width
     * the display has now: the screen zoom can change after the profile was applied, and the old
     * launcher would have followed it. Runs on every launcher start; writes only on a change, and
     * leaves dp-sized profiles (and installs without a profile) alone.
     */
    fun renewIconSizes(context: Context) {
        val config = context.launcherConfig
        val json = config.homeProfileJson.takeIf { it.isNotEmpty() && it != APPLIED_NONE } ?: return
        runCatching {
            val saved = HomeLayoutProfile.fromJson(JSONObject(json), source = LayoutOrigin.STANDARD) ?: return
            // The sizing fields follow the rules row the profile came from as it reads today, so a
            // corrected row (One UI's dropped 0.94 inset) reaches installs that applied the old one.
            val row = HomeSignature.stored(context)?.let { fp ->
                val remote = HomeLayoutRules.parse(HomeServiceLocator.bridge.configString(HomeLayoutResolver.RC_RULES_KEY, ""), LayoutOrigin.RC_RULE)
                (remote?.select(fp) ?: HomeLayoutRules.builtIn.select(fp))
            }?.takeIf { it.id == saved.id }
            val stored = row?.let {
                saved.copy(
                    iconWidthFraction = it.iconWidthFraction,
                    iconWidthFractionByDpi = it.iconWidthFractionByDpi,
                    dockIconWidthFraction = it.dockIconWidthFraction,
                    drawerIconWidthFraction = it.drawerIconWidthFraction,
                    iconInset = it.iconInset,
                )
            } ?: saved
            if (stored.iconWidthFraction <= 0f && stored.dockIconWidthFraction <= 0f && stored.drawerIconWidthFraction <= 0f) return
            val metrics = context.resources.displayMetrics
            val widthDp = (minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density).toInt()
            val profile = stored.settleFractions(widthDp, metrics.densityDpi)
            if (config.iconSizeDp != profile.homeArtDp) config.iconSizeDp = profile.homeArtDp
            if (config.dockIconSizeDp != profile.dockArtDp) config.dockIconSizeDp = profile.dockArtDp
            if (config.drawerIconSizeDp != profile.drawerArtDp) config.drawerIconSizeDp = profile.drawerArtDp
        }.onFailure { Timber.w(it, "HomeLayoutProfile: icon refresh failed") }
    }

    /** Which layout the launcher follows - see [imprintStyle]. */
    const val STYLE_OS = "os"
    const val STYLE_DEFAULT = "default"
    const val DEFAULT_ICON_DP = 62

    /**
     * Our own layout, the same on every OS, brand and model: 4x6 above a four-slot dock, a
     * vertical drawer with its search bar at the bottom, our label size. One icon size on the pages, the dock and the drawer alike -
     * [DEFAULT_ICON_DP], a touch above the ~59 dp the drawer drew on its own. The drawer is on and
     * the pages carry every app too.
     */
    val DEFAULT_SETUP: HomeLayoutProfile = HomeLayoutProfile.AOSP.copy(
        id = STYLE_DEFAULT,
        homeCols = 4,
        homeRows = 6,
        dockSize = 4,
        drawerCols = 4,
        iconDp = DEFAULT_ICON_DP,
        dockIconDp = DEFAULT_ICON_DP,
        drawerIconDp = DEFAULT_ICON_DP,
        drawerSearchPosition = TraySearchPlacement.BOTTOM,
    )

    /** `launcher_config.os_style`: true follows the replaced launcher, false (or unset) is [DEFAULT_SETUP]. */
    fun styleFor(osStyle: Boolean): String = if (osStyle) STYLE_OS else STYLE_DEFAULT

    /**
     * Brings the launcher's knobs in line with the style Remote Config asks for. Runs before the
     * workspace inflates and again on every resume and rc_sync push; a no-op while the style is
     * unchanged. Returns true when an already-built home switched style: its app icons are then
     * rebuilt on the next refresh ([Config.homeRebuildPending]) and the caller recreates the screen
     * so every surface reads the new grid.
     */
    fun imprintStyle(context: Context): Boolean {
        val config = context.launcherConfig
        val want = styleFor(HomeServiceLocator.setup().osStyle)
        val built = config.wasHomeScreenInit || config.homeProfileJson.isNotEmpty()
        // An install from before the switch existed took the replaced launcher's layout.
        val have = config.launcherStyle.ifEmpty { if (built) STYLE_OS else "" }
        if (have == want) {
            if (config.launcherStyle.isEmpty()) config.launcherStyle = have
            return want == STYLE_DEFAULT && renewDefault(config)
        }

        if (want == STYLE_OS) {
            val profile = settleOs(context)
            if (profile == null) {
                config.homeProfileJson = APPLIED_NONE
                config.homeAndDrawer = false
            } else {
                // Home + Drawer: the replaced launcher's own build says it has a drawer (ColorOS
                // config_default_launcher_mode 2), and that launcher keeps every app on its pages as well.
                write(config, profile, homeAndDrawer = profile.drawerMode != TrayLayoutMode.NONE &&
                    profile.sourceOf(HomeLayoutProfile.KEY_DRAWER_MODE) == LayoutOrigin.APK)
            }
        } else {
            write(config, DEFAULT_SETUP, homeAndDrawer = true)
        }
        config.launcherStyle = want
        Timber.i("HomeLayoutProfile: launcher style $have -> $want")
        if (!built) return false
        config.homeSeeded = false
        config.homeRebuildPending = true
        return true
    }

    /**
     * Our default setup changed in an app update (a new icon size, say): its knobs are written
     * again. The icons are laid out afresh only when the grid or the dock changed shape. Returns
     * true when anything was written, so the screen redraws.
     */
    private fun renewDefault(config: Config): Boolean {
        val json = DEFAULT_SETUP.toJson().toString()
        if (config.homeProfileJson == json) return false
        val saved = runCatching { HomeLayoutProfile.fromJson(JSONObject(config.homeProfileJson), source = LayoutOrigin.STANDARD) }.getOrNull()
        write(config, DEFAULT_SETUP, homeAndDrawer = true)
        val reshaped = saved == null || saved.homeCols != DEFAULT_SETUP.homeCols ||
            saved.homeRows != DEFAULT_SETUP.homeRows || saved.dockSize != DEFAULT_SETUP.dockSize
        if (reshaped) {
            config.homeSeeded = false
            config.homeRebuildPending = true
        }
        return true
    }

    /** The replaced launcher's profile: its rules row with its own build's numbers laid over it. */
    private fun settleOs(context: Context): HomeLayoutProfile? {
        return runCatching {
            val fp = HomeSignature.stored(context)?.takeIf { it.homePackage.isNotEmpty() }
                ?: HomeSignature.captureAndStore(context)
            HomeLayoutResolver.resolve(context, fp)
        }.onFailure { Timber.w(it, "HomeLayoutProfile: resolve failed, keeping defaults") }.getOrNull()
    }

    private fun write(config: Config, profile: HomeLayoutProfile, homeAndDrawer: Boolean) {
        // The profile counts rows above the dock; the grid counts the dock as its last row.
        config.homeRowCount = (profile.homeRows + 1).coerceIn(MIN_ROW_COUNT, MAX_ROW_COUNT)
        config.homeColumnCount = profile.homeCols.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        config.drawerColumnCount = profile.drawerCols.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        config.showSearchBar = profile.drawerSearch
        config.showHomeAppLabels = profile.homeLabels
        config.showDrawerAppLabels = profile.drawerLabels
        config.drawerMode = profile.drawerMode
        if (profile.drawerMode != TrayLayoutMode.NONE) config.drawerStyleWhenOn = profile.drawerMode
        if (profile.drawerRows > 0) config.drawerRowCount = profile.drawerRows.coerceIn(MIN_ROW_COUNT, MAX_ROW_COUNT)
        // The dock is its own row and may hold more slots than the grid has columns (a 4x6
        // ColorOS home carries five); the grid narrows dock cells to fit.
        config.dockColumnCount = profile.dockSize.coerceIn(1, MAX_DOCK_SLOTS)
        config.drawerSearchAtBottom = profile.drawerSearchPosition == TraySearchPlacement.BOTTOM
        config.iconScale = profile.iconScale
        // The size each surface draws: the launcher's item size less its own margin around the art.
        config.iconSizeDp = profile.homeArtDp
        config.dockIconSizeDp = profile.dockArtDp
        config.drawerIconSizeDp = profile.drawerArtDp
        config.labelTextSp = profile.labelSp
        config.legacyIconTray = GlyphMasker.Tray.parse(profile.legacyIconTray) ?: GlyphMasker.Tray.DOMINANT
        config.homeAndDrawer = homeAndDrawer
        config.homeProfileJson = profile.toJson().toString()
        Timber.i("HomeLayoutProfile: applied ${profile.id} — home ${profile.homeCols}x${profile.homeRows}+dock, drawer ${profile.drawerCols} cols")
    }
}
