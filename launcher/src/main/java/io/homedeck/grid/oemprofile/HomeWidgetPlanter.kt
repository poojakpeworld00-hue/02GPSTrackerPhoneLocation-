package io.homedeck.grid.oemprofile

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetHost
import android.content.Context
import android.os.Build
import io.homedeck.grid.bridge.HomeServiceLocator
import io.homedeck.grid.setup.HomeOptions
import io.homedeck.grid.ext.homeScreenGridItemsDB
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.support.ITEM_TYPE_WIDGET
import io.homedeck.grid.support.PSEUDO_WIDGET_GOOGLE_SEARCH
import io.homedeck.grid.support.PSEUDO_WIDGET_SEARCH
import io.homedeck.grid.support.PSEUDO_WIDGET_TIME
import io.homedeck.grid.support.WIDGET_HOST_ID
import io.homedeck.grid.entities.DeskGridEntry
import timber.log.Timber

object HomeWidgetPlanter {

    private val GOOGLE_PROVIDERS = listOf(
        "com.google.android.googlequicksearchbox.SearchWidgetProvider",
        "com.google.android.apps.gsa.staticplugins.searchwidget.GoogleSearchWidgetProvider",
    )

    private val CHROME_PROVIDERS = listOf(
        "org.chromium.chrome.browser.quickactionsearchwidget.QuickActionSearchWidgetProvider\$QuickActionSearchWidgetProviderSearch",
        "org.chromium.chrome.browser.searchwidget.SearchWidgetProvider",
    )

    private const val TIME_COLUMNS = 4
    private const val TIME_ROWS = 2

    private const val LEGACY_TIME_TOP_ROW = 2

    private const val MAX_ROWS_TALL_DP = 140

    fun seedFirstPage(context: Context) {
        val config = context.launcherConfig
        val setup = HomeServiceLocator.setup()
        config.timeWidgetShown = setup.showTimeWidget
        config.searchWidgetShown = setup.showSearchWidget
        config.timeWidgetAtTop = true
        if (setup.showTimeWidget) seedTimeWidget(context)
        if (setup.showSearchWidget) seedSearchRow(context)
    }

    fun seedSearchRow(context: Context): Boolean {
        val config = context.launcherConfig
        val columns = config.homeColumnCount
        if (columns <= 0) return false

        val row = config.homeRowCount - 2
        if (row < 0) return false

        val db = context.homeScreenGridItemsDB
        val items = db.getAllItems()
        if (items.any { isSeededSearchRow(it) }) return false
        val rowTaken = items.any { !it.docked && it.parentId == null && it.page == 0 && it.top <= row && it.bottom >= row }
        if (rowTaken) return false

        val choice = HomeServiceLocator.setup().searchWidget
        config.searchWidget = choice
        val provider = searchProvider(context, choice)
        val item = DeskGridEntry(
            id = null,
            left = 0,
            top = row,
            right = columns - 1,
            bottom = row,
            page = 0,
            packageName = provider?.provider?.packageName ?: context.packageName,
            activityName = "",
            title = "",
            type = ITEM_TYPE_WIDGET,
            className = provider?.provider?.className ?: PSEUDO_WIDGET_GOOGLE_SEARCH,

            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
        )
        db.insert(item)
        Timber.i("HomeWidgetPlanter: search row ($choice) = ${item.packageName}/${item.className}")
        return true
    }

    fun syncFirstPage(context: Context): Boolean {
        val config = context.launcherConfig
        if (!config.homeSeeded) return false
        val setup = HomeServiceLocator.setup()
        var removed = false

        if (!config.timeWidgetAtTop) {
            config.timeWidgetAtTop = true
            moveTimeWidgetToTop(context)
        }

        if (config.timeWidgetShown != setup.showTimeWidget) {
            config.timeWidgetShown = setup.showTimeWidget
            if (setup.showTimeWidget) seedTimeWidget(context) else removed = dropWidgets(context) { it.className == PSEUDO_WIDGET_TIME }
            Timber.i("HomeWidgetPlanter: time widget ${if (setup.showTimeWidget) "on" else "off"}")
        }

        if (config.searchWidgetShown != setup.showSearchWidget) {
            config.searchWidgetShown = setup.showSearchWidget
            if (setup.showSearchWidget) seedSearchRow(context) else removed = dropWidgets(context) { isSeededSearchRow(it) } || removed
            Timber.i("HomeWidgetPlanter: search widget ${if (setup.showSearchWidget) "on" else "off"}")
        } else if (setup.showSearchWidget && config.searchWidget != setup.searchWidget) {
            dropWidgets(context) { isSeededSearchRow(it) }

            config.searchWidget = setup.searchWidget
            seedSearchRow(context)
            removed = true
            Timber.i("HomeWidgetPlanter: search widget switched to ${setup.searchWidget}")
        }
        return removed
    }

    private fun moveTimeWidgetToTop(context: Context) {
        val db = context.homeScreenGridItemsDB
        val items = db.getAllItems()
        val time = items.firstOrNull {
            it.type == ITEM_TYPE_WIDGET && it.className == PSEUDO_WIDGET_TIME && it.page == 0 && it.top == LEGACY_TIME_TOP_ROW
        } ?: return
        val bottom = time.bottom - time.top
        val taken = items.any {
            it.id != time.id && !it.docked && it.parentId == null && it.page == 0 &&
                it.left <= time.right && it.right >= time.left && it.top <= bottom
        }
        if (taken) return
        db.updateItemPosition(time.left, 0, time.right, bottom, time.page, false, null, time.id ?: return)
        Timber.i("HomeWidgetPlanter: time widget moved back to row 0, above the default-home banner")
    }

    private fun dropWidgets(context: Context, match: (DeskGridEntry) -> Boolean): Boolean {
        val db = context.homeScreenGridItemsDB
        val gone = db.getAllItems().filter { it.type == ITEM_TYPE_WIDGET && match(it) }
        gone.forEach { item ->
            if (item.widgetId > 0) runCatching { AppWidgetHost(context, WIDGET_HOST_ID).deleteAppWidgetId(item.widgetId) }
            db.deleteItemById(item.id ?: return@forEach)
        }
        return gone.isNotEmpty()
    }

    fun seedTimeWidget(context: Context): Boolean {
        val config = context.launcherConfig
        val columns = config.homeColumnCount
        val rows = config.homeRowCount - 1
        if (columns <= 0 || rows < TIME_ROWS + 1) return false
        val right = minOf(TIME_COLUMNS, columns) - 1
        val top = 0
        val bottom = TIME_ROWS - 1

        val db = context.homeScreenGridItemsDB
        val items = db.getAllItems()
        if (items.any { it.className == PSEUDO_WIDGET_TIME && it.page == 0 }) return false
        val taken = items.any {
            !it.docked && it.parentId == null && it.page == 0 && it.left <= right && it.top <= bottom && it.bottom >= top
        }
        if (taken) return false

        db.insert(
            DeskGridEntry(
                id = null,
                left = 0,
                top = top,
                right = right,
                bottom = bottom,
                page = 0,
                packageName = context.packageName,
                activityName = "",
                title = "",
                type = ITEM_TYPE_WIDGET,
                className = PSEUDO_WIDGET_TIME,
                widgetId = -1,
                shortcutId = "",
                icon = null,
                docked = false,
                parentId = null,
            )
        )
        Timber.i("HomeWidgetPlanter: time widget ${right + 1}x$TIME_ROWS at the top left")
        return true
    }

    fun isSeededWidget(item: DeskGridEntry): Boolean =
        isSeededSearchRow(item) ||
            (item.type == ITEM_TYPE_WIDGET && item.page == 0 && item.className == PSEUDO_WIDGET_TIME)

    private fun isSeededSearchRow(item: DeskGridEntry): Boolean =
        item.type == ITEM_TYPE_WIDGET && !item.docked && item.parentId == null && item.page == 0 &&
            item.left == 0 && item.top == item.bottom &&
            (item.className == PSEUDO_WIDGET_GOOGLE_SEARCH || item.className == PSEUDO_WIDGET_SEARCH ||
                item.className in GOOGLE_PROVIDERS || item.className in CHROME_PROVIDERS)

    fun configurationOptional(info: AppWidgetProviderInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0

    private fun searchProvider(context: Context, choice: String): AppWidgetProviderInfo? = runCatching {
        val installed = AppWidgetManager.getInstance(context).installedProviders
            .filter { it.minHeight <= MAX_ROWS_TALL_DP * context.resources.displayMetrics.density }

            .filter { it.configure == null || configurationOptional(it) }
        val order = if (choice == HomeOptions.SEARCH_WIDGET_CHROME) CHROME_PROVIDERS + GOOGLE_PROVIDERS else GOOGLE_PROVIDERS + CHROME_PROVIDERS
        order.firstNotNullOfOrNull { className ->
            installed.firstOrNull { it.provider.className == className }
        }
    }.getOrNull()
}
