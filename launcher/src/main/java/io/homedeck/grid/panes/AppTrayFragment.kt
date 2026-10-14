package io.homedeck.grid.panes

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.content.pm.LauncherApps
import android.os.Process
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.homedeck.grid.databinding.HdkRowSearchShortcutBinding
import io.homedeck.grid.ext.appUsageDB
import io.homedeck.grid.support.AppRecommender
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.views.MyGridLayoutManager
import io.homedeck.grid.R
import io.homedeck.grid.screens.HomeDeskPane
import io.homedeck.grid.lists.TrayPagesAdapter
import io.homedeck.grid.lists.LaunchablesAdapter
import io.homedeck.grid.oemprofile.TrayLayoutMode
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import io.homedeck.grid.databinding.HdkAllAppsFragmentBinding
import io.homedeck.grid.ext.applyDrawerSkin
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.ext.fireApp
import io.homedeck.grid.sponsor.HomeAdsSettings
import io.homedeck.grid.bridge.HomeServiceLocator
import io.homedeck.grid.sponsor.PaidAppTiles
import io.homedeck.grid.ext.prepareDrawerBackground
import io.homedeck.grid.support.ITEM_TYPE_ICON
import io.homedeck.grid.contracts.InstalledAppsCallback
import io.homedeck.grid.entities.LaunchableApp
import io.homedeck.grid.entities.DeskGridEntry
import io.homedeck.grid.entities.appLauncherComparator

class AppTrayFragment(
    context: Context,
    attributeSet: AttributeSet
) : HomeDeskFragment<HdkAllAppsFragmentBinding>(context, attributeSet), InstalledAppsCallback {

    companion object {

        private const val SEARCH_COLUMNS = 4
        private const val SEARCH_MAX_RESULTS = 8

        private const val PREWARM_ROWS = 4
        private const val SEARCH_MAX_SHORTCUTS = 4
    }

    private var lastTouchCoords = Pair(0f, 0f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var touchDownY = -1
    var ignoreTouches = false

    private var launchers = emptyList<LaunchableApp>()

    @Volatile
    private var submittedShape: List<Any>? = null

    private fun drawerShape(): List<Any> = with(context.launcherConfig) {
        listOf(drawerMode, drawerColumnCount, drawerRowCount, showDrawerAppLabels)
    }

    private fun sameLaunchers(a: List<LaunchableApp>, b: List<LaunchableApp>): Boolean =
        a.size == b.size && a.indices.all { i ->
            val x = a[i]
            val y = b[i]
            x.packageName == y.packageName && x.activityName == y.activityName && x.title == y.title &&
                x.thumbnailColor == y.thumbnailColor && x.drawable === y.drawable
        }

    private var scrollListenerAdded = false

    private val adSlot by lazy {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
    }

    private var launchersAdapter: LaunchablesAdapter? = null

    private var pagesAdapter: TrayPagesAdapter? = null
    private var shownPaged: Boolean? = null
    private var touchDownX = -1

    private val isPaged: Boolean get() = context.launcherConfig.drawerMode == TrayLayoutMode.PAGED

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: HomeDeskPane) {
        this.activity = activity
        this.binding = HdkAllAppsFragmentBinding.bind(this)

        val layoutManager = binding.allAppsGridView.layoutManager as MyGridLayoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (getAdapter()?.isAdSlotPosition(position) == true) layoutManager.spanCount else 1
        }

        binding.allAppsGridView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }

        binding.allAppsPagerView.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = paintDots(selected = position)
        })
    }

    fun renewAds() {
        HomeServiceLocator.ads.wireNativeOnOpen(activity, HomeAdsSettings.KEY_APP_DRAWER, adSlot)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prepareDrawerBackground(context.getColor(R.color.launcher_all_app_bg))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (binding.allAppsGridView.layoutManager == null || (binding.allAppsGridView.adapter == null && pagesAdapter == null)) {
            return
        }

        val layoutManager = binding.allAppsGridView.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.launcherConfig.drawerColumnCount || shownPaged != isPaged) {
            onConfigurationChanged()

            getAdapter()?.notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGridView.scrollToPosition(0)
        binding.allAppsPagerView.setCurrentItem(0, false)
        prepareViews()

        val layoutManager = binding.allAppsGridView.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.launcherConfig.drawerColumnCount
        setupAdapter(launchers)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

        var shouldIntercept = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y.toInt()
                touchDownX = event.x.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreTouches) {

                    if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                        touchDownY = -1
                        return true
                    }
                }

                if (touchDownY != -1) {
                    val distance = event.y.toInt() - touchDownY

                    val mostlyVertical = !isPaged || abs(distance) > abs(event.x.toInt() - touchDownX)
                    shouldIntercept = distance > touchSlop && mostlyVertical && isGridAtTop()
                    if (shouldIntercept) {

                        if (binding.searchBarView.hasFocus()) {
                            activity?.hideKeyboard()
                        }
                        activity?.kickHandlingTouches(touchDownY)
                        touchDownY = -1
                    }
                }
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        return shouldIntercept
    }

    private fun isGridAtTop(): Boolean {

        if (isPaged) return true
        val grid = binding.allAppsGridView
        val first = grid.getChildAt(0) ?: return true
        val layoutManager = grid.layoutManager ?: return true
        return grid.getChildAdapterPosition(first) <= 0 &&
            layoutManager.getDecoratedTop(first) >= grid.paddingTop
    }

    fun gotLaunchers(appLaunchers: List<LaunchableApp>, force: Boolean = false) {
        val sorted = appLaunchers.sortedWith(appLauncherComparator)
        val shape = drawerShape()
        if (!force && shape == submittedShape && sameLaunchers(sorted, launchers)) return
        launchers = sorted
        submittedShape = shape

        setupAdapter(launchers)
    }

    private fun live(apps: List<LaunchableApp>, current: List<LaunchableApp>): List<LaunchableApp> {
        val byKey = current.associateBy { it.packageName to it.activityName }
        return apps.mapNotNull { byKey[it.packageName to it.activityName] ?: current.firstOrNull { c -> c.packageName == it.packageName } }
    }

    private fun getAdapter() = launchersAdapter

    private val onItemClick: (Any) -> Unit = {
        val launcher = it as LaunchableApp

        if (PaidAppTiles.isSponsored(launcher)) {
            activity?.let { a -> PaidAppTiles.open(a, launcher) }
        } else if (launcher.packageName == context.applicationContext.packageName) {

            activity?.unfoldHostApp(launcher.activityName)
        } else {
            activity?.fireApp(
                launcher.packageName, launcher.activityName,
                io.homedeck.grid.sponsor.HomeAdsSettings.DRAWER_LAUNCH,
            )
        }
        if (activity?.launcherConfig?.shutAppDrawer == true) {
            activity?.shutAppDrawer(delayed = true)
        }
        ignoreTouches = false
        touchDownY = -1
    }

    private fun setupAdapter(launchers: List<LaunchableApp>) {
        activity?.runOnUiThread {
            val paged = isPaged
            val layoutManager = binding.allAppsGridView.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.launcherConfig.drawerColumnCount

            if (shownPaged != paged) {
                shownPaged = paged
                binding.allAppsGridView.beVisibleIf(!paged)
                binding.allAppsPagerView.beVisibleIf(paged)
                binding.allAppsPageDotsView.beVisibleIf(paged)
                (adSlot.parent as? ViewGroup)?.removeView(adSlot)
                if (paged) {
                    launchersAdapter = null
                    binding.allAppsGridView.adapter = null
                } else {
                    pagesAdapter = null
                    binding.allAppsPagerView.adapter = null
                }
            }

            if (paged) {
                if (pagesAdapter == null) {
                    pagesAdapter = TrayPagesAdapter(activity!!, this, adSlot, onItemClick)
                    binding.allAppsPagerView.adapter = pagesAdapter
                }
            } else if (getAdapter() == null) {
                LaunchablesAdapter(activity!!, this, adSlot, onItemClick).apply {
                    launchersAdapter = this
                    binding.allAppsGridView.itemAnimator = null
                    binding.allAppsGridView.adapter = this
                    prewarm(binding.allAppsGridView, layoutManager.spanCount * PREWARM_ROWS)
                }
            }

            submitList(launchers.toMutableList())
        }
    }

    private fun chunkPages(apps: List<LaunchableApp>): List<List<LaunchableApp>> {
        if (apps.isEmpty()) return emptyList()
        val columns = context.launcherConfig.drawerColumnCount.coerceAtLeast(1)
        val rows = context.launcherConfig.drawerRowCount.coerceAtLeast(2)
        val perPage = columns * rows
        val firstPageApps = (perPage - columns).coerceAtLeast(columns)
        val pages = ArrayList<List<LaunchableApp>>()
        val first = apps.take(firstPageApps).toMutableList()
        val adIndex = (HomeAdsSettings.drawerAdRowPosition() * columns).coerceIn(0, first.size)
        first.add(adIndex, LaunchablesAdapter.AD_SLOT)
        pages.add(first)
        apps.drop(firstPageApps).chunked(perPage).forEach(pages::add)
        return pages
    }

    private fun paintDots(count: Int = pagesAdapter?.itemCount ?: 0, selected: Int = binding.allAppsPagerView.currentItem) {
        val dots = binding.allAppsPageDotsView
        val size = resources.getDimensionPixelSize(R.dimen.launcher_drawer_page_dot)
        val gap = resources.getDimensionPixelSize(R.dimen.launcher_drawer_page_dot_gap)
        while (dots.childCount > count) dots.removeViewAt(dots.childCount - 1)
        while (dots.childCount < count) {
            dots.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also { it.marginStart = gap; it.marginEnd = gap }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(context.getColor(android.R.color.white))
                }
            })
        }
        for (i in 0 until dots.childCount) dots.getChildAt(i).alpha = if (i == selected) 1f else 0.35f
        dots.beVisibleIf(isPaged && count > 1)
    }

    fun resetToFirstPage() {
        if (binding.allAppsPagerView.adapter != null) binding.allAppsPagerView.setCurrentItem(0, false)
    }

    fun onIconHidden(item: DeskGridEntry) {
        val itemToRemove = launchers.firstOrNull {
            it.getLauncherIdentifier() == item.getItemIdentifier()
        }

        if (itemToRemove != null) {
            val position = launchers.indexOfFirst {
                it.getLauncherIdentifier() == item.getItemIdentifier()
            }

            launchers = launchers.toMutableList().apply {
                removeAt(position)
            }

            submitList(launchers.toMutableList())
        }
    }

    fun prepareViews() {
        if (activity == null) {
            return
        }

        if (!scrollListenerAdded) {
            scrollListenerAdded = true
            binding.allAppsGridView.addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                    if (binding.searchBarView.hasFocus() && dy > 0 && binding.allAppsGridView.computeVerticalScrollOffset() > 0) {
                        activity?.hideKeyboard()
                    }
                }
            })
        }

        prepareDrawerBackground(context.getColor(R.color.launcher_all_app_bg))

        applySearchPosition()
        imprintBottomSearchClearance()
        binding.searchBarView.beVisibleIf(context.launcherConfig.showSearchBar)
        binding.searchBarView.requireToolbar().beGone()
        binding.searchBarView.updateColors()
        binding.searchBarView.applyDrawerSkin()
        binding.searchBarView.setupMenu()

        binding.searchBarView.onSearchTextChangedListener = {
            submitList(launchers)
        }
        binding.searchBarView.onSearchOpenListener = { presentSearchPanel(true) }
        binding.searchBarView.onSearchClosedListener = { presentSearchPanel(false) }

        binding.searchBarView.binding.topToolbarSearch.setOnEditorActionListener { _, actionId, _ ->
            if (binding.searchBarView.getCurrentQuery().isEmpty()) return@setOnEditorActionListener false
            when (actionId) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO -> (if (isPaged) pagesAdapter?.firstPageAdapter else getAdapter())?.fireFirstApp() == true
                else -> false
            }
        }
    }

    private var appliedSearchAtBottom: Boolean? = null

    private fun applySearchPosition() {
        val bottom = context.launcherConfig.drawerSearchAtBottom

        if (appliedSearchAtBottom == bottom) return
        appliedSearchAtBottom = bottom
        val search = binding.searchBarView.id
        val dots = binding.allAppsPageDotsView.id

        fun RelativeLayout.LayoutParams.anchor(vararg rules: Pair<Int, Int>) {
            for (verb in listOf(RelativeLayout.ABOVE, RelativeLayout.BELOW, RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.ALIGN_PARENT_BOTTOM)) {
                removeRule(verb)
            }
            rules.forEach { (verb, subject) -> addRule(verb, subject) }
        }

        (binding.searchBarView.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_BOTTOM to RelativeLayout.TRUE else RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE
        )
        (binding.allAppsGridView.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ABOVE to search else RelativeLayout.BELOW to search
        )
        (binding.allAppsPageDotsView.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ABOVE to search else RelativeLayout.ALIGN_PARENT_BOTTOM to RelativeLayout.TRUE
        )
        (binding.allAppsPagerView.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE else RelativeLayout.BELOW to search,
            RelativeLayout.ABOVE to dots,
        )
        (binding.noResultsPlaceholderView.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE else RelativeLayout.BELOW to search
        )

        (binding.searchPanelView.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE else RelativeLayout.BELOW to search,
            if (bottom) RelativeLayout.ABOVE to search else RelativeLayout.ALIGN_PARENT_BOTTOM to RelativeLayout.TRUE,
        )
        binding.searchPanelView.gravity = Gravity.TOP
        binding.searchBarView.requestLayout()
    }

    private fun imprintBottomSearchClearance() {
        if (!clearanceWatchAdded) {
            clearanceWatchAdded = true
            viewTreeObserver.addOnGlobalLayoutListener { syncBottomSearchClearance() }
        }
        syncBottomSearchClearance()
    }

    private var clearanceWatchAdded = false

    private fun syncBottomSearchClearance() {
        val bottom = context.launcherConfig.drawerSearchAtBottom
        val wanted = if (bottom) binding.allAppsGridView.paddingBottom else 0
        val bar = binding.searchBarView
        if (bar.paddingBottom != wanted) {
            bar.setPadding(bar.paddingLeft, bar.paddingTop, bar.paddingRight, wanted)
        }

        val grid = binding.allAppsGridView
        val topGap = if (bottom) resources.getDimensionPixelSize(R.dimen.launcher_drawer_list_top_gap) else 0
        if (grid.paddingTop != topGap) {
            grid.setPadding(grid.paddingLeft, topGap, grid.paddingRight, grid.paddingBottom)
        }
        syncDotsToSearchPill(bottom)
    }

    private fun syncDotsToSearchPill(bottom: Boolean) {
        val dots = binding.allAppsPageDotsView
        val params = dots.layoutParams as? RelativeLayout.LayoutParams ?: return

        if (bottom) {
            val own = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
            if (dots.paddingBottom != own) dots.setPadding(dots.paddingLeft, dots.paddingTop, dots.paddingRight, own)
        }
        val margin = if (bottom) {
            val pill = binding.searchBarView.binding.toolbarContainer
            var pillTop = 0
            var v: View? = pill
            while (v != null && v !== binding.searchBarView) { pillTop += v.top; v = v.parent as? View }
            if (v == null || pill.height == 0) return
            val gap = resources.getDimensionPixelSize(R.dimen.launcher_page_dots_search_gap)
            -(pillTop - gap).coerceAtLeast(0)
        } else {
            0
        }
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin
            dots.layoutParams = params
        }
    }

    private fun presentNoResultsPlaceholderIfNeeded() {
        val itemCount = if (isPaged) pagesAdapter?.itemCount else getAdapter()?.itemCount
        binding.noResultsPlaceholderView.beVisibleIf(itemCount != null && itemCount == 0)
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: LaunchableApp) {
        val gridItem = DeskGridEntry(
            id = null,
            left = -1,
            top = -1,
            right = -1,
            bottom = -1,
            page = 0,
            packageName = appLauncher.packageName,
            activityName = appLauncher.activityName,
            title = appLauncher.title,
            type = ITEM_TYPE_ICON,
            className = "",
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appLauncher.drawable
        )

        activity?.presentHomeIconMenu(x, y, gridItem, true)
        ignoreTouches = true

        binding.searchBarView.closeSearch()
    }

    fun resetSearch() {
        if (binding.searchBarView.isSearchOpen) {
            binding.searchBarView.closeSearch()
        }
    }

    private fun submitList(items: List<LaunchableApp>) {
        val searchQuery = binding.searchBarView.getCurrentQuery()
        if (binding.searchBarView.isSearchOpen) {
            paintSearchPanel(items, searchQuery)
            return
        }
        val filtered = if (searchQuery.isNotEmpty()) {
            items.filter {
                it.title.normalizeString()
                    .contains(searchQuery.normalizeString(), ignoreCase = true)
            }
        } else {
            PaidAppTiles.interleave(items)
        }

        if (isPaged) {
            pagesAdapter?.submitPages(chunkPages(filtered), context.launcherConfig.drawerColumnCount, context.launcherConfig.drawerRowCount.coerceAtLeast(2))
            binding.allAppsPagerView.setCurrentItem(0, false)
            paintDots(selected = 0)
            presentNoResultsPlaceholderIfNeeded()
            return
        }
        getAdapter()?.submitList(withAdRow(filtered)) {
            presentNoResultsPlaceholderIfNeeded()
        }
    }

    private var searchAdapter: LaunchablesAdapter? = null
    private var suggestions: List<LaunchableApp>? = null
    private var suggestionsGeneration = 0

    private fun presentSearchPanel(open: Boolean) {
        binding.searchPanelView.beVisibleIf(open)
        if (open) {
            binding.allAppsGridView.beGone()
            binding.allAppsPagerView.beGone()
            binding.allAppsPageDotsView.beGone()
            binding.noResultsPlaceholderView.beGone()
            suggestions = null
            fetchSuggestions()
            paintSearchPanel(launchers, binding.searchBarView.getCurrentQuery())
        } else {
            val paged = isPaged
            binding.allAppsGridView.beVisibleIf(!paged)
            binding.allAppsPagerView.beVisibleIf(paged)
            binding.allAppsPageDotsView.beVisibleIf(paged)
            searchAdapter?.highlight = ""
            submitList(launchers)
        }
    }

    private fun searchGridAdapter(): LaunchablesAdapter {
        searchAdapter?.let { return it }
        val grid = binding.searchAppsGridView
        grid.layoutManager = object : GridLayoutManager(context, SEARCH_COLUMNS) {
            override fun canScrollVertically() = false
        }
        grid.itemAnimator = null
        grid.isNestedScrollingEnabled = false

        val rowGap = resources.getDimensionPixelSize(R.dimen.launcher_search_card_row_gap)
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                outRect.top = if (position >= SEARCH_COLUMNS) rowGap else 0
            }
        })
        return LaunchablesAdapter(activity!!, this, null, onItemClick).also {
            searchAdapter = it
            grid.adapter = it
        }
    }

    private fun fetchSuggestions() {
        val generation = ++suggestionsGeneration
        val installed = launchers
        val pm = context.packageManager
        ensureBackgroundThread {
            val usage = runCatching { context.appUsageDB.getAll() }.getOrDefault(emptyList())
            val facts = HashMap<String, AppRecommender.Facts?>()
            val ranked = AppRecommender.rank(installed, usage, { pkg ->
                facts.getOrPut(pkg) {
                    runCatching {
                        val info = pm.getPackageInfo(pkg, 0)
                        AppRecommender.Facts(info.firstInstallTime, info.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED)
                    }.getOrNull()
                }
            }, System.currentTimeMillis())
            activity?.runOnUiThread {
                if (generation != suggestionsGeneration) return@runOnUiThread
                suggestions = ranked
                if (binding.searchBarView.isSearchOpen && binding.searchBarView.getCurrentQuery().isEmpty()) {
                    paintSearchPanel(launchers, "")
                }
            }
        }
    }

    private fun paintSearchPanel(items: List<LaunchableApp>, query: String) {
        val adapter = searchGridAdapter()
        if (query.isEmpty()) {

            val apps = live(suggestions ?: emptyList(), items)
            binding.searchAppsTitleView.setText(R.string.launcher_suggested_apps)
            adapter.highlight = ""
            adapter.submitList(apps.toMutableList())
            binding.searchAppsCardView.beVisibleIf(apps.size >= AppRecommender.MIN_TO_SHOW)
            binding.searchShortcutsCardView.beGone()
            binding.noResultsPlaceholderView.beGone()
            return
        }
        val needle = query.normalizeString()
        val matches = items.filter { it.title.normalizeString().contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<LaunchableApp> { !it.title.normalizeString().startsWith(needle, ignoreCase = true) }.thenBy { it.title.lowercase() })
        binding.searchAppsTitleView.setText(R.string.launcher_searched_apps)
        adapter.highlight = query
        adapter.submitList(matches.take(SEARCH_MAX_RESULTS).toMutableList())
        binding.searchAppsCardView.beVisibleIf(matches.isNotEmpty())
        binding.noResultsPlaceholderView.beVisibleIf(matches.isEmpty())
        paintShortcuts(matches.firstOrNull())
    }

    private fun paintShortcuts(app: LaunchableApp?) {
        val card = binding.searchShortcutsCardView
        val list = binding.searchShortcutsListView
        list.removeAllViews()
        if (app == null) { card.beGone(); return }
        val launcherApps = context.applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val shortcuts = runCatching {
            if (!launcherApps.hasShortcutHostPermission()) return@runCatching null
            val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(flags).setPackage(app.packageName), Process.myUserHandle())
        }.getOrNull()?.filter { it.isEnabled }?.take(SEARCH_MAX_SHORTCUTS).orEmpty()
        if (shortcuts.isEmpty()) { card.beGone(); return }
        binding.searchShortcutsTitleView.text = app.title
        val inflater = LayoutInflater.from(context)
        val density = resources.displayMetrics.densityDpi
        shortcuts.forEach { shortcut ->
            val row = HdkRowSearchShortcutBinding.inflate(inflater, list, false)
            row.searchShortcutLabelView.text = shortcut.shortLabel ?: shortcut.longLabel ?: ""
            row.searchShortcutIconView.setImageDrawable(runCatching { launcherApps.getShortcutIconDrawable(shortcut, density) }.getOrNull())
            row.root.setOnClickListener {
                runCatching {
                    launcherApps.startShortcut(shortcut.`package`, shortcut.id, null, null, Process.myUserHandle())
                }.onFailure { activity?.showErrorToast(it as? Exception ?: Exception(it)) }
                if (activity?.launcherConfig?.shutAppDrawer == true) activity?.shutAppDrawer(delayed = true)
            }
            list.addView(row.root)
        }
        card.beVisible()
    }

    private fun withAdRow(apps: List<LaunchableApp>): MutableList<LaunchableApp> {
        if (apps.isEmpty()) {
            return apps.toMutableList()
        }

        val spanCount = (binding.allAppsGridView.layoutManager as MyGridLayoutManager).spanCount
        val row = HomeAdsSettings.drawerAdRowPosition()
        val index = (row * spanCount).coerceIn(0, apps.size)
        return apps.toMutableList().apply { add(index, LaunchablesAdapter.AD_SLOT) }
    }
}
