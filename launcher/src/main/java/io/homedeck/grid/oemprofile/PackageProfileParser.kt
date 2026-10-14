package io.homedeck.grid.oemprofile

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.provider.Settings
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import kotlin.math.hypot

object PackageProfileParser {

    private const val RES_AUTO_NS = "http://schemas.android.com/apk/res-auto"
    private const val DEVICE_PROFILES = "device_profiles"
    private const val TAG_GRID_OPTION = "grid-option"
    private const val TAG_DISPLAY_OPTION = "display-option"

    const val KEY_HOME_COLS = "home_cols"
    const val KEY_HOME_ROWS = "home_rows"
    const val KEY_DOCK_SIZE = "dock_size"
    const val KEY_DRAWER_COLS = "drawer_cols"
    const val KEY_ICON_SIZE_DP = "icon_size_dp"
    const val KEY_LABEL_SP = "label_sp"

    const val KEY_DRAWER_MODE = "drawer_mode"
    const val KEY_DRAWER_MODE_SETTING = "drawer_mode_setting"
    const val KEY_DRAWER_MODE_VALUES = "drawer_mode_values"

    const val DEFAULT_PROBES_JSON = """
{
  "com.miui.home": {
    "_verified": "HyperOS, com.miui.home RELEASE-7.50.06.2592 (Xiaomi 24090RA29I, Android 16)",
    "home_cols": ["config_cell_count_x"],
    "home_rows": ["config_cell_count_y"]
  },
  "com.mi.android.globallauncher": {
    "home_cols": ["config_cell_count_x"],
    "home_rows": ["config_cell_count_y"]
  },
  "com.android.launcher": {
    "_verified": "ColorOS/OxygenOS 14 (14.0.62, OnePlus CPH2469) and 16 (16.4.25, CPH2487); device_profiles.xml is read first, these are the fallback",
    "home_cols": ["device_profiles_display_option_oplusnumColumns"],
    "home_rows": ["device_profiles_display_option_oplusnumRows"],
    "drawer_mode": ["config_default_launcher_mode"],
    "drawer_mode_setting": "launcher_mode",
    "drawer_mode_values": { "0": "none", "2": "vertical", "3": "none" }
  },
  "com.oppo.launcher": {
    "_verified": "not yet - same ColorOS code base, name assumed; a miss keeps the rules-table mode",
    "drawer_mode": ["config_default_launcher_mode"],
    "drawer_mode_setting": "launcher_mode",
    "drawer_mode_values": { "0": "none", "2": "vertical", "3": "none" }
  },
  "net.oneplus.launcher": {
    "_verified": "not yet - pre-merge OxygenOS launcher",
    "drawer_mode": ["config_default_launcher_mode"],
    "drawer_mode_setting": "launcher_mode",
    "drawer_mode_values": { "0": "none", "2": "vertical", "3": "none" }
  },
  "com.oneplus.launcher": {
    "_verified": "not yet - pre-merge OxygenOS launcher",
    "drawer_mode": ["config_default_launcher_mode"],
    "drawer_mode_setting": "launcher_mode",
    "drawer_mode_values": { "0": "none", "2": "vertical", "3": "none" }
  }
}
"""

    class ApkFacts(val numbers: HomeLayoutProfile.Numbers?, val sources: List<String>)

    fun read(context: Context, fp: HomeSignature, probesJson: String): ApkFacts? {
        if (fp.homePackage.isEmpty() || fp.homePackage == context.packageName) return null
        val pm = context.packageManager
        val resources = runCatching { pm.getResourcesForApplication(fp.homePackage) }.getOrElse { return null }
        val apkPath = runCatching { pm.getApplicationInfo(fp.homePackage, 0).publicSourceDir }.getOrNull()
        val index = apkPath?.let { runCatching { PackageResourceTable.read(it) }.getOrNull() }
        val sources = ArrayList<String>()

        var numbers = HomeLayoutProfile.Numbers()
        runCatching { readLauncher3(context, resources, fp.homePackage) }
            .onFailure { Timber.d(it, "PackageProfileParser: device_profiles unreadable for ${fp.homePackage}") }
            .getOrNull()?.let { n -> numbers = n; sources.add("device_profiles") }
        if (index != null) {
            runCatching { StockGridParser.read(resources, fp.homePackage, index) }
                .onFailure { Timber.d(it, "PackageProfileParser: generic read failed for ${fp.homePackage}") }
                .getOrNull()?.let { g -> if (!g.isEmpty) { numbers = numbers.fill(g); sources.add("generic") } }
        }
        runCatching { readProbes(context, resources, fp.homePackage, probesJson) }
            .onFailure { Timber.d(it, "PackageProfileParser: probes failed for ${fp.homePackage}") }
            .getOrNull()?.let { p -> if (!p.isEmpty) { numbers = p.fill(numbers); sources.add("probes") } }

        return ApkFacts(numbers.takeIf { !it.isEmpty }, sources)
    }

    private class GridOption(
        val cols: Int,
        val rows: Int,
        val hotseat: Int?,
        val allAppsCols: Int?,
        val deviceCategory: String?,
        val displays: MutableList<DisplayOption> = mutableListOf(),
    )

    private class DisplayOption(
        val minWidthDps: Float,
        val minHeightDps: Float,
        val canBeDefault: Boolean,
        val iconImageSize: Float?,
        val iconTextSize: Float?,
        val allAppsIconSize: Float?,
        val hotseatIconSize: Float?,
    )

    private fun readLauncher3(context: Context, resources: Resources, packageName: String): HomeLayoutProfile.Numbers? {
        val id = resources.getIdentifier(DEVICE_PROFILES, "xml", packageName)
        if (id == 0) return null
        val parser = resources.getXml(id)
        val options = try {
            parseGridOptions(parser)
        } finally {
            parser.close()
        }
        if (options.isEmpty()) return null

        val metrics = context.resources.displayMetrics
        val widthDp = minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density
        val heightDp = maxOf(metrics.widthPixels, metrics.heightPixels) / metrics.density
        val wantedCategory = if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"

        val eligible = options.filter { it.deviceCategory == null || it.deviceCategory.contains(wantedCategory) }
            .ifEmpty { options }
        val (best, display) = eligible.flatMap { option ->
            option.displays.filter { it.canBeDefault }.map { display -> option to display }
        }.minByOrNull { (_, display) ->
            hypot((display.minWidthDps - widthDp).toDouble(), (display.minHeightDps - heightDp).toDouble())
        } ?: return null

        return HomeLayoutProfile.Numbers(
            homeCols = best.cols,
            homeRows = best.rows,
            dockSize = best.hotseat,
            drawerCols = best.allAppsCols ?: best.cols,
            iconSizeDp = display.iconImageSize?.toInt()?.takeIf { it in 24..120 },

            labelSp = display.iconTextSize?.takeIf { it in HomeLayoutProfile.MIN_LABEL_SP..HomeLayoutProfile.MAX_LABEL_SP },

            drawerIconDp = display.allAppsIconSize?.toInt()?.takeIf { it in 24..120 },
            dockIconDp = display.hotseatIconSize?.toInt()?.takeIf { it in 24..120 },
        )
    }

    private fun parseGridOptions(parser: XmlResourceParser): List<GridOption> {
        val options = mutableListOf<GridOption>()
        var current: GridOption? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_GRID_OPTION -> {
                        val cols = parser.int("numColumns")
                        val rows = parser.int("numRows")
                        current = if (cols != null && rows != null) {
                            GridOption(
                                cols = cols,
                                rows = rows,
                                hotseat = parser.int("numHotseatIcons"),
                                allAppsCols = parser.int("numAllAppsColumns"),
                                deviceCategory = parser.str("deviceCategory"),
                            ).also(options::add)
                        } else {
                            null
                        }
                    }

                    TAG_DISPLAY_OPTION -> {
                        val w = parser.float("minWidthDps")
                        val h = parser.float("minHeightDps")
                        if (current != null && w != null && h != null) {

                            current.displays.add(
                                DisplayOption(
                                    w, h, parser.bool("canBeDefault") ?: true, parser.float("iconImageSize"), parser.float("iconTextSize"),
                                    parser.float("allAppsIconSize"), parser.float("hotseatIconSize"),
                                )
                            )
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == TAG_GRID_OPTION) {
                current = null
            }
            event = parser.next()
        }
        return options
    }

    private fun XmlResourceParser.str(name: String): String? =
        getAttributeValue(RES_AUTO_NS, name) ?: getAttributeValue(null, name)

    private fun XmlResourceParser.int(name: String): Int? = str(name)?.trim()?.toIntOrNull()
    private fun XmlResourceParser.float(name: String): Float? = str(name)?.trim()?.toFloatOrNull()
    private fun XmlResourceParser.bool(name: String): Boolean? = str(name)?.trim()?.toBooleanStrictOrNull()

    private fun readInt(resources: Resources, packageName: String, name: String): Int? {
        val intId = resources.getIdentifier(name, "integer", packageName)
        if (intId != 0) return runCatching { resources.getInteger(intId) }.getOrNull()
        val dimenId = resources.getIdentifier(name, "dimen", packageName)
        if (dimenId != 0) {
            return runCatching { (resources.getDimension(dimenId) / resources.displayMetrics.density).toInt() }.getOrNull()
        }
        return null
    }

    fun drawerModeOf(value: Int, values: JSONObject?): TrayLayoutMode? {
        val name = values?.optString(value.toString())?.trim().orEmpty()
        if (name.isEmpty()) return null
        return TrayLayoutMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun readProbes(context: Context, resources: Resources, packageName: String, probesJson: String): HomeLayoutProfile.Numbers? {
        val text = probesJson.trim()
        if (text.isEmpty()) return null
        val probes = JSONObject(text).optJSONObject(packageName) ?: return null
        fun probe(key: String, range: IntRange = HomeLayoutProfile.MIN_CELLS..HomeLayoutProfile.MAX_CELLS): Int? {
            val names = probes.optJSONArray(key) ?: return null
            for (i in 0 until names.length()) {
                val name = names.optString(i).trim()
                if (name.isEmpty()) continue
                val value = readInt(resources, packageName, name) ?: continue
                if (value in range) return value
            }
            return null
        }

        val drawerMode = probes.optJSONArray(KEY_DRAWER_MODE)?.let { names ->
            val values = probes.optJSONObject(KEY_DRAWER_MODE_VALUES)
            val live = probes.optString(KEY_DRAWER_MODE_SETTING).trim().takeIf { it.isNotEmpty() }?.let { key ->
                runCatching { Settings.Global.getInt(context.contentResolver, key) }.getOrNull()
            }
            live?.let { drawerModeOf(it, values) } ?: (0 until names.length()).asSequence()
                .map { names.optString(it).trim() }.filter { it.isNotEmpty() }
                .firstNotNullOfOrNull { name ->
                    readInt(resources, packageName, name)?.let { drawerModeOf(it, values) }
                }
        }

        val labelSp = probes.optJSONArray(KEY_LABEL_SP)?.let { names ->
            (0 until names.length()).asSequence().map { names.optString(it).trim() }.filter { it.isNotEmpty() }
                .firstNotNullOfOrNull { name ->
                    val id = resources.getIdentifier(name, "dimen", packageName)
                    if (id == 0) null
                    else StockGridParser.readTextSizeSp(resources, id)?.takeIf { it in HomeLayoutProfile.MIN_LABEL_SP..HomeLayoutProfile.MAX_LABEL_SP }
                }
        }
        return HomeLayoutProfile.Numbers(
            homeCols = probe(KEY_HOME_COLS),
            homeRows = probe(KEY_HOME_ROWS),
            dockSize = probe(KEY_DOCK_SIZE),
            drawerCols = probe(KEY_DRAWER_COLS),

            iconSizeDp = probe(KEY_ICON_SIZE_DP, 24..120),
            labelSp = labelSp,
            drawerMode = drawerMode,
        )
    }
}
