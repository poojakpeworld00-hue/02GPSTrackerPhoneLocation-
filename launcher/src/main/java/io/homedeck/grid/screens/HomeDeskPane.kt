package io.homedeck.grid.screens

import timber.log.Timber
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.app.WallpaperManager.OnColorsChangedListener
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.util.Log
import android.util.Property
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.viewbinding.ViewBinding
import kotlinx.collections.immutable.toImmutableList
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.insetsController
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.DARK_GREY
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isOreoMr1Plus
import io.homedeck.grid.R
import io.homedeck.grid.databinding.HdkScreenMainBinding
import io.homedeck.grid.databinding.HdkAllAppsFragmentBinding
import io.homedeck.grid.databinding.HdkWidgetsFragmentBinding
import io.homedeck.grid.bridge.HomeServiceLocator
import io.homedeck.grid.anim.HomeStripTransitions
import io.homedeck.grid.sheets.RelabelEntrySheet
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.ext.getLabel
import io.homedeck.grid.ext.processGridItemPopupMenu
import io.homedeck.grid.sheets.EntryOptionsSheet
import io.homedeck.grid.ext.hiddenIconsDB
import io.homedeck.grid.ext.homeScreenGridItemsDB
import io.homedeck.grid.ext.isDefaultLauncher
import io.homedeck.grid.ext.fireApp
import io.homedeck.grid.ext.fireAppInfo
import io.homedeck.grid.ext.launchersDB
import io.homedeck.grid.ext.supportsDarkText
import io.homedeck.grid.ext.uninstallApp
import io.homedeck.grid.panes.HomeDeskFragment
import io.homedeck.grid.support.HomeRoleAsker
import io.homedeck.grid.support.ITEM_TYPE_FOLDER
import io.homedeck.grid.support.ITEM_TYPE_ICON
import io.homedeck.grid.support.ITEM_TYPE_SHORTCUT
import io.homedeck.grid.support.ITEM_TYPE_WIDGET
import io.homedeck.grid.support.GlyphMemo
import io.homedeck.grid.support.HomeRoleProbe
import io.homedeck.grid.support.PSEUDO_WIDGET_SEARCH
import io.homedeck.grid.support.REQUEST_ALLOW_BINDING_WIDGET
import io.homedeck.grid.support.REQUEST_CONFIGURE_WIDGET
import io.homedeck.grid.support.REQUEST_CREATE_SHORTCUT
import io.homedeck.grid.support.REQUEST_DEFAULT_SMS
import io.homedeck.grid.support.PACKAGE_REFRESH_DELAY_MS
import io.homedeck.grid.support.UNINSTALL_WATCH_MS
import io.homedeck.grid.support.UNINSTALL_WATCH_STEP_MS
import io.homedeck.grid.support.UNINSTALL_APP_REQUEST_CODE
import io.homedeck.grid.support.SelfUninstall
import io.homedeck.grid.sponsor.HomeAdsSettings
import io.homedeck.grid.sponsor.HomeTutorialStage
import io.homedeck.grid.sponsor.HomeSponsorDriver
import io.homedeck.grid.contracts.SwipeCallback
import io.homedeck.grid.oemprofile.HomeIconsPopulator
import io.homedeck.grid.oemprofile.HomeDefaultsPlanter
import io.homedeck.grid.oemprofile.HomeWidgetPlanter
import io.homedeck.grid.oemprofile.HomeLayoutApplier
import io.homedeck.grid.oemprofile.HomeSignature
import io.homedeck.grid.contracts.EntryMenuCallback
import io.homedeck.grid.entities.LaunchableApp
import io.homedeck.grid.entities.ConcealedApp
import io.homedeck.grid.entities.DeskGridEntry
import io.homedeck.grid.hooks.ScreenLockAdminReceiver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class HomeDeskPane : HomeFoundationPane(), SwipeCallback {

    private var mTouchDownX = -1
    private var mTouchDownY = -1
    private var mAllAppsFragmentY = 0
    private var mWidgetsFragmentY = 0
    private var mScreenHeight = 0
    private var mScreenWidth = 0
    private var mMoveGestureThreshold = 0
    private var mIgnoreUpEvent = false
    private var mIgnoreMoveEvents = false
    private var mIgnoreXMoveEvents = false
    private var mIgnoreYMoveEvents = false

    private var mGestureDownX = 0f
    private var mGestureMinX = 0f
    private var mGestureMaxX = 0f
    private val mTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val mQuickSwipeSpeed by lazy { 0.4f * resources.displayMetrics.density }

    private var mDrawerEnabled = true
    private var mLongPressedIcon: DeskGridEntry? = null
    private var mOpenPopupMenu: EntryOptionsSheet? = null
    private var mLastTouchCoords = Pair(-1f, -1f)
    private var mActionOnCanBindWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnWidgetConfiguredWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnAddShortcut:
            ((shortcutId: String, label: String, icon: Drawable) -> Unit)? = null
    private var mActionOnDefaultSmsResult: (() -> Unit)? = null
    private var wasJustPaused: Boolean = false

    private var mSwipeHintAnimator: ObjectAnimator? = null

    private var needsDefaultLauncherBanner = false

    private var mDefaultLauncherPromptEnabled = true

    private var mOpenSidePanel: View? = null

    private fun shutDisabledPanels() {
        if (isLeftPanelExpanded() && !HomeAdsSettings.panelEnabled(HomeAdsSettings.LEFT_SWIPE)) {
            concealLeftPanel()
        }
        if (isHostPanelExpanded() && !HomeAdsSettings.panelEnabled(HomeAdsSettings.RIGHT_SWIPE)) {
            concealHostPanel()
        }
    }

    private fun onConfigUpdated() {
        if (isFinishing || isDestroyed) {
            return
        }

        renewDefaultLauncherBanner()
        shutDisabledPanels()

        if (!isAllAppsFragmentExpanded() && GlyphMemo.launchers.isNotEmpty()) {

            binding.allAppsFragmentView.root.gotLaunchers(GlyphMemo.launchers, force = true)
        }

    }

    private val defaultLauncherRequester by lazy {

        HomeRoleAsker(this) { renewDefaultLauncherBanner() }
    }

    private var wallpaperColorChangeListener: OnColorsChangedListener? = null
    private var wallpaperSupportsDarkText: Boolean? = null

    private lateinit var mDetector: GestureDetectorCompat
    private val binding by viewBinding(HdkScreenMainBinding::inflate)

    companion object {

        const val EXTRA_OPEN_HOST_PANEL = "open_host_panel"

        private const val DOCK_HOST_SLOT = 1
        private const val PLAY_STORE_PACKAGE = "com.android.vending"

        private var mLastUpEvent = 0L
        private const val ANIMATION_DURATION = 150L
        private const val APP_DRAWER_CLOSE_DELAY = 300L
        private const val APP_DRAWER_STATE = "app_drawer_state"
        private const val SWIPE_HINT_ANIMATION_DURATION = 900L

        private const val PERMISSION_SHEET_DELAY = 500L

        private const val HOST_PANEL_WARM_UP_DELAY = 250L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false

        super.onCreate(savedInstanceState)

        if (!HomeServiceLocator.bridge.onLauncherStart(this)) {
            return
        }

        HomeLayoutApplier.imprintStyle(this)
        HomeLayoutApplier.renewIconSizes(this)
        mDrawerEnabled = launcherConfig.isDrawerEnabled

        setContentView(binding.root)

        getSystemService(LauncherApps::class.java)?.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        appLaunched(packageName)
        setupEdgeToEdge(
            padTopSystem = listOf(
                binding.allAppsFragmentView.root,
                binding.widgetsFragmentView.root,
                binding.leftPanelView.root,

                binding.defaultLauncherBannerView.root
            ),
            padBottomImeAndSystem = listOf(
                binding.allAppsFragmentView.allAppsGridView,
                binding.allAppsFragmentView.allAppsPageDotsView,
                binding.widgetsFragmentView.widgetsListView,
                binding.leftPanelView.panelScrollView,

                binding.hostPanelView.hostPanelContainerView
            ),

            padBottomSystem = listOf(
                binding.homeScreenGridView.root,
                binding.leftPanelView.panelNativeFrameView
            )
        )

        mDetector = GestureDetectorCompat(this, DeskSwipeDetector(this))

        mScreenHeight = realScreenSize.y
        mScreenWidth = realScreenSize.x
        mAllAppsFragmentY = mScreenHeight
        mWidgetsFragmentY = mScreenHeight
        mMoveGestureThreshold = resources.getDimensionPixelSize(R.dimen.launcher_move_gesture_threshold)

        arrayOf(
            binding.allAppsFragmentView.root as HomeDeskFragment<*>,
            binding.widgetsFragmentView.root as HomeDeskFragment<*>
        ).forEach { fragment ->
            fragment.setupFragment(this)
            fragment.y = mScreenHeight.toFloat()
            fragment.beVisible()
        }

        binding.leftPanelView.root.apply {
            setupFragment(this@HomeDeskPane)
            x = mScreenWidth.toFloat()
            beVisible()
        }

        binding.hostPanelView.root.apply {
            setupFragment(this@HomeDeskPane)
            x = -mScreenWidth.toFloat()
            beVisible()
        }

        processIntentAction(intent)

        binding.homeScreenGridView.root.itemClickListener = {
            performItemClick(it)
        }

        binding.homeScreenGridView.root.itemLongClickListener = {
            performItemLongClick(
                x = binding.homeScreenGridView.root.getClickableRect(it).left.toFloat(),
                clickedGridItem = it
            )
        }

        prepareDefaultLauncherBanner(savedInstanceState)
        prepareWallpaperColorListener()

        if (savedInstanceState == null) unfoldHostPanelIfAsked(intent)

        binding.mainHolderView.post { perhapsShowGuide() }

        binding.mainHolderView.postDelayed({
            Looper.myQueue().addIdleHandler {

                if (!isFinishing && !isDestroyed &&
                    HomeAdsSettings.panelEnabled(HomeAdsSettings.RIGHT_SWIPE)
                ) binding.hostPanelView.root.warmUp()
                false
            }
        }, HOST_PANEL_WARM_UP_DELAY)
    }

    private fun prepareDefaultLauncherBanner(savedInstanceState: Bundle?) {

        defaultLauncherRequester.restoreState(savedInstanceState)
        binding.defaultLauncherBannerView.root.setOnClickListener {
            HomeServiceLocator.bridge.onEvent("default_home_card_tap")
            defaultLauncherRequester.start()
        }

        binding.homeScreenGridView.root.onFirstPageScrolled = { x ->
            binding.defaultLauncherBannerView.root.translationX = x
        }
        HomeStripTransitions.bind(binding.defaultLauncherBannerView)

        binding.defaultLauncherBannerView.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncGridTopReserve()
        }
        renewDefaultLauncherBanner()
    }

    private fun syncGridTopReserve() {
        val banner = binding.defaultLauncherBannerView.root
        val wanted = needsDefaultLauncherBanner && mDefaultLauncherPromptEnabled
        val grid = binding.homeScreenGridView.root
        grid.topReserve = when {
            !wanted -> 0

            banner.height > 0 -> (banner.height - banner.paddingTop).coerceAtLeast(0)
            else -> grid.topReserve
        }
    }

    private fun renewDefaultLauncherBanner() {
        val needs = !isDefaultLauncher()

        if (needsDefaultLauncherBanner && !needs) HomeServiceLocator.bridge.onEvent("default_home_granted_later")
        needsDefaultLauncherBanner = needs

        mDefaultLauncherPromptEnabled = HomeAdsSettings.defaultLauncherPromptEnabled()
        updateDefaultLauncherBannerVisibility()
        syncGridTopReserve()
    }

    private fun updateDefaultLauncherBannerVisibility() {
        binding.defaultLauncherBannerView.root.beVisibleIf(
            needsDefaultLauncherBanner &&
                mDefaultLauncherPromptEnabled &&
                !isAllAppsFragmentExpanded() &&
                !isWidgetsFragmentExpanded() &&
                mOpenSidePanel == null &&
                !binding.swipeHintView.isVisible
        )
    }

    private var mGuideStep: HomeTutorialStage? = null

    private fun nextGuideStep(): HomeTutorialStage? = HomeTutorialStage.entries.firstOrNull {
        HomeAdsSettings.guideStepEnabled(it) && !launcherConfig.isGuideStepDone(it) &&

                (it != HomeTutorialStage.SWIPE_UP_DRAWER || mDrawerEnabled)
    }

    private fun perhapsShowGuide() {

        if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()) return
        if (isLeftPanelExpanded() || isHostPanelExpanded()) return

        val step = nextGuideStep()
        if (step == null) {
            hideSwipeHint()
        } else if (mGuideStep != step || !binding.swipeHintView.isVisible) {
            presentGuideStep(step)
        }

        updateDefaultLauncherBannerVisibility()
    }

    private fun presentGuideStep(step: HomeTutorialStage) {
        cancelGuideAnimation()
        mGuideStep = step

        val chevrons = listOf(
            binding.swipeHintChevron1View,
            binding.swipeHintChevron2View,
            binding.swipeHintChevron3View,
        )
        val travel = resources.getDimension(R.dimen.launcher_swipe_hint_travel)

        val icon: Int
        val label: Int
        val property: Property<View, Float>
        val distance: Float
        val vertical: Boolean
        when (step) {
            HomeTutorialStage.SWIPE_RIGHT_HOST -> {
                icon = org.fossify.commons.R.drawable.ic_chevron_right_vector
                label = R.string.launcher_swipe_right_hint
                property = View.TRANSLATION_X
                distance = travel
                vertical = false
            }

            HomeTutorialStage.SWIPE_LEFT_APPS -> {
                icon = org.fossify.commons.R.drawable.ic_chevron_left_vector
                label = R.string.launcher_swipe_left_hint
                property = View.TRANSLATION_X
                distance = -travel
                vertical = false
            }

            HomeTutorialStage.SWIPE_UP_DRAWER -> {
                icon = org.fossify.commons.R.drawable.ic_chevron_up_vector
                label = R.string.launcher_swipe_up_hint
                property = View.TRANSLATION_Y
                distance = -travel
                vertical = true
            }
        }

        binding.swipeHintChevronsView.orientation =
            if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        val alphas = if (distance < 0f) listOf(1f, 0.6f, 0.3f) else listOf(0.3f, 0.6f, 1f)
        chevrons.forEachIndexed { index, view ->
            view.setImageResource(icon)
            view.alpha = alphas[index]

            view.translationX = 0f
            view.translationY = 0f
        }
        binding.swipeHintChevronsView.translationX = 0f
        binding.swipeHintChevronsView.translationY = 0f
        binding.swipeHintLabelView.setText(label)
        binding.swipeHintView.beVisible()

        mSwipeHintAnimator = ObjectAnimator.ofFloat(
            binding.swipeHintChevronsView,
            property,
            0f,
            distance,
        ).apply {
            duration = SWIPE_HINT_ANIMATION_DURATION
            repeatCount = ValueAnimator.INFINITE

            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun completeGuideStep(step: HomeTutorialStage) {
        if (launcherConfig.isGuideStepDone(step)) return
        launcherConfig.markGuideStepDone(step)
        if (mGuideStep == step) {
            hideSwipeHint()
        }
    }

    private fun performGuideStep(): Boolean {
        val step = mGuideStep ?: return false
        if (!binding.swipeHintView.isVisible) return false

        hideSwipeHint()

        when (step) {
            HomeTutorialStage.SWIPE_RIGHT_HOST -> presentHostPanel()
            HomeTutorialStage.SWIPE_LEFT_APPS -> presentLeftPanel()
            HomeTutorialStage.SWIPE_UP_DRAWER -> presentFragment(binding.allAppsFragmentView)
        }
        return true
    }

    private fun cancelGuideAnimation() {
        mSwipeHintAnimator?.cancel()
        mSwipeHintAnimator = null
    }

    private fun hideSwipeHint() {
        if (!binding.swipeHintView.isVisible) {
            return
        }

        cancelGuideAnimation()
        binding.swipeHintView.beGone()
    }

    private fun prepareWallpaperColorListener() {
        if (isOreoMr1Plus()) {
            val wallpaperManager = WallpaperManager.getInstance(this)
            wallpaperColorChangeListener = OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    wallpaperSupportsDarkText = colors?.wantsDarkText() ?: run {
                        renewWallpaperSupportsDarkText()
                        wallpaperSupportsDarkText
                    }

                    if (currentSurfaceColor() == null) {
                        runOnUiThread {
                            updateStatusBarIcons()
                        }
                    }
                }
            }
            wallpaperManager.addOnColorsChangedListener(
                wallpaperColorChangeListener!!,
                Handler(Looper.getMainLooper())
            )

            renewWallpaperSupportsDarkText()
        }
    }

    private fun renewWallpaperSupportsDarkText() {
        if (!isOreoMr1Plus()) return
        wallpaperSupportsDarkText = WallpaperManager.getInstance(this)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.wantsDarkText()
    }

    private fun isNightTheme(): Boolean =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun android.app.WallpaperColors.wantsDarkText(): Boolean = supportsDarkText()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        retireItemActions()

        val wasAnyFragmentOpen = isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()
        val wasAnyPanelOpen = isLeftPanelExpanded() || isHostPanelExpanded()
        if (wasJustPaused) {
            if (isAllAppsFragmentExpanded()) {
                concealFragment(binding.allAppsFragmentView)
            }
            if (isWidgetsFragmentExpanded()) {
                concealFragment(binding.widgetsFragmentView)
            }
        } else {
            shutAppDrawer()
            shutWidgetsFragment()
        }

        if (isLeftPanelExpanded()) {
            concealLeftPanel()
        }

        if (isHostPanelExpanded()) {
            concealHostPanel()
        }

        binding.allAppsFragmentView.searchBarView.closeSearch()

        val alreadyOnHome = intent.flags and FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyOnHome && !wasAnyFragmentOpen) {
            binding.homeScreenGridView.root.skipToPage(0)
        }

        if (alreadyOnHome && !wasAnyFragmentOpen && !wasAnyPanelOpen && intent.hasCategory(Intent.CATEGORY_HOME)) {
            HomeSponsorDriver.run(this, HomeAdsSettings.HOME_PRESS) {}
        }

        processIntentAction(intent)
        unfoldHostPanelIfAsked(intent)
    }

    private fun unfoldHostPanelIfAsked(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_HOST_PANEL, false)) return
        intent.removeExtra(EXTRA_OPEN_HOST_PANEL)
        if (!HomeAdsSettings.panelEnabled(HomeAdsSettings.RIGHT_SWIPE)) return
        binding.mainHolderView.post {
            if (isFinishing || isDestroyed || isHostPanelExpanded()) return@post
            revealHostPanel()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.homeScreenGridView.root.appWidgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()

        if (isWorkspaceCovered()) {
            binding.homeScreenGridView.root.animate().cancel()
            binding.homeScreenGridView.root.alpha = 0f
        }

        if (reapplyLauncherStyle()) return
        wasJustPaused = false
        mDrawerEnabled = launcherConfig.isDrawerEnabled

        HomeServiceLocator.bridge.onLauncherResume(this)

        defaultLauncherRequester.onResume()
        renewWallpaperSupportsDarkText()

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatusBarIcons(currentSurfaceColor())

            perhapsShowGuide()
        }, ANIMATION_DURATION)

        with(binding.mainHolderView) {
            onGlobalLayout {
                binding.allAppsFragmentView.root.prepareViews()
                binding.widgetsFragmentView.root.prepareViews()
            }
        }

        ensureBackgroundThread {
            if (GlyphMemo.launchers.isEmpty()) {
                val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
                    it.getIconIdentifier()
                }

                GlyphMemo.launchers = launchersDB.getAppLaunchers().filter {
                    val showIcon = !hiddenIcons.contains(it.getLauncherIdentifier())
                    if (!showIcon) {
                        try {
                            launchersDB.deleteById(it.id!!)
                        } catch (_: Exception) {
                        }
                    }
                    showIcon
                }.toMutableList() as ArrayList<LaunchableApp>

                GlyphMemo.installedSignature = null
            }

            binding.allAppsFragmentView.root.gotLaunchers(GlyphMemo.launchers)
            binding.leftPanelView.root.gotLaunchers(GlyphMemo.launchers)
            renewLaunchers()
        }

        renewDefaultLauncherBanner()
        syncHostDockItem()

        binding.homeScreenGridView.root.resizeGrid(
            newRowCount = launcherConfig.homeRowCount,
            newColumnCount = launcherConfig.homeColumnCount
        )
        binding.homeScreenGridView.root.updateColors()
        binding.allAppsFragmentView.root.onResume()
    }

    override fun onStop() {
        super.onStop()
        try {
            binding.homeScreenGridView.root.appWidgetHost.stopListening()
        } catch (_: Exception) {
        }

        wasJustPaused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(LauncherApps::class.java)?.unregisterCallback(packageCallback)
        packageRefreshHandler.removeCallbacksAndMessages(null)
        if (isOreoMr1Plus() && wallpaperColorChangeListener != null) {
            WallpaperManager.getInstance(this)
                .removeOnColorsChangedListener(wallpaperColorChangeListener!!)
        }
    }

    override fun onPause() {
        super.onPause()
        wasJustPaused = true

        retireItemActions()
    }

    private fun retireItemActions() {
        mOpenPopupMenu?.dismiss()
        mOpenPopupMenu = null
    }

    override fun onBackPressedCompat(): Boolean {
        return if (isLeftPanelExpanded()) {
            concealLeftPanel()
            true
        } else if (isHostPanelExpanded()) {

            if (!binding.hostPanelView.root.handleBack()) concealHostPanel()
            true
        } else if (isAllAppsFragmentExpanded()) {
            binding.allAppsFragmentView.root.resetSearch()
            concealFragment(binding.allAppsFragmentView)
            true
        } else if (isWidgetsFragmentExpanded()) {
            if (binding.widgetsFragmentView.searchBarView.isSearchOpen) {
                wipeWidgetsSearch()
            } else {
                concealFragment(binding.widgetsFragmentView)
            }
            true
        } else if (binding.homeScreenGridView.resizeFrameView.isVisible) {
            binding.homeScreenGridView.root.concealResizeLines()
            true
        } else {

            HomeSponsorDriver.run(this, HomeAdsSettings.HOME_BACK) {}
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        when (requestCode) {

            UNINSTALL_APP_REQUEST_CODE -> Unit

            REQUEST_DEFAULT_SMS -> {
                mActionOnDefaultSmsResult?.invoke()
                mActionOnDefaultSmsResult = null
            }

            REQUEST_ALLOW_BINDING_WIDGET -> mActionOnCanBindWidget?.invoke(resultCode == RESULT_OK)
            REQUEST_CONFIGURE_WIDGET -> mActionOnWidgetConfiguredWidget?.invoke(resultCode == RESULT_OK)
            REQUEST_CREATE_SHORTCUT -> {
                if (resultCode == RESULT_OK && resultData != null) {
                    val launcherApps =
                        applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                    if (launcherApps.hasShortcutHostPermission()) {
                        val item = launcherApps.getPinItemRequest(resultData)
                        val shortcutInfo = item?.shortcutInfo ?: return
                        if (item.accept()) {
                            val shortcutId = shortcutInfo.id
                            val label = shortcutInfo.getLabel()
                            val icon = launcherApps.getShortcutBadgedIconDrawable(
                                shortcutInfo,
                                resources.displayMetrics.densityDpi
                            )
                            mActionOnAddShortcut?.invoke(shortcutId, label, icon)
                        }
                    }
                }
            }
        }
    }

    private fun quickSwipeDirection(up: MotionEvent): Boolean? {
        val travel = up.x - mGestureDownX
        val duration = (up.eventTime - up.downTime).coerceAtLeast(1L)
        if (abs(travel) <= 2 * mTouchSlop || abs(travel) / duration < mQuickSwipeSpeed) return null
        val retreat = if (travel < 0) up.x - mGestureMinX else mGestureMaxX - up.x
        if (retreat > mTouchSlop) return null
        return travel < 0
    }

    private fun resetGestureRange(x: Float) {
        mGestureDownX = x
        mGestureMinX = x
        mGestureMaxX = x
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.allAppsFragmentView.root.onConfigurationChanged()
        binding.widgetsFragmentView.root.onConfigurationChanged()

        updateStatusBarIcons(currentSurfaceColor())
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        if (mLongPressedIcon != null && event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            mLastUpEvent = System.currentTimeMillis()
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resetGestureRange(event.x)
        } else {
            for (i in 0 until event.historySize) {
                mGestureMinX = min(mGestureMinX, event.getHistoricalX(i))
                mGestureMaxX = max(mGestureMaxX, event.getHistoricalX(i))
            }
            mGestureMinX = min(mGestureMinX, event.x)
            mGestureMaxX = max(mGestureMaxX, event.x)
        }

        try {
            mDetector.onTouchEvent(event)
        } catch (e: Exception) {

            Timber.e(e, "HomeDeskPane: gesture handler failed")
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x.toInt()
                mTouchDownY = event.y.toInt()
                mAllAppsFragmentY = binding.allAppsFragmentView.root.y.toInt()
                mWidgetsFragmentY = binding.widgetsFragmentView.root.y.toInt()
                mIgnoreUpEvent = false
            }

            MotionEvent.ACTION_MOVE -> {

                val hasFingerMoved = if (mTouchDownX == -1 || mTouchDownY == -1) {
                    mTouchDownX = event.x.toInt()
                    mTouchDownY = event.y.toInt()
                    resetGestureRange(event.x)
                    false
                } else {
                    hasFingerMoved(event)
                }

                if (mLongPressedIcon != null && (mOpenPopupMenu != null) && hasFingerMoved) {
                    mOpenPopupMenu?.dismiss()
                    mOpenPopupMenu = null
                    binding.homeScreenGridView.root.itemDraggingStarted(mLongPressedIcon!!)
                    concealFragment(binding.allAppsFragmentView)
                }

                if (mLongPressedIcon != null && hasFingerMoved) {
                    binding.homeScreenGridView.root.draggedItemMoved(event.x.toInt(), event.y.toInt())
                }

                if (hasFingerMoved && !mIgnoreMoveEvents) {

                    hideSwipeHint()

                    val diffY = mTouchDownY - event.y
                    val diffX = mTouchDownX - event.x

                    if (abs(diffY) > abs(diffX) && !mIgnoreYMoveEvents) {
                        mIgnoreXMoveEvents = true
                        if (isWidgetsFragmentExpanded()) {
                            val newY = mWidgetsFragmentY - diffY
                            binding.widgetsFragmentView.root.y = min(
                                a = max(0f, newY), b = mScreenHeight.toFloat()
                            )
                        } else if (mLongPressedIcon == null && mDrawerEnabled) {
                            val newY = mAllAppsFragmentY - diffY
                            binding.allAppsFragmentView.root.y = min(
                                a = max(0f, newY), b = mScreenHeight.toFloat()
                            )
                        }

                        updateDefaultLauncherBannerVisibility()
                    } else if (abs(diffX) > abs(diffY) && !mIgnoreXMoveEvents) {
                        mIgnoreYMoveEvents = true
                        binding.homeScreenGridView.root.setSwipeMovement(diffX)
                    }
                }

                mLastTouchCoords = Pair(event.x, event.y)
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                mTouchDownX = -1
                mTouchDownY = -1
                mIgnoreMoveEvents = false
                mLongPressedIcon = null
                mLastTouchCoords = Pair(-1f, -1f)
                resetFragmentTouches()
                binding.homeScreenGridView.root.itemDraggingStopped()

                if (!mIgnoreUpEvent) {
                    if (!mIgnoreYMoveEvents) {
                        if (binding.allAppsFragmentView.root.y < mScreenHeight * 0.5) {

                            presentFragment(
                                fragment = binding.allAppsFragmentView,
                                renewAds = mAllAppsFragmentY == mScreenHeight
                            )
                        } else if (isAllAppsFragmentExpanded()) {
                            concealFragment(binding.allAppsFragmentView)
                        }

                        if (binding.widgetsFragmentView.root.y < mScreenHeight * 0.5) {
                            presentFragment(binding.widgetsFragmentView)
                        } else if (isWidgetsFragmentExpanded()) {
                            concealFragment(binding.widgetsFragmentView)
                        }
                    }

                }

                if (!mIgnoreXMoveEvents) {
                    val grid = binding.homeScreenGridView.root

                    if (!mIgnoreUpEvent && mIgnoreYMoveEvents && event.actionMasked == MotionEvent.ACTION_UP) {
                        quickSwipeDirection(event)?.let { grid.flingSwipe(towardsNext = it) }
                    }
                    grid.finalizeSwipe()
                }

                mIgnoreXMoveEvents = false
                mIgnoreYMoveEvents = false
            }
        }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(APP_DRAWER_STATE, isAllAppsFragmentExpanded())
        defaultLauncherRequester.persistState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState.getBoolean(APP_DRAWER_STATE)) {
            presentFragment(binding.allAppsFragmentView, 0L)
        }
    }

    private fun processIntentAction(intent: Intent) {
        if (intent.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            val launcherApps =
                applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val item = launcherApps.getPinItemRequest(intent)
            val shortcutInfo = item?.shortcutInfo ?: return

            ensureBackgroundThread {
                val shortcutId = shortcutInfo.id
                val label = shortcutInfo.getLabel()
                val icon = launcherApps.getShortcutBadgedIconDrawable(
                    shortcutInfo,
                    resources.displayMetrics.densityDpi
                )
                val (page, rect) = locateFirstEmptyCell()
                val gridItem = DeskGridEntry(
                    id = null,
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                    page = page,
                    packageName = shortcutInfo.`package`,
                    activityName = "",
                    title = label,
                    type = ITEM_TYPE_SHORTCUT,
                    className = "",
                    widgetId = -1,
                    shortcutId = shortcutId,
                    icon = icon.toBitmap(),
                    docked = false,
                    parentId = null,
                    drawable = icon
                )

                runOnUiThread {
                    binding.homeScreenGridView.root.skipToPage(page)
                }

                Thread.sleep(2000)

                try {
                    item.accept()
                    binding.homeScreenGridView.root.storeAndShowGridItem(gridItem)
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    private fun locateFirstEmptyCell(): Pair<Int, Rect> {
        val gridItems = homeScreenGridItemsDB.getAllItems() as ArrayList<DeskGridEntry>
        val maxPage = gridItems.maxOf { it.page }
        val occupiedCells = ArrayList<Triple<Int, Int, Int>>()
        gridItems.toImmutableList().filter { it.parentId == null }.forEach { item ->
            for (xCell in item.left..item.right) {
                for (yCell in item.top..item.bottom) {
                    occupiedCells.add(Triple(item.page, xCell, yCell))
                }
            }
        }

        for (page in 0 until maxPage) {
            for (checkedYCell in 0 until launcherConfig.homeColumnCount) {
                for (checkedXCell in 0 until launcherConfig.homeRowCount - 1) {
                    val wantedCell = Triple(page, checkedXCell, checkedYCell)
                    if (!occupiedCells.contains(wantedCell)) {
                        return Pair(
                            first = page,
                            second = Rect(
                                wantedCell.second,
                                wantedCell.third,
                                wantedCell.second,
                                wantedCell.third
                            )
                        )
                    }
                }
            }
        }

        return Pair(maxPage + 1, Rect(0, 0, 0, 0))
    }

    private fun hasFingerMoved(event: MotionEvent): Boolean {
        return mTouchDownX != -1 && mTouchDownY != -1 &&
                (abs(mTouchDownX - event.x) > mMoveGestureThreshold || abs(mTouchDownY - event.y) > mMoveGestureThreshold)
    }

    private val packageRefreshHandler = Handler(Looper.getMainLooper())

    fun dropSelfFromHome() {
        HomeServiceLocator.bridge.onEvent("uninstall_flow_self_hidden", mapOf("trigger" to "shortcut_in_place"))

        launcherConfig.selfIconHidden = true
        ensureBackgroundThread {
            SelfUninstall.hide(this)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) binding.homeScreenGridView.root.grabGridItems()
            }
        }
    }

    fun watchUninstall(packageName: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + UNINSTALL_WATCH_MS
        val check = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                val gone = runCatching { packageManager.getPackageInfo(packageName, 0) }.isFailure
                when {
                    gone -> {
                        Timber.d("HomeDeskPane: $packageName uninstalled - refreshing")
                        packageRefreshHandler.removeCallbacks(packageRefresh)
                        packageRefresh.run()
                    }
                    android.os.SystemClock.elapsedRealtime() < deadline ->
                        packageRefreshHandler.postDelayed(this, UNINSTALL_WATCH_STEP_MS)
                }
            }
        }
        packageRefreshHandler.postDelayed(check, UNINSTALL_WATCH_STEP_MS)
    }

    private val packageRefresh = Runnable {
        if (!isFinishing && !isDestroyed) ensureBackgroundThread { renewLaunchers() }
    }

    private val packageCallback = object : LauncherApps.Callback() {
        private fun changed(packageName: String?) {
            Timber.d("HomeDeskPane: package changed - $packageName")
            packageRefreshHandler.removeCallbacks(packageRefresh)
            packageRefreshHandler.postDelayed(packageRefresh, PACKAGE_REFRESH_DELAY_MS)
        }

        override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) = changed(packageName)
        override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) = changed(packageName)
        override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) =
            changed(packageNames?.joinToString())
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) =
            changed(packageNames?.joinToString())
    }

    private val refreshLock = Any()

    private fun isInstalledAndEnabled(packageName: String): Boolean =
        runCatching { packageManager.getApplicationInfo(packageName, 0).enabled }.getOrDefault(false)

    private fun renewLaunchers(): Unit = synchronized(refreshLock) {

        val dockEmpty = homeScreenGridItemsDB.getAllItems().none { it.docked }
        if (!launcherConfig.wasHomeScreenInit || dockEmpty) {
            getDefaultAppPackages()
            launcherConfig.wasHomeScreenInit = true
            launcherConfig.wasSearchBarPurged = true
            binding.homeScreenGridView.root.grabGridItems()
        }

        if (!launcherConfig.homeSeeded && !launcherConfig.homeRebuildPending) {
            val names = HomeRoleProbe.quick(this)
            HomeDefaultsPlanter.seedFirstPage(this, names)
            if (!launcherConfig.isDrawerEnabled || launcherConfig.homeAndDrawer) {
                HomeIconsPopulator.placeMissing(this, names)
            }
            binding.homeScreenGridView.root.grabGridItems()
        }

        val knownSignature = GlyphMemo.installedSignature
        val launchers = getAllAppLaunchers()

        val appsChanged = knownSignature == null || knownSignature != GlyphMemo.installedSignature
        binding.allAppsFragmentView.root.gotLaunchers(launchers)
        binding.leftPanelView.root.gotLaunchers(launchers)
        if (appsChanged || !binding.widgetsFragmentView.root.hasWidgets()) {
            binding.widgetsFragmentView.root.getAppWidgets()
        }

        GlyphMemo.launchers.map { it.packageName }.forEach { packageName ->
            if (!launchers.map { it.packageName }.contains(packageName) && !isInstalledAndEnabled(packageName)) {
                launchersDB.deleteApp(packageName)
                homeScreenGridItemsDB.deleteByPackageName(packageName)
            }
        }

        GlyphMemo.launchers = launchers

        if (launcherConfig.homeRebuildPending) {
            HomeDefaultsPlanter.resetForRebuild(this)
            launcherConfig.homeRebuildPending = false
        }
        HomeDefaultsPlanter.seedFirstPage(this, launchers)

        if (HomeWidgetPlanter.syncFirstPage(this)) {
            runOnUiThread { if (!isFinishing && !isDestroyed) recreate() }
            return
        }
        if (!launcherConfig.isDrawerEnabled || launcherConfig.homeAndDrawer) {
            HomeIconsPopulator.placeMissing(this, launchers)
        }
        HomeDefaultsPlanter.placeNewInstalls(this, launchers)

        if (!launcherConfig.wasSearchBarPurged) {
            ensureBackgroundThread {
                purgeSeededSearchBarIfNeeded()

                binding.homeScreenGridView.root.dropPlacedSearchBars()
                binding.homeScreenGridView.root.grabGridItems()
            }
        } else {
            binding.homeScreenGridView.root.grabGridItems()
        }
    }

    fun reapplyLauncherStyle(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (!HomeLayoutApplier.imprintStyle(this)) return false
        recreate()
        return true
    }

    fun isAllAppsFragmentExpanded() = binding.allAppsFragmentView.root.y != mScreenHeight.toFloat()

    private fun isWidgetsFragmentExpanded() =
        binding.widgetsFragmentView.root.y != mScreenHeight.toFloat()

    fun isLeftPanelExpanded() = binding.leftPanelView.root.x != mScreenWidth.toFloat()

    fun isHostPanelExpanded() = binding.hostPanelView.root.x != -mScreenWidth.toFloat()

    private fun presentLeftPanel() {

        if (!HomeAdsSettings.panelEnabled(HomeAdsSettings.LEFT_SWIPE)) {
            Timber.d("HomeDeskPane: apps panel disabled by launcher_config")
            return
        }
        HomeSponsorDriver.run(this, HomeAdsSettings.LEFT_SWIPE) {

            binding.leftPanelView.root.renewAds()
            binding.leftPanelView.root.onOpened()
            presentSidePanel(binding.leftPanelView.root)

            completeGuideStep(HomeTutorialStage.SWIPE_LEFT_APPS)
        }
    }

    fun concealLeftPanel() {
        concealSidePanel(binding.leftPanelView.root, mScreenWidth.toFloat())

        Handler(Looper.getMainLooper()).postDelayed({
            binding.leftPanelView.root.resetSearch()
        }, ANIMATION_DURATION)
    }

    fun unfoldHostApp(fallbackActivity: String = "") {
        if (isAllAppsFragmentExpanded()) shutAppDrawer()
        if (isLeftPanelExpanded()) concealLeftPanel()
        if (HomeAdsSettings.panelEnabled(HomeAdsSettings.RIGHT_SWIPE)) {
            presentHostPanel()
        } else {
            fireApp(packageName, hostActivityName().ifEmpty { fallbackActivity })
        }
    }

    private fun presentHostPanel() {

        if (!HomeAdsSettings.panelEnabled(HomeAdsSettings.RIGHT_SWIPE)) {
            Timber.d("HomeDeskPane: messages panel disabled by launcher_config")
            return
        }
        HomeSponsorDriver.run(this, HomeAdsSettings.RIGHT_SWIPE) {

            revealHostPanel()
            completeGuideStep(HomeTutorialStage.SWIPE_RIGHT_HOST)
        }
    }

    private fun revealHostPanel() {
        binding.hostPanelView.root.refresh()
        presentSidePanel(binding.hostPanelView.root)

        binding.hostPanelView.root.setPanelVisible(true)
    }

    fun concealHostPanel() {

        binding.hostPanelView.root.setPanelVisible(false)
        concealSidePanel(binding.hostPanelView.root, -mScreenWidth.toFloat())
    }

    fun shutHostPanel() {
        if (isHostPanelExpanded()) {
            concealHostPanel()
        }
    }

    private fun presentSidePanel(panel: View) {
        hideSwipeHint()
        mOpenSidePanel = panel
        tweenSidePanelTo(panel, 0f)

        updateDefaultLauncherBannerVisibility()
        window.navigationBarColor = resources.getColor(R.color.launcher_semitransparent_navigation)
        binding.homeScreenGridView.root.fragmentExpanded()
        binding.homeScreenGridView.root.concealResizeLines()
        binding.homeScreenGridView.root.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .start()

        @SuppressLint("AccessibilityFocus")
        panel.performAccessibilityAction(
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )

        updateStatusBarIcons(sidePanelBackgroundColor(panel))
    }

    private fun concealSidePanel(panel: View, parkedX: Float) {

        tweenSidePanelTo(panel, parkedX) {
            mOpenSidePanel = null
            perhapsShowGuide()

            updateDefaultLauncherBannerVisibility()
        }
        window.navigationBarColor = Color.TRANSPARENT
        binding.homeScreenGridView.root.fragmentCollapsed()
        updateStatusBarIcons()
        hideKeyboard()
    }

    private fun tweenSidePanelTo(panel: View, x: Float, onEnd: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(panel, "x", x).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            if (onEnd != null) {
                addListener(endListener(onEnd))
            }
            start()
        }
    }

    private fun endListener(action: () -> Unit) = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = action()
    }

    fun kickHandlingTouches(touchDownY: Int) {
        mLongPressedIcon = null
        mTouchDownY = touchDownY
        mAllAppsFragmentY = binding.allAppsFragmentView.root.y.toInt()
        mWidgetsFragmentY = binding.widgetsFragmentView.root.y.toInt()
        mIgnoreUpEvent = false
    }

    private fun presentFragment(
        fragment: ViewBinding,
        animationDuration: Long = ANIMATION_DURATION,
        renewAds: Boolean = true,
    ) {
        ObjectAnimator.ofFloat(fragment.root, "y", 0f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()

            addListener(endListener { updateDefaultLauncherBannerVisibility() })
            start()
        }

        window.navigationBarColor = resources.getColor(R.color.launcher_semitransparent_navigation)
        binding.homeScreenGridView.root.fragmentExpanded()
        binding.homeScreenGridView.root.concealResizeLines()

        hideSwipeHint()

        updateDefaultLauncherBannerVisibility()
        if (fragment is HdkAllAppsFragmentBinding) {

            completeGuideStep(HomeTutorialStage.SWIPE_UP_DRAWER)

            if (renewAds) {
                fragment.root.renewAds()
            }
        }

        @SuppressLint("AccessibilityFocus")
        fragment.root.performAccessibilityAction(
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )

        if (
            fragment is HdkAllAppsFragmentBinding
            && launcherConfig.showSearchBar
            && launcherConfig.autoShowKeyboardInAppDrawer
        ) {
            fragment.root.post {
                showKeyboard(fragment.searchBarView.binding.topToolbarSearch)
            }
        }

        if (animationDuration == 0L) {

            binding.homeScreenGridView.root.animate().cancel()
            binding.homeScreenGridView.root.alpha = 0f
        } else {
            binding.homeScreenGridView.root.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .start()
        }

        updateStatusBarIcons(sheetBackgroundColor(fragment))
    }

    private fun concealFragment(fragment: ViewBinding, animationDuration: Long = ANIMATION_DURATION) {
        ObjectAnimator.ofFloat(fragment.root, "y", mScreenHeight.toFloat()).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()

            addListener(
                endListener {
                    perhapsShowGuide()
                    updateDefaultLauncherBannerVisibility()
                }
            )
            start()
        }

        window.navigationBarColor = Color.TRANSPARENT
        binding.homeScreenGridView.root.fragmentCollapsed()
        updateStatusBarIcons()

        hideKeyboard()
        if (fragment is HdkWidgetsFragmentBinding) {
            wipeWidgetsSearch()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (fragment is HdkAllAppsFragmentBinding) {
                fragment.allAppsGridView.scrollToPosition(0)
                fragment.root.resetToFirstPage()
                fragment.root.touchDownY = -1
            } else if (fragment is HdkWidgetsFragmentBinding) {
                fragment.widgetsListView.scrollToPosition(0)
                fragment.root.touchDownY = -1
            }
        }, animationDuration)
    }

    fun homeScreenLongPressed(eventX: Float, eventY: Float) {
        if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()) {
            return
        }

        hideSwipeHint()

        val (x, y) = binding.homeScreenGridView.root.intoViewSpaceCoords(eventX, eventY)
        mIgnoreMoveEvents = true
        val clickedGridItem = binding.homeScreenGridView.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemLongClick(x, clickedGridItem)
        }

    }

    private fun isWorkspaceCovered() =
        isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded() || isLeftPanelExpanded() || isHostPanelExpanded()

    fun homeScreenClicked(eventX: Float, eventY: Float) {
        if (isWorkspaceCovered()) {
            return
        }

        if (performGuideStep()) {
            return
        }

        binding.homeScreenGridView.root.concealResizeLines()
        val (x, y) = binding.homeScreenGridView.root.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = binding.homeScreenGridView.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemClick(clickedGridItem)
        }
        if (clickedGridItem?.type != ITEM_TYPE_FOLDER) {
            binding.homeScreenGridView.root.shutFolder(redraw = true)
        }
    }

    fun homeScreenDoubleTapped(eventX: Float, eventY: Float) {

        if (isWorkspaceCovered()) {
            return
        }

        val (x, y) = binding.homeScreenGridView.root.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = binding.homeScreenGridView.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            return
        }

        val devicePolicyManager =
            getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
            ComponentName(this, ScreenLockAdminReceiver::class.java)
        )
        if (isLockDeviceAdminActive) {
            devicePolicyManager.lockNow()
        }
    }

    fun shutAppDrawer(delayed: Boolean = false) {
        if (isAllAppsFragmentExpanded()) {

            hideKeyboard()
            val close = {
                binding.allAppsFragmentView.root.y = mScreenHeight.toFloat()
                binding.allAppsFragmentView.allAppsGridView.scrollToPosition(0)
                binding.allAppsFragmentView.root.resetToFirstPage()
                binding.allAppsFragmentView.root.touchDownY = -1
                binding.homeScreenGridView.root.fragmentCollapsed()
                updateStatusBarIcons()

                perhapsShowGuide()
                updateDefaultLauncherBannerVisibility()
            }
            if (delayed) {
                Handler(Looper.getMainLooper()).postDelayed(close, APP_DRAWER_CLOSE_DELAY)
            } else {
                close()
            }
        }
    }

    fun shutWidgetsFragment(delayed: Boolean = false) {
        if (isWidgetsFragmentExpanded()) {
            val close = {
                binding.widgetsFragmentView.root.y = mScreenHeight.toFloat()
                binding.widgetsFragmentView.widgetsListView.scrollToPosition(0)
                wipeWidgetsSearch()
                binding.widgetsFragmentView.root.touchDownY = -1
                binding.homeScreenGridView.root.fragmentCollapsed()
                updateStatusBarIcons()

                perhapsShowGuide()
                updateDefaultLauncherBannerVisibility()
            }
            if (delayed) {
                Handler(Looper.getMainLooper()).postDelayed(close, APP_DRAWER_CLOSE_DELAY)
            } else {
                close()
            }
        }
    }

    fun wipeWidgetsSearch() {
        binding.widgetsFragmentView.searchBarView.closeSearch()
    }

    private fun performItemClick(clickedGridItem: DeskGridEntry) {
        when (clickedGridItem.type) {
            ITEM_TYPE_ICON -> if (clickedGridItem.packageName == packageName) {

                unfoldHostApp(clickedGridItem.activityName)
            } else {
                fireApp(clickedGridItem.packageName, clickedGridItem.activityName, HomeAdsSettings.HOME_LAUNCH)
            }
            ITEM_TYPE_FOLDER -> unfoldFolder(clickedGridItem)
            ITEM_TYPE_SHORTCUT -> {
                val id = clickedGridItem.shortcutId
                val packageName = clickedGridItem.packageName
                val userHandle = android.os.Process.myUserHandle()
                val shortcutBounds = binding.homeScreenGridView.root.getClickableRect(clickedGridItem)
                val launcherApps =
                    applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.startShortcut(packageName, id, shortcutBounds, null, userHandle)
            }
        }
    }

    private fun unfoldFolder(folder: DeskGridEntry) {
        binding.homeScreenGridView.root.unfoldFolder(folder)
    }

    private fun performItemLongClick(x: Float, clickedGridItem: DeskGridEntry) {
        if (clickedGridItem.type == ITEM_TYPE_ICON || clickedGridItem.type == ITEM_TYPE_SHORTCUT || clickedGridItem.type == ITEM_TYPE_FOLDER) {
            binding.mainHolderView.performHapticFeedback()
        }

        val anchorY = binding.homeScreenGridView.root.sideMargins.top +
                (clickedGridItem.top * binding.homeScreenGridView.root.cellHeight.toFloat())
        presentHomeIconMenu(x, anchorY, clickedGridItem, false)
    }

    fun presentHomeIconMenu(
        x: Float,
        y: Float,
        gridItem: DeskGridEntry,
        isOnAllAppsFragment: Boolean,
    ) {
        binding.homeScreenGridView.root.concealResizeLines()
        mLongPressedIcon = gridItem

        val grid = binding.homeScreenGridView.root
        val gridOnScreen = IntArray(2).also { grid.getLocationOnScreen(it) }
        val (centerX, itemTop, itemBottom) = when {
            isOnAllAppsFragment -> {
                val tile = realScreenSize.x / launcherConfig.drawerColumnCount.toFloat()
                Triple(x, y, y + tile)
            }
            gridItem.type == ITEM_TYPE_WIDGET -> {
                val top = gridOnScreen[1] + y
                Triple(gridOnScreen[0] + x, top, top + gridItem.getHeightInCells() * grid.cellHeight)
            }
            else -> {
                val rect = grid.getClickableRect(gridItem)
                Triple(gridOnScreen[0] + rect.exactCenterX(), gridOnScreen[1] + rect.top.toFloat(), gridOnScreen[1] + rect.bottom.toFloat())
            }
        }

        if (mOpenPopupMenu == null) {
            mOpenPopupMenu = processGridItemPopupMenu(
                anchorView = binding.root,
                gridItem = gridItem,
                isOnAllAppsFragment = isOnAllAppsFragment,
                listener = menuListener,
                centerX = centerX,
                itemTop = itemTop,
                itemBottom = itemBottom,
            )
        }
    }

    fun widgetLongPressedOnList(gridItem: DeskGridEntry) {
        mLongPressedIcon = gridItem
        concealFragment(binding.widgetsFragmentView)
        binding.homeScreenGridView.root.itemDraggingStarted(mLongPressedIcon!!)
    }

    private fun resetFragmentTouches() {
        binding.widgetsFragmentView.root.apply {
            touchDownY = -1
            ignoreTouches = false
        }

        binding.allAppsFragmentView.root.apply {
            touchDownY = -1
            ignoreTouches = false
        }
    }

    private fun concealIconUi(item: DeskGridEntry) {
        ensureBackgroundThread {
            val hiddenIconUi = ConcealedApp(null, item.packageName, item.activityName, item.title, null)
            hiddenIconsDB.insert(hiddenIconUi)

            runOnUiThread {
                binding.allAppsFragmentView.root.onIconHidden(item)
            }
        }
    }

    private fun renameItem(homeScreenGridItem: DeskGridEntry) {
        RelabelEntrySheet(this, homeScreenGridItem) {
            binding.homeScreenGridView.root.grabGridItems()
        }
    }

    val menuListener: EntryMenuCallback = object : EntryMenuCallback {
        override fun onAnyClick() {
            resetFragmentTouches()
        }

        override fun hide(gridItem: DeskGridEntry) {
            concealIconUi(gridItem)
        }

        override fun rename(gridItem: DeskGridEntry) {
            renameItem(gridItem)
        }

        override fun resize(gridItem: DeskGridEntry) {
            binding.homeScreenGridView.root.widgetLongPressed(gridItem)
        }

        override fun appInfoUi(gridItem: DeskGridEntry) {
            fireAppInfo(gridItem.packageName)
        }

        override fun remove(gridItem: DeskGridEntry) {
            binding.homeScreenGridView.root.dropAppIcon(gridItem)
        }

        override fun uninstall(gridItem: DeskGridEntry) {
            uninstallApp(gridItem.packageName)
        }

        override fun onDismiss() {
            mOpenPopupMenu = null
            resetFragmentTouches()
        }
    }

    private class DeskSwipeDetector(
        private val flingListener: SwipeCallback,
    ) : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            (flingListener as HomeDeskPane).homeScreenClicked(event.x, event.y)
            return super.onSingleTapUp(event)
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            (flingListener as HomeDeskPane).homeScreenDoubleTapped(event.x, event.y)
            return super.onDoubleTap(event)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {

            if (System.currentTimeMillis() - mLastUpEvent < 500L) {
                return true
            }

            if (abs(velocityY) > abs(velocityX)) {
                if (velocityY > 0) {
                    flingListener.onFlingDown()
                } else {
                    flingListener.onFlingUp()
                }
            } else if (abs(velocityX) > abs(velocityY)) {

                val travelX = if (event1 != null) event2.x - event1.x else velocityX
                val direction = if (travelX != 0f) travelX else velocityX
                if (sign(direction) != sign(velocityX)) {
                    val panel = flingListener as HomeDeskPane
                    val retreat = if (direction < 0) event2.x - panel.mGestureMinX else panel.mGestureMaxX - event2.x
                    if (retreat > panel.mTouchSlop) {
                        return true
                    }
                }
                if (direction > 0) {
                    flingListener.onFlingRight()
                } else {
                    flingListener.onFlingLeft()
                }
            }

            return true
        }

        override fun onLongPress(event: MotionEvent) {
            (flingListener as HomeDeskPane).homeScreenLongPressed(event.x, event.y)
        }
    }

    override fun onFlingUp() {
        if (mIgnoreYMoveEvents || !mDrawerEnabled) {
            return
        }

        if (!isWidgetsFragmentExpanded()) {
            mIgnoreUpEvent = true
            presentFragment(binding.allAppsFragmentView)
        }
    }

    @SuppressLint("WrongConstant")
    override fun onFlingDown() {
        if (mIgnoreYMoveEvents) {
            return
        }

        mIgnoreUpEvent = true
        if (isAllAppsFragmentExpanded()) {
            concealFragment(binding.allAppsFragmentView)
        } else if (isWidgetsFragmentExpanded()) {
            concealFragment(binding.widgetsFragmentView)
        } else {
            try {
                Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(getSystemService("statusbar"))
            } catch (_: Exception) {
            }
        }
    }

    override fun onFlingRight() {
        if (mIgnoreXMoveEvents) {
            return
        }

        mIgnoreUpEvent = true
        val grid = binding.homeScreenGridView.root

        if (grid.flingSwipe(towardsNext = false)) {
            return
        }
        if (!isAllAppsFragmentExpanded() && !isWidgetsFragmentExpanded() && grid.isOnFirstPage()) {
            presentHostPanel()
        } else {
            grid.prevPage(redraw = true)
        }
    }

    override fun onFlingLeft() {
        if (mIgnoreXMoveEvents) {
            return
        }

        mIgnoreUpEvent = true
        val grid = binding.homeScreenGridView.root

        if (grid.flingSwipe(towardsNext = true)) {
            return
        }
        if (!isAllAppsFragmentExpanded() && !isWidgetsFragmentExpanded() && grid.isOnLastPage()) {
            presentLeftPanel()
        } else {
            grid.nextPage(redraw = true)
        }
    }

    fun getAllAppLaunchers(): ArrayList<LaunchableApp> = HomeRoleProbe.scan(
        this,
        onQuick = { quick ->
            binding.allAppsFragmentView.root.gotLaunchers(quick)
            binding.leftPanelView.root.gotLaunchers(quick)
        },
        onProgress = { partial ->
            binding.allAppsFragmentView.root.gotLaunchers(partial)
            binding.leftPanelView.root.gotLaunchers(partial)
        },
    )

    private fun purgeSeededSearchBarIfNeeded() {
        if (launcherConfig.wasSearchBarPurged) {
            return
        }

        launcherConfig.wasSearchBarPurged = true
        try {
            homeScreenGridItemsDB.deleteItemsByClassName(PSEUDO_WIDGET_SEARCH)
        } catch (e: Exception) {
            Log.e("HomeDeskPane", "Failed to remove the seeded search bar", e)
        }
    }

    private fun hostDockPackage(): String? = HomeServiceLocator.bridge.dockSlotPackage(this)

    private fun dockSlotPackage(): String? {
        val wanted = hostDockPackage()
        return if (wanted == packageName && launcherConfig.selfIconHidden) null else wanted
    }

    private fun dockableTitle(packageName: String?): String? {
        if (packageName.isNullOrEmpty()) return null
        return try {
            packageManager.getLaunchIntentForPackage(packageName) ?: return null
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun hostDockItem(packageName: String?): DeskGridEntry? {
        if (packageName.isNullOrEmpty()) return null
        val ours = packageName == this.packageName
        if (ours && launcherConfig.selfIconHidden) return null
        val title = if (ours) hostLabel() else dockableTitle(packageName) ?: return null
        return DeskGridEntry(
            id = null,
            left = DOCK_HOST_SLOT,
            top = launcherConfig.homeRowCount - 1,
            right = DOCK_HOST_SLOT,
            bottom = launcherConfig.homeRowCount - 1,
            page = 0,
            packageName = packageName,
            activityName = if (ours) hostActivityName() else "",
            title = title,
            type = ITEM_TYPE_ICON,
            className = "",
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = true,
            parentId = null
        )
    }

    private fun syncHostDockItem() {
        if (!launcherConfig.wasHomeScreenInit) return
        val wanted = dockSlotPackage() ?: return

        val placed = launcherConfig.dockHostPackage.ifEmpty { packageName }
        if (wanted == placed && launcherConfig.dockHostPackage.isNotEmpty()) return
        ensureBackgroundThread {
            val items = homeScreenGridItemsDB.getAllItems()
            if (wanted == placed) {

                val slotFilled = items.any { it.docked && it.left == DOCK_HOST_SLOT && it.parentId == null }
                if (!slotFilled) {
                    hostDockItem(wanted)?.let { homeScreenGridItemsDB.insert(it) }
                    binding.homeScreenGridView.root.grabGridItems()
                }
                launcherConfig.dockHostPackage = placed
                return@ensureBackgroundThread
            }
            items.filter { it.docked && it.type == ITEM_TYPE_ICON && it.packageName == placed }
                .forEach { homeScreenGridItemsDB.deleteById(it.id!!) }
            val slotTaken = items.any {
                it.docked && it.left == DOCK_HOST_SLOT && it.packageName != placed && it.parentId == null
            }
            if (!slotTaken) {
                hostDockItem(wanted)?.let { homeScreenGridItemsDB.insert(it) }
            }
            launcherConfig.dockHostPackage = wanted
            Timber.d("HomeDeskPane: dock host slot $placed -> $wanted (slotTaken=$slotTaken)")
            binding.homeScreenGridView.root.grabGridItems()
        }
    }

    private fun getDefaultAppPackages() {
        val homeScreenGridItems = ArrayList<DeskGridEntry>()

        try {
            val defaultDialerPackage =
                (getSystemService(TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            dockableTitle(defaultDialerPackage)?.let { title ->
                val dialerIcon =
                    DeskGridEntry(
                        id = null,
                        left = 0,
                        top = launcherConfig.homeRowCount - 1,
                        right = 0,
                        bottom = launcherConfig.homeRowCount - 1,
                        page = 0,
                        packageName = defaultDialerPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(dialerIcon)
            }
        } catch (_: Exception) {
        }

        hostDockItem(dockSlotPackage())?.let { item ->
            homeScreenGridItems.add(item)
            launcherConfig.dockHostPackage = item.packageName
        }

        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
            val resolveInfo =
                packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            dockableTitle(defaultBrowserPackage)?.let { title ->
                val browserIcon =
                    DeskGridEntry(
                        id = null,
                        left = 2,
                        top = launcherConfig.homeRowCount - 1,
                        right = 2,
                        bottom = launcherConfig.homeRowCount - 1,
                        page = 0,
                        packageName = defaultBrowserPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(browserIcon)
            }
        } catch (_: Exception) {
        }

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveInfo =
                packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultCameraPackage = resolveInfo!!.activityInfo.packageName
            dockableTitle(defaultCameraPackage)?.let { title ->
                val cameraIcon =
                    DeskGridEntry(
                        id = null,
                        left = 3,
                        top = launcherConfig.homeRowCount - 1,
                        right = 3,
                        bottom = launcherConfig.homeRowCount - 1,
                        page = 0,
                        packageName = defaultCameraPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (_: Exception) {
        }

        if (launcherConfig.dockColumnCount >= 5) {
            dockableTitle(PLAY_STORE_PACKAGE)?.let { title ->
                homeScreenGridItems.add(
                    DeskGridEntry(
                        id = null,
                        left = 4,
                        top = launcherConfig.homeRowCount - 1,
                        right = 4,
                        bottom = launcherConfig.homeRowCount - 1,
                        page = 0,
                        packageName = PLAY_STORE_PACKAGE,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                )
            }
        }

        homeScreenGridItemsDB.insertAll(homeScreenGridItems)
    }

    fun processWidgetBinding(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo,
        callback: (canBind: Boolean) -> Unit,
    ) {
        mActionOnCanBindWidget = null
        val canCreateWidget =
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetInfo.provider)
        if (canCreateWidget) {
            callback(true)
        } else {
            mActionOnCanBindWidget = callback
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetInfo.provider)
                startActivityForResult(this, REQUEST_ALLOW_BINDING_WIDGET)
            }
        }
    }

    fun processWidgetConfigureScreen(
        appWidgetHost: AppWidgetHost,
        appWidgetId: Int,
        callback: (canBind: Boolean) -> Unit,
    ) {
        mActionOnWidgetConfiguredWidget = callback
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            this,
            appWidgetId,
            0,
            REQUEST_CONFIGURE_WIDGET,
            null
        )
    }

    fun processShorcutCreation(
        activityInfo: ActivityInfo,
        callback: (shortcutId: String, label: String, icon: Drawable) -> Unit,
    ) {
        mActionOnAddShortcut = callback
        val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
        Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            component = componentName
            startActivityForResult(this, REQUEST_CREATE_SHORTCUT)
        }
    }

    private fun sheetBackgroundColor(fragment: ViewBinding): Int =
        if (fragment is HdkAllAppsFragmentBinding) {
            getColor(R.color.launcher_all_app_bg)
        } else {
            getProperBackgroundColor()
        }

    private fun sidePanelBackgroundColor(panel: View): Int =
        (panel.background as? ColorDrawable)?.color ?: getProperBackgroundColor()

    private fun currentSurfaceColor(): Int? = when {
        isAllAppsFragmentExpanded() -> sheetBackgroundColor(binding.allAppsFragmentView)
        isWidgetsFragmentExpanded() -> sheetBackgroundColor(binding.widgetsFragmentView)
        isLeftPanelExpanded() -> sidePanelBackgroundColor(binding.leftPanelView.root)
        isHostPanelExpanded() -> sidePanelBackgroundColor(binding.hostPanelView.root)
        else -> null
    }

    private fun updateStatusBarIcons(backgroundColor: Int? = null) {
        val isLightBackground = when {
            backgroundColor != null -> backgroundColor.getContrastColor() == DARK_GREY
            else -> false
        }
        window.insetsController().apply {
            isAppearanceLightStatusBars = isLightBackground
            isAppearanceLightNavigationBars = isLightBackground
        }
    }

    private fun hostLabel(): String = runCatching {
        applicationInfo.loadLabel(packageManager).toString()
    }.getOrDefault(packageName)

    private fun hostActivityName(): String =
        HomeServiceLocator.bridge.hostLaunchComponent(this)?.className.orEmpty()
}
