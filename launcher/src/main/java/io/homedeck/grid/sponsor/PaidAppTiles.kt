package io.homedeck.grid.sponsor

import android.app.Activity
import io.homedeck.grid.bridge.HomeServiceLocator
import io.homedeck.grid.setup.HomeOptions.PaidAppTile
import io.homedeck.grid.entities.LaunchableApp

object PaidAppTiles {

    private const val PACKAGE_PREFIX = "io.homedeck.grid.sponsored."

    private val logos = HashMap<String, String>()

    fun isSponsored(item: LaunchableApp) = item.packageName.startsWith(PACKAGE_PREFIX)

    fun logo(item: LaunchableApp): String = logos[item.packageName].orEmpty()

    fun interleave(apps: List<LaunchableApp>): List<LaunchableApp> {
        val setup = HomeServiceLocator.setup()
        val feed = setup.drawerAppAds
        if (!setup.drawerAppAdEnabled || feed.isEmpty() || apps.isEmpty()) return apps
        val every = setup.drawerAppAdEvery.coerceAtLeast(1)
        val out = ArrayList<LaunchableApp>(apps.size + feed.size)
        var fed = 0
        apps.forEachIndexed { i, app ->
            out.add(app)
            if ((i + 1) % every == 0 && fed < feed.size) out.add(marker(feed[fed], fed++))
        }
        return out
    }
    private fun marker(ad: PaidAppTile, index: Int): LaunchableApp {

        val pkg = "$PACKAGE_PREFIX$index"
        logos[pkg] = ad.logo
        return LaunchableApp(
            id = null,
            title = ad.label,
            packageName = pkg,
            activityName = ad.landingUrl,
            order = 0,
            thumbnailColor = 0,
            drawable = null,
        )
    }
    fun open(activity: Activity, item: LaunchableApp) =
        HomeServiceLocator.ads.openSponsored(activity, item.activityName)
}
