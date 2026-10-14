package io.homedeck.grid.oemprofile

import org.json.JSONArray
import org.json.JSONObject

object HomeLayoutRules {

    class Rule(val match: Match, val profile: HomeLayoutProfile)

    class Match(
        val packages: Set<String>,
        val manufacturers: Set<String>,
        val modelPrefix: String?,
        val versionCodeMin: Long?,
        val versionCodeMax: Long?,
        val sdkMin: Int?,
        val sdkMax: Int?,
        val swDpMin: Int?,
        val swDpMax: Int?,
        val models: Set<String> = emptySet(),
        val devices: Set<String> = emptySet(),
        val products: Set<String> = emptySet(),
        val osVersionMin: Long? = null,
        val osVersionMax: Long? = null,
        val osVersionPrefix: String? = null,
        val densityMin: Int? = null,
        val densityMax: Int? = null,
        val widthDpMin: Int? = null,
        val widthDpMax: Int? = null,
        val heightDpMin: Int? = null,
        val heightDpMax: Int? = null,
        val navMode: Int? = null,
    ) {

        val specificity: Int = listOf(
            packages.isNotEmpty(), manufacturers.isNotEmpty(), modelPrefix != null,
            models.isNotEmpty(), devices.isNotEmpty(), products.isNotEmpty(),
            versionCodeMin != null || versionCodeMax != null,
            osVersionMin != null || osVersionMax != null || osVersionPrefix != null,
            sdkMin != null || sdkMax != null,
            swDpMin != null || swDpMax != null,
            densityMin != null || densityMax != null,
            widthDpMin != null || widthDpMax != null,
            heightDpMin != null || heightDpMax != null,
            navMode != null,
        ).count { it }

        fun matches(fp: HomeSignature): Boolean {
            if (packages.isNotEmpty() && fp.homePackage !in packages) return false
            if (manufacturers.isNotEmpty() && fp.manufacturer !in manufacturers && fp.brand !in manufacturers) return false
            if (modelPrefix != null && !fp.model.startsWith(modelPrefix, ignoreCase = true)) return false
            if (models.isNotEmpty() && models.none { it.equals(fp.model, ignoreCase = true) }) return false
            if (devices.isNotEmpty() && devices.none { it.equals(fp.device, ignoreCase = true) }) return false
            if (products.isNotEmpty() && products.none { it.equals(fp.product, ignoreCase = true) }) return false
            if (versionCodeMin != null && fp.homeVersionCode < versionCodeMin) return false
            if (versionCodeMax != null && fp.homeVersionCode > versionCodeMax) return false
            if (osVersionMin != null && fp.osVersionNumber < osVersionMin) return false
            if (osVersionMax != null && fp.osVersionNumber > osVersionMax) return false
            if (osVersionPrefix != null && !fp.osVersion.startsWith(osVersionPrefix, ignoreCase = true)) return false
            if (sdkMin != null && fp.sdk < sdkMin) return false
            if (sdkMax != null && fp.sdk > sdkMax) return false
            if (swDpMin != null && fp.swDp < swDpMin) return false
            if (swDpMax != null && fp.swDp > swDpMax) return false
            if (densityMin != null && fp.densityDpi < densityMin) return false
            if (densityMax != null && fp.densityDpi > densityMax) return false
            if (widthDpMin != null && fp.widthDp < widthDpMin) return false
            if (widthDpMax != null && fp.widthDp > widthDpMax) return false
            if (heightDpMin != null && fp.heightDp < heightDpMin) return false
            if (heightDpMax != null && fp.heightDp > heightDpMax) return false
            if (navMode != null && fp.navMode != navMode) return false
            return true
        }
    }

    class Table(val disabled: Boolean, val rules: List<Rule>) {
        fun select(fp: HomeSignature): HomeLayoutProfile? =
            rules.filter { it.match.matches(fp) }.maxByOrNull { it.match.specificity }?.profile
    }

    const val KEY_DISABLED = "disabled"
    const val KEY_RULES = "rules"
    const val KEY_MATCH = "match"
    const val KEY_PROFILE = "profile"
    const val KEY_PACKAGE = "package"
    const val KEY_MANUFACTURER = "manufacturer"
    const val KEY_MODEL_PREFIX = "model_prefix"
    const val KEY_VERSION_CODE_MIN = "version_code_min"
    const val KEY_VERSION_CODE_MAX = "version_code_max"
    const val KEY_SDK_MIN = "sdk_min"
    const val KEY_SDK_MAX = "sdk_max"
    const val KEY_SW_DP_MIN = "sw_dp_min"
    const val KEY_SW_DP_MAX = "sw_dp_max"
    const val KEY_MODEL = "model"
    const val KEY_DEVICE = "device"
    const val KEY_PRODUCT = "product"
    const val KEY_OS_VERSION_MIN = "os_version_min"
    const val KEY_OS_VERSION_MAX = "os_version_max"
    const val KEY_OS_VERSION_PREFIX = "os_version_prefix"
    const val KEY_DENSITY_MIN = "density_min"
    const val KEY_DENSITY_MAX = "density_max"
    const val KEY_WIDTH_DP_MIN = "width_dp_min"
    const val KEY_WIDTH_DP_MAX = "width_dp_max"
    const val KEY_HEIGHT_DP_MIN = "height_dp_min"
    const val KEY_HEIGHT_DP_MAX = "height_dp_max"
    const val KEY_NAV_MODE = "nav_mode"

    fun parse(raw: String, source: LayoutOrigin): Table? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return runCatching {
            val (disabled, array) = when {
                text.startsWith("[") -> false to JSONArray(text)
                else -> JSONObject(text).let { it.optBoolean(KEY_DISABLED, false) to (it.optJSONArray(KEY_RULES) ?: JSONArray()) }
            }
            val rules = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val profile = o.optJSONObject(KEY_PROFILE)?.let { HomeLayoutProfile.fromJson(it, source = source) }
                    ?: return@mapNotNull null
                Rule(match(o.optJSONObject(KEY_MATCH) ?: JSONObject()), profile)
            }
            Table(disabled, rules)
        }.getOrNull()
    }

    private fun match(o: JSONObject) = Match(
        packages = strings(o, KEY_PACKAGE),
        manufacturers = strings(o, KEY_MANUFACTURER).map { it.lowercase() }.toSet(),
        modelPrefix = o.optString(KEY_MODEL_PREFIX).trim().takeIf { it.isNotEmpty() },
        versionCodeMin = long(o, KEY_VERSION_CODE_MIN),
        versionCodeMax = long(o, KEY_VERSION_CODE_MAX),
        sdkMin = int(o, KEY_SDK_MIN),
        sdkMax = int(o, KEY_SDK_MAX),
        swDpMin = int(o, KEY_SW_DP_MIN),
        swDpMax = int(o, KEY_SW_DP_MAX),
        models = strings(o, KEY_MODEL),
        devices = strings(o, KEY_DEVICE),
        products = strings(o, KEY_PRODUCT),
        osVersionMin = long(o, KEY_OS_VERSION_MIN),
        osVersionMax = long(o, KEY_OS_VERSION_MAX),
        osVersionPrefix = o.optString(KEY_OS_VERSION_PREFIX).trim().takeIf { it.isNotEmpty() },
        densityMin = int(o, KEY_DENSITY_MIN),
        densityMax = int(o, KEY_DENSITY_MAX),
        widthDpMin = int(o, KEY_WIDTH_DP_MIN),
        widthDpMax = int(o, KEY_WIDTH_DP_MAX),
        heightDpMin = int(o, KEY_HEIGHT_DP_MIN),
        heightDpMax = int(o, KEY_HEIGHT_DP_MAX),
        navMode = int(o, KEY_NAV_MODE),
    )

    private fun strings(o: JSONObject, key: String): Set<String> {
        val array = o.optJSONArray(key)
        if (array != null) {
            return (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf { s -> s.isNotEmpty() } }.toSet()
        }
        return o.optString(key).trim().takeIf { it.isNotEmpty() }?.let { setOf(it) } ?: emptySet()
    }

    private fun long(o: JSONObject, key: String): Long? = if (o.has(key) && !o.isNull(key)) o.optLong(key) else null
    private fun int(o: JSONObject, key: String): Int? = if (o.has(key) && !o.isNull(key)) o.optInt(key) else null

    val knownLaunchers: Set<String> by lazy {
        builtIn.rules.flatMap { it.match.packages }.toSet()
    }

    fun isKnownLauncher(packageName: String): Boolean = packageName in knownLaunchers

    val builtIn: Table by lazy {
        parse(DEFAULT_RULES_JSON, LayoutOrigin.BUILT_IN) ?: Table(disabled = false, rules = emptyList())
    }

    const val DEFAULT_RULES_JSON = """
[
  { "match": { "sw_dp_min": 720 },
    "profile": { "id": "aosp_tablet", "home_cols": 6, "home_rows": 5, "drawer_cols": 6 } },
  { "match": { "sw_dp_min": 600, "sw_dp_max": 719 },
    "profile": { "id": "aosp_fold_inner", "home_cols": 6, "home_rows": 5, "drawer_cols": 6 } },

  { "match": { "package": "com.sec.android.app.launcher", "sw_dp_min": 720 },
    "profile": { "id": "samsung_tablet", "home_cols": 6, "home_rows": 5, "drawer_mode": "paged", "drawer_cols": 8, "drawer_rows": 6, "legacy_icon_tray": "white" } },
  { "match": { "package": "com.sec.android.app.launcher", "sw_dp_min": 600, "sw_dp_max": 719 },
    "profile": { "id": "samsung_fold_inner", "home_cols": 6, "home_rows": 5, "drawer_mode": "paged", "drawer_cols": 6, "drawer_rows": 5, "legacy_icon_tray": "white" } },
  { "match": { "package": "com.sec.android.app.launcher", "os_version_min": 70000 },
    "profile": { "id": "samsung_phone_oneui7", "home_cols": 4, "home_rows": 6, "dock_size": 4, "drawer_mode": "paged", "drawer_cols": 4, "drawer_rows": 6, "drawer_search_position": "bottom", "label_sp": 11, "icon_width_fraction": 0.164, "icon_width_fraction_by_dpi": { "420": 0.161, "450": 0.164, "480": 0.168, "510": 0.170, "540": 0.172 }, "icon_inset": 0.95, "legacy_icon_tray": "white" } },
  { "match": { "package": "com.sec.android.app.launcher" },
    "profile": { "id": "samsung_phone", "home_cols": 4, "home_rows": 5, "dock_size": 4, "drawer_mode": "paged", "drawer_cols": 4, "drawer_rows": 6, "legacy_icon_tray": "white" } },

  { "match": { "package": "com.google.android.apps.nexuslauncher" },
    "profile": { "id": "pixel_phone", "home_cols": 5, "home_rows": 5, "drawer_cols": 5 } },
  { "match": { "package": ["com.android.launcher3", "com.motorola.launcher3", "com.nothing.launcher", "com.sonymobile.home", "com.fairphone.launcher3"] },
    "profile": { "id": "launcher3", "home_cols": 5, "home_rows": 5, "drawer_cols": 5 } },

  { "match": { "package": "com.miui.home" },
    "profile": { "id": "miui_classic", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },
  { "match": { "package": "com.mi.android.globallauncher" },
    "profile": { "id": "poco", "home_cols": 4, "home_rows": 6, "drawer_cols": 4 } },

  { "match": { "package": ["com.android.launcher", "com.oppo.launcher", "net.oneplus.launcher", "com.oneplus.launcher"] },
    "profile": { "id": "coloros", "home_cols": 4, "home_rows": 6, "drawer_cols": 4 } },
  { "match": { "package": ["com.android.launcher", "com.oppo.launcher", "net.oneplus.launcher", "com.oneplus.launcher"], "os_version_min": 150000 },
    "profile": { "id": "coloros15", "home_cols": 4, "home_rows": 6, "drawer_cols": 4, "drawer_search_position": "bottom" } },

  { "match": { "package": "com.bbk.launcher2" },
    "profile": { "id": "vivo_classic", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },

  { "match": { "package": "com.huawei.android.launcher" },
    "profile": { "id": "huawei_classic", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },
  { "match": { "package": "com.hihonor.android.launcher" },
    "profile": { "id": "honor_magicos", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },

  { "match": { "package": ["com.transsion.hilauncher", "com.transsion.XOSLauncher", "com.transsion.itel.launcher"] },
    "profile": { "id": "transsion", "home_cols": 4, "home_rows": 6, "drawer_cols": 4 } },

  { "match": { "package": ["com.teslacoilsw.launcher", "com.microsoft.launcher", "app.lawnchair", "bitpit.launcher", "ginlemon.flowerfree", "com.actionlauncher.playstore"] },
    "profile": { "id": "third_party" } },

  { "match": { "manufacturer": "samsung" },
    "profile": { "id": "samsung_phone", "home_cols": 4, "home_rows": 5, "dock_size": 4, "drawer_mode": "paged", "drawer_cols": 4, "drawer_rows": 6, "legacy_icon_tray": "white" } },
  { "match": { "manufacturer": ["xiaomi", "redmi", "poco"] },
    "profile": { "id": "miui_classic", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },
  { "match": { "manufacturer": ["oppo", "realme", "oneplus"] },
    "profile": { "id": "coloros", "home_cols": 4, "home_rows": 6, "drawer_cols": 4 } },
  { "match": { "manufacturer": ["oppo", "realme", "oneplus"], "os_version_min": 150000 },
    "profile": { "id": "coloros15", "home_cols": 4, "home_rows": 6, "drawer_cols": 4, "drawer_search_position": "bottom" } },
  { "match": { "manufacturer": ["vivo", "iqoo"] },
    "profile": { "id": "vivo_classic", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },
  { "match": { "manufacturer": "huawei" },
    "profile": { "id": "huawei_classic", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },
  { "match": { "manufacturer": "honor" },
    "profile": { "id": "honor_magicos", "home_cols": 4, "home_rows": 6, "drawer_mode": "none", "drawer_cols": 4 } },
  { "match": { "manufacturer": ["tecno", "infinix", "itel"] },
    "profile": { "id": "transsion", "home_cols": 4, "home_rows": 6, "drawer_cols": 4 } }
]
"""
}
