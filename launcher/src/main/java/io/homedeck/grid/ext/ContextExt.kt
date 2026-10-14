package io.homedeck.grid.ext

import android.app.role.RoleManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import io.homedeck.grid.support.GlyphMasker
import android.os.Build
import android.os.Process
import android.util.Size
import android.provider.Telephony
import androidx.annotation.RequiresApi
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isSPlus
import io.homedeck.grid.storage.HomeGridDatabase
import io.homedeck.grid.support.Config
import io.homedeck.grid.contracts.LaunchableAppDao
import io.homedeck.grid.contracts.AppOpenStatDao
import io.homedeck.grid.contracts.ConcealedAppDao
import io.homedeck.grid.contracts.DeskGridEntryDao
import kotlin.math.ceil
import kotlin.math.max

val Context.launcherConfig: Config get() = Config.newInstance(applicationContext)

val Context.launchersDB: LaunchableAppDao
    get() = HomeGridDatabase.getInstance(applicationContext).LaunchableAppDao()

val Context.homeScreenGridItemsDB: DeskGridEntryDao
    get() = HomeGridDatabase.getInstance(
        applicationContext
    ).DeskGridEntryDao()

val Context.hiddenIconsDB: ConcealedAppDao
    get() = HomeGridDatabase.getInstance(applicationContext).ConcealedAppDao()

val Context.appUsageDB: AppOpenStatDao
    get() = HomeGridDatabase.getInstance(applicationContext).AppOpenStatDao()

@get:RequiresApi(Build.VERSION_CODES.Q)
val Context.roleManager: RoleManager
    get() = getSystemService(RoleManager::class.java)

fun Context.getDrawableForPackageName(packageName: String): Drawable? {
    var drawable: Drawable? = null
    try {

        val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityList = launcher.getActivityList(packageName, Process.myUserHandle())[0]
        drawable = activityList.getBadgedIcon(0)
    } catch (e: Exception) {
    } catch (e: Error) {
    }

    if (drawable == null) {
        drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (ignored: Exception) {
            null
        }
    }

    return drawable?.let { GlyphMasker.shape(it, launcherConfig.legacyIconTray) }
}

fun Context.getInitialCellSize(
    info: AppWidgetProviderInfo,
    fallbackWidth: Int,
    fallbackHeight: Int
): Size {
    return if (isSPlus() && info.targetCellWidth != 0 && info.targetCellHeight != 0) {
        Size(info.targetCellWidth, info.targetCellHeight)
    } else {
        val widthCells = getCellCount(fallbackWidth)
        val heightCells = getCellCount(fallbackHeight)
        Size(widthCells, heightCells)
    }
}

fun Context.getCellCount(size: Int): Int {
    val tiles = ceil(((size / resources.displayMetrics.density) - 30) / 70.0).toInt()
    return max(tiles, 1)
}

fun Context.isDefaultSmsApp(): Boolean {
    val roleHeld = if (isQPlus()) {
        runCatching {
            with(roleManager) {
                if (isRoleAvailable(RoleManager.ROLE_SMS)) {
                    isRoleHeld(RoleManager.ROLE_SMS)
                } else {
                    null
                }
            }
        }.getOrNull()
    } else {
        null
    }

    return roleHeld ?: (Telephony.Sms.getDefaultSmsPackage(this) == packageName)
}

fun Context.isDefaultLauncher(): Boolean {
    return if (isQPlus()) {
        with(roleManager) {
            isRoleAvailable(RoleManager.ROLE_HOME) && isRoleHeld(RoleManager.ROLE_HOME)
        }
    } else {
        val filters = ArrayList<IntentFilter>()
        val activities = ArrayList<ComponentName>()
        @Suppress("DEPRECATION")
        packageManager.getPreferredActivities(filters, activities, null)
        return activities.indices.any { i ->
            activities[i].packageName == packageName &&
                    filters[i].hasAction(Intent.ACTION_MAIN) &&
                    filters[i].hasCategory(Intent.CATEGORY_HOME)
        }
    }
}
