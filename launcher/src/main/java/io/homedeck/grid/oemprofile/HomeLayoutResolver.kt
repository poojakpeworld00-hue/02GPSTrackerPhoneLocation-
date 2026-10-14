package io.homedeck.grid.oemprofile

import android.content.Context
import io.homedeck.grid.R
import io.homedeck.grid.bridge.HomeServiceLocator
import timber.log.Timber

/**
 * Fingerprint in, profile out. The base comes from the rules table (Remote Config first, the
 * built-in copy when the console blob is empty or broken, [HomeLayoutProfile.AOSP] when no rule
 * matches); the launcher's own APK numbers, when readable, are laid over it. Null only when the
 * console has switched the feature off.
 */
object HomeLayoutResolver {

    const val RC_RULES_KEY = "home_profile_rules"
    const val RC_PROBES_KEY = "home_profile_probes"

    fun resolve(context: Context, fp: HomeSignature): HomeLayoutProfile? {
        val remote = HomeLayoutRules.parse(HomeServiceLocator.bridge.configString(RC_RULES_KEY, ""), LayoutOrigin.RC_RULE)
        if (remote?.disabled == true) {
            Timber.i("HomeLayoutProfile: disabled by $RC_RULES_KEY")
            return null
        }
        val base = remote?.select(fp)
            ?: HomeLayoutRules.builtIn.select(fp)
            ?: HomeLayoutProfile.AOSP
        val probes = HomeServiceLocator.bridge.configString(RC_PROBES_KEY, "").ifBlank { PackageProfileParser.DEFAULT_PROBES_JSON }
        val facts = PackageProfileParser.read(context, fp, probes)
        val widthDp = fp.widthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) / it.density }).toInt()
        val profile = (facts?.numbers?.let { base.overlay(it, ownIconDp = { cols -> ownIconDp(context, fp, cols) }) } ?: base)
            .resolveFractions(widthDp, fp.densityDpi.takeIf { it > 0 } ?: context.resources.displayMetrics.densityDpi)
        Timber.i(
            "HomeLayoutProfile: ${fp.homePackage.ifEmpty { "<none>" }} on ${fp.manufacturer}/${fp.model} sw${fp.swDp} " +
                "-> ${profile.id} [apk readers: ${facts?.sources?.joinToString(",").orEmpty().ifEmpty { "none" }}] ${profile.describeSources()}"
        )
        return profile
    }

    /**
     * The icon size our grid draws at [cols] on this display: a cell is the short side of the
     * screen over the columns, less the grid's side margin, which the grid itself scales as
     * `launcher_icon_side_margin * 5 / columns`. Mirrors DeskGrid.fillCellSizes in dp.
     */
    private fun ownIconDp(context: Context, fp: HomeSignature, cols: Int): Float {
        if (cols <= 0) return 0f
        val widthDp = fp.widthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) / it.density }).toInt()
        val density = context.resources.displayMetrics.density
        val marginDp = context.resources.getDimension(R.dimen.launcher_icon_side_margin) / density
        // DeskGrid keeps `normal_margin` clear on both sides before it divides into columns.
        val sideDp = 2 * context.resources.getDimension(org.fossify.commons.R.dimen.normal_margin) / density
        val cell = (widthDp - sideDp) / cols
        return cell - 2 * (marginDp * 5 / cols)
    }
}
