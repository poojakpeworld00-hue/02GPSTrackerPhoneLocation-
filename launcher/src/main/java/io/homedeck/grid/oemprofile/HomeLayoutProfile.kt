package io.homedeck.grid.oemprofile

import org.json.JSONObject

enum class TrayLayoutMode { VERTICAL, PAGED, NONE }

enum class TraySearchPlacement { TOP, BOTTOM }

enum class LayoutOrigin { APK, RC_RULE, BUILT_IN, STANDARD }

data class HomeLayoutProfile(
    val id: String,
    val homeCols: Int,
    val homeRows: Int,
    val dockSize: Int,
    val drawerMode: TrayLayoutMode,
    val drawerCols: Int,

    val drawerRows: Int,
    val drawerSearch: Boolean,
    val homeLabels: Boolean,
    val drawerLabels: Boolean,
    val source: LayoutOrigin,
    val drawerSearchPosition: TraySearchPlacement = TraySearchPlacement.TOP,

    val iconScale: Float = 1f,

    val iconDp: Int = 0,

    val dockIconDp: Int = 0,

    val drawerIconDp: Int = 0,

    val iconWidthFraction: Float = 0f,
    val dockIconWidthFraction: Float = 0f,
    val drawerIconWidthFraction: Float = 0f,

    val iconWidthFractionByDpi: Map<Int, Float> = emptyMap(),

    val iconInset: Float = 0f,

    val legacyIconTray: String = LEGACY_TRAY_DOMINANT,

    val labelSp: Float = 0f,

    val fieldSources: Map<String, LayoutOrigin> = emptyMap(),
) {

    data class Numbers(
        val homeCols: Int? = null,
        val homeRows: Int? = null,
        val dockSize: Int? = null,
        val drawerCols: Int? = null,

        val iconSizeDp: Int? = null,

        val labelSp: Float? = null,

        val drawerIconDp: Int? = null,

        val dockIconDp: Int? = null,

        val drawerMode: TrayLayoutMode? = null,

    ) {
        val isEmpty: Boolean
            get() = homeCols == null && homeRows == null && dockSize == null && drawerCols == null && iconSizeDp == null &&
                labelSp == null && drawerIconDp == null && dockIconDp == null && drawerMode == null

        fun fill(other: Numbers) = Numbers(
            homeCols = homeCols ?: other.homeCols,
            homeRows = homeRows ?: other.homeRows,
            dockSize = dockSize ?: other.dockSize,
            drawerCols = drawerCols ?: other.drawerCols,
            iconSizeDp = iconSizeDp ?: other.iconSizeDp,
            labelSp = labelSp ?: other.labelSp,
            drawerIconDp = drawerIconDp ?: other.drawerIconDp,
            dockIconDp = dockIconDp ?: other.dockIconDp,
            drawerMode = drawerMode ?: other.drawerMode,
        )
    }

    fun overlay(numbers: Numbers, ownIconDp: (cols: Int) -> Float = { 0f }): HomeLayoutProfile {
        if (numbers.isEmpty) return this
        val cols = numbers.homeCols ?: homeCols
        val scale = numbers.iconSizeDp?.let { oem ->
            val ours = ownIconDp(cols)
            if (ours > 0f) (oem / ours).coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE) else null
        }

        val tagged = FIELD_KEYS.associateWith { sourceOf(it) }.toMutableMap()
        if (numbers.homeCols != null) tagged[KEY_HOME_COLS] = LayoutOrigin.APK
        if (numbers.homeRows != null) tagged[KEY_HOME_ROWS] = LayoutOrigin.APK
        if (numbers.dockSize != null) tagged[KEY_DOCK_SIZE] = LayoutOrigin.APK
        if (numbers.drawerCols != null) tagged[KEY_DRAWER_COLS] = LayoutOrigin.APK
        if (scale != null) tagged[KEY_ICON_SCALE] = LayoutOrigin.APK
        val label = numbers.labelSp?.takeIf { it in MIN_LABEL_SP..MAX_LABEL_SP }
        if (label != null) tagged[KEY_LABEL_SP] = LayoutOrigin.APK
        val dp = numbers.iconSizeDp?.takeIf { it in MIN_ICON_DP..MAX_ICON_DP }
        if (dp != null) tagged[KEY_ICON_DP] = LayoutOrigin.APK
        val dockDp = numbers.dockIconDp?.takeIf { it in MIN_ICON_DP..MAX_ICON_DP }
        if (dockDp != null) tagged[KEY_DOCK_ICON_DP] = LayoutOrigin.APK
        val drawerDp = numbers.drawerIconDp?.takeIf { it in MIN_ICON_DP..MAX_ICON_DP }
        if (drawerDp != null) tagged[KEY_DRAWER_ICON_DP] = LayoutOrigin.APK
        if (numbers.drawerMode != null) tagged[KEY_DRAWER_MODE] = LayoutOrigin.APK
        return copy(
            homeCols = cols,
            homeRows = numbers.homeRows ?: homeRows,
            dockSize = numbers.dockSize ?: dockSize,
            drawerCols = numbers.drawerCols ?: drawerCols,
            iconScale = scale ?: iconScale,
            labelSp = label ?: labelSp,
            iconDp = dp ?: iconDp,
            dockIconDp = dockDp ?: dockIconDp,
            drawerIconDp = drawerDp ?: drawerIconDp,
            drawerMode = numbers.drawerMode ?: drawerMode,
            source = LayoutOrigin.APK,
            fieldSources = tagged,
        )
    }

    fun settleFractions(widthDp: Int, densityDpi: Int = 0): HomeLayoutProfile {
        if (widthDp <= 0) return this
        fun dp(current: Int, fraction: Float) = if (fraction > 0f) Math.round(fraction * widthDp).takeIf { it in MIN_ICON_DP..MAX_ICON_DP } ?: current else current
        val iconFraction = iconFractionFor(densityDpi)
        val resolved = copy(
            iconDp = dp(iconDp, iconFraction),
            dockIconDp = dp(dockIconDp, dockIconWidthFraction),
            drawerIconDp = dp(drawerIconDp, drawerIconWidthFraction),
        )

        val retagged = fieldSources.toMutableMap()
        fun retag(key: String, fractionKey: String, before: Int, after: Int) {
            if (before != after) retagged[key] = sourceOf(fractionKey)
        }
        retag(KEY_ICON_DP, KEY_ICON_WIDTH_FRACTION, iconDp, resolved.iconDp)
        retag(KEY_DOCK_ICON_DP, KEY_DOCK_ICON_WIDTH_FRACTION, dockIconDp, resolved.dockIconDp)
        retag(KEY_DRAWER_ICON_DP, KEY_DRAWER_ICON_WIDTH_FRACTION, drawerIconDp, resolved.drawerIconDp)
        return resolved.copy(fieldSources = retagged)
    }

    fun iconFractionFor(densityDpi: Int): Float =
        iconWidthFractionByDpi.takeIf { densityDpi > 0 && it.isNotEmpty() }
            ?.minByOrNull { (dpi, _) -> kotlin.math.abs(dpi - densityDpi) }?.value
            ?: iconWidthFraction

    private fun artDp(item: Int): Int = if (item == 0) 0 else Math.round(item * (if (iconInset > 0f) iconInset else 1f)).coerceIn(MIN_ICON_DP, MAX_ICON_DP)
    val homeArtDp: Int get() = artDp(iconDp)
    val dockArtDp: Int get() = artDp(if (dockIconDp > 0) dockIconDp else iconDp)
    val drawerArtDp: Int get() = artDp(if (drawerIconDp > 0) drawerIconDp else iconDp)

    fun sourceOf(key: String): LayoutOrigin = fieldSources[key] ?: source

    fun describeSources(): String = FIELD_KEYS.joinToString(" ") { "$it=${sourceOf(it).name.lowercase()}" }

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_HOME_COLS, homeCols)
        put(KEY_HOME_ROWS, homeRows)
        put(KEY_DOCK_SIZE, dockSize)
        put(KEY_DRAWER_MODE, drawerMode.name.lowercase())
        put(KEY_DRAWER_COLS, drawerCols)
        put(KEY_DRAWER_ROWS, drawerRows)
        put(KEY_DRAWER_SEARCH, drawerSearch)
        put(KEY_HOME_LABELS, homeLabels)
        put(KEY_DRAWER_LABELS, drawerLabels)
        put(KEY_DRAWER_SEARCH_POSITION, drawerSearchPosition.name.lowercase())
        put(KEY_ICON_SCALE, iconScale.toDouble())
        put(KEY_LABEL_SP, labelSp.toDouble())
        put(KEY_ICON_DP, iconDp)
        put(KEY_DOCK_ICON_DP, dockIconDp)
        put(KEY_DRAWER_ICON_DP, drawerIconDp)
        put(KEY_ICON_WIDTH_FRACTION, iconWidthFraction.toDouble())
        put(KEY_DOCK_ICON_WIDTH_FRACTION, dockIconWidthFraction.toDouble())
        put(KEY_DRAWER_ICON_WIDTH_FRACTION, drawerIconWidthFraction.toDouble())
        if (iconWidthFractionByDpi.isNotEmpty()) {
            put(KEY_ICON_WIDTH_FRACTION_BY_DPI, JSONObject().apply { iconWidthFractionByDpi.forEach { (dpi, f) -> put(dpi.toString(), f.toDouble()) } })
        }
        put(KEY_ICON_INSET, iconInset.toDouble())
        put(KEY_LEGACY_ICON_TRAY, legacyIconTray)
        put(KEY_SOURCE, source.name.lowercase())
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_HOME_COLS = "home_cols"
        const val KEY_HOME_ROWS = "home_rows"
        const val KEY_DOCK_SIZE = "dock_size"
        const val KEY_DRAWER_MODE = "drawer_mode"
        const val KEY_DRAWER_COLS = "drawer_cols"
        const val KEY_DRAWER_ROWS = "drawer_rows"
        const val KEY_DRAWER_SEARCH = "drawer_search"
        const val KEY_HOME_LABELS = "home_labels"
        const val KEY_DRAWER_LABELS = "drawer_labels"
        const val KEY_DRAWER_SEARCH_POSITION = "drawer_search_position"
        const val KEY_ICON_SCALE = "icon_scale"
        const val KEY_LABEL_SP = "label_sp"
        const val KEY_ICON_DP = "icon_dp"
        const val KEY_DOCK_ICON_DP = "dock_icon_dp"
        const val KEY_DRAWER_ICON_DP = "drawer_icon_dp"
        const val KEY_ICON_WIDTH_FRACTION = "icon_width_fraction"
        const val KEY_ICON_WIDTH_FRACTION_BY_DPI = "icon_width_fraction_by_dpi"
        const val KEY_DOCK_ICON_WIDTH_FRACTION = "dock_icon_width_fraction"
        const val KEY_DRAWER_ICON_WIDTH_FRACTION = "drawer_icon_width_fraction"
        const val KEY_ICON_INSET = "icon_inset"
        const val KEY_LEGACY_ICON_TRAY = "legacy_icon_tray"
        const val LEGACY_TRAY_WHITE = "white"
        const val LEGACY_TRAY_DOMINANT = "dominant"
        val FIELD_KEYS = listOf(
            KEY_HOME_COLS, KEY_HOME_ROWS, KEY_DOCK_SIZE, KEY_DRAWER_MODE, KEY_DRAWER_COLS, KEY_DRAWER_ROWS,
            KEY_DRAWER_SEARCH, KEY_DRAWER_SEARCH_POSITION, KEY_HOME_LABELS, KEY_DRAWER_LABELS, KEY_ICON_SCALE, KEY_ICON_DP,
            KEY_DOCK_ICON_DP, KEY_DRAWER_ICON_DP, KEY_LABEL_SP, KEY_LEGACY_ICON_TRAY,
        )

        const val MIN_ICON_FRACTION = 0.05f
        const val MAX_ICON_FRACTION = 0.5f
        const val MIN_ICON_DP = 24
        const val MAX_ICON_DP = 120
        const val MIN_ICON_SCALE = 0.5f
        const val MAX_ICON_SCALE = 1.5f

        const val MIN_LABEL_SP = 8f
        const val MAX_LABEL_SP = 20f
        const val KEY_SOURCE = "source"

        val AOSP = HomeLayoutProfile(
            id = "aosp",
            homeCols = 5,
            homeRows = 5,
            dockSize = 5,
            drawerMode = TrayLayoutMode.VERTICAL,
            drawerCols = 5,
            drawerRows = 0,
            drawerSearch = true,
            homeLabels = true,
            drawerLabels = true,
            source = LayoutOrigin.STANDARD,
        )

        fun fromJson(o: JSONObject, defaults: HomeLayoutProfile = AOSP, source: LayoutOrigin): HomeLayoutProfile? {
            val id = o.optString(KEY_ID).trim()
            if (id.isEmpty()) return null
            val profile = HomeLayoutProfile(
                id = id,
                homeCols = o.optInt(KEY_HOME_COLS, defaults.homeCols),
                homeRows = o.optInt(KEY_HOME_ROWS, defaults.homeRows),
                dockSize = o.optInt(KEY_DOCK_SIZE, defaults.dockSize),
                drawerMode = drawerMode(o.optString(KEY_DRAWER_MODE)) ?: defaults.drawerMode,
                drawerCols = o.optInt(KEY_DRAWER_COLS, defaults.drawerCols),
                drawerRows = o.optInt(KEY_DRAWER_ROWS, defaults.drawerRows),
                drawerSearch = o.optBoolean(KEY_DRAWER_SEARCH, defaults.drawerSearch),
                homeLabels = o.optBoolean(KEY_HOME_LABELS, defaults.homeLabels),
                drawerLabels = o.optBoolean(KEY_DRAWER_LABELS, defaults.drawerLabels),
                source = source,
                drawerSearchPosition = drawerSearchPosition(o.optString(KEY_DRAWER_SEARCH_POSITION)) ?: defaults.drawerSearchPosition,
                iconScale = o.optDouble(KEY_ICON_SCALE, defaults.iconScale.toDouble()).toFloat(),
                labelSp = o.optDouble(KEY_LABEL_SP, defaults.labelSp.toDouble()).toFloat(),
                iconDp = o.optInt(KEY_ICON_DP, defaults.iconDp),
                dockIconDp = o.optInt(KEY_DOCK_ICON_DP, defaults.dockIconDp),
                drawerIconDp = o.optInt(KEY_DRAWER_ICON_DP, defaults.drawerIconDp),
                iconWidthFraction = o.optDouble(KEY_ICON_WIDTH_FRACTION, defaults.iconWidthFraction.toDouble()).toFloat(),
                dockIconWidthFraction = o.optDouble(KEY_DOCK_ICON_WIDTH_FRACTION, defaults.dockIconWidthFraction.toDouble()).toFloat(),
                drawerIconWidthFraction = o.optDouble(KEY_DRAWER_ICON_WIDTH_FRACTION, defaults.drawerIconWidthFraction.toDouble()).toFloat(),
                iconWidthFractionByDpi = o.optJSONObject(KEY_ICON_WIDTH_FRACTION_BY_DPI)?.let { table ->
                    table.keys().asSequence().mapNotNull { key ->
                        key.trim().toIntOrNull()?.let { dpi -> dpi to table.optDouble(key, Double.NaN).toFloat() }
                    }.toMap()
                } ?: defaults.iconWidthFractionByDpi,
                iconInset = o.optDouble(KEY_ICON_INSET, defaults.iconInset.toDouble()).toFloat(),
                legacyIconTray = o.optString(KEY_LEGACY_ICON_TRAY).trim().lowercase().ifEmpty { defaults.legacyIconTray },
            )
            return profile.takeIf { it.isSane() }
        }

        private fun drawerMode(raw: String): TrayLayoutMode? =
            TrayLayoutMode.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

        private fun drawerSearchPosition(raw: String): TraySearchPlacement? =
            TraySearchPlacement.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

        const val MIN_CELLS = 2
        const val MAX_CELLS = 12
    }

    fun isSane(): Boolean =
        homeCols in MIN_CELLS..MAX_CELLS && homeRows in MIN_CELLS..MAX_CELLS &&
                dockSize in 0..MAX_CELLS && drawerCols in MIN_CELLS..MAX_CELLS &&
                drawerRows in 0..MAX_CELLS && !iconScale.isNaN() &&
                iconScale in MIN_ICON_SCALE..MAX_ICON_SCALE &&
                !labelSp.isNaN() && (labelSp == 0f || labelSp in MIN_LABEL_SP..MAX_LABEL_SP) &&
                (iconDp == 0 || iconDp in MIN_ICON_DP..MAX_ICON_DP) &&
                (dockIconDp == 0 || dockIconDp in MIN_ICON_DP..MAX_ICON_DP) &&
                (drawerIconDp == 0 || drawerIconDp in MIN_ICON_DP..MAX_ICON_DP) &&
                (listOf(iconWidthFraction, dockIconWidthFraction, drawerIconWidthFraction) + iconWidthFractionByDpi.values).all { !it.isNaN() && (it == 0f || it in MIN_ICON_FRACTION..MAX_ICON_FRACTION) } &&
                iconWidthFractionByDpi.keys.all { it in 100..1000 } &&
                !iconInset.isNaN() && (iconInset == 0f || iconInset in 0.5f..1f) &&
                legacyIconTray in setOf(LEGACY_TRAY_WHITE, LEGACY_TRAY_DOMINANT)
}
