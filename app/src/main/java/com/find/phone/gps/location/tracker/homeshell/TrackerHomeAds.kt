package com.find.phone.gps.location.tracker.homeshell

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.render.DeepLinkRouter
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsor
import com.find.phone.gps.location.tracker.TrackerPhoneApp
import com.google.android.gms.ads.nativead.NativeAdView
import io.homedeck.grid.bridge.HomeAdsPort
import io.homedeck.grid.bridge.HomeGlyphPort

/**
 * The launcher module's ad surface, backed by this app's own ad layer (admesh).
 *
 * Everything comes from this app's `launcher_ads`, one block per moment (see
 * [HomeAdsSettings.HomeDeskFragment]): the gesture ads run `app_click` / `left_panel.open_ad` /
 * `right_panel.open_ad` with their own sequence and counter, and the inline slots are:
 *
 *  - [HomeAdsPort.SLOT_PANEL_BOTTOM] ← `left_panel.native_bottom`
 *  - [HomeAdsPort.SLOT_PANEL_MID]    ← `left_panel.native_mid`
 *  - [HomeAdsPort.SLOT_DRAWER_TOP]   ← `app_drawer.bottom_native`
 *
 * On top of that, each slot answers to its `ScreenAds.<name>.show` switch like every app screen.
 */
class TrackerHomeAds : HomeAdsPort {

    override fun isReady(): Boolean =
        AdPrefsStore.getInstance(TrackerPhoneApp.appContext).getBoolean("IsAdsON")

    override fun bindNative(activity: Activity?, key: String, container: FrameLayout?) {
        if (activity == null || container == null) return
        if (activity.isFinishing || activity.isDestroyed) return

        val slot = when (key) {
            HomeAdsPort.SLOT_PANEL_BOTTOM -> HomeAdsSettings.leftPanelBottomSlot(activity)
            HomeAdsPort.SLOT_PANEL_MID -> HomeAdsSettings.leftPanelMidSlot(activity)
            HomeAdsPort.SLOT_DRAWER_TOP -> HomeAdsSettings.appDrawerSlot(activity)
            else -> return
        }

        if (!PageSponsorSettings.resolve(activity, screenAdsKey(key)).show) {
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        // The launcher never goes through this app's Activities, so the native preload those do
        // has not run — on a device booted straight into the home screen the cache is empty, and
        // an empty frame asked to render with nothing cached stays blank for good. Request one and
        // paint when it lands; the second pass has a cached ad and cannot come back here.
        val emptyFrame = container.childCount == 0
        if (slot.needsNativePreload && emptyFrame && !InlineSponsor.hasPreloadedNative()) {
            InlineSponsor().loadNativeADs(activity, object : InlineSponsor.InlineAdCallback {
                override fun onNativeAdLoaded() {
                    if (activity.isFinishing || activity.isDestroyed) return
                    paint(activity, slot, container, key)
                }

                override fun onNativeAdFailed() = Unit
            })
            return
        }

        paint(activity, slot, container, key)
    }

    private fun paint(activity: Activity, slot: HomeAdsSettings.Slot, container: FrameLayout, key: String) {
        // refreshSlot keeps an ad already on screen rather than swapping it for a blank when the
        // shared native cache is momentarily empty.
        HomeAdsSettings.refreshSlot(activity, slot, container)
        // A slot that was GONE at the last inset dispatch has no nav-bar padding yet.
        ViewCompat.requestApplyInsets(container)
        if (key == HomeAdsPort.SLOT_DRAWER_TOP) shapeAdIcon(container)
    }

    /**
     * The drawer slot sits among app icons, and a native ad's icon arrives square: give it the same
     * device shape as the tiles around it. Once now and once after layout, since the ad view may be
     * bound a frame later.
     */
    private fun shapeAdIcon(container: FrameLayout) {
        val apply = {
            (container.findNativeAdView()?.iconView as? ImageView)?.let(HomeGlyphPort::applyTo)
        }
        runCatching(apply)
        container.post { runCatching(apply) }
    }

    private fun View.findNativeAdView(): NativeAdView? {
        if (this is NativeAdView) return this
        if (this !is ViewGroup) return null
        for (i in 0 until childCount) getChildAt(i).findNativeAdView()?.let { return it }
        return null
    }

    /**
     * A panel is opening (`left_panel.open_ad` / `right_panel.open_ad`) or Back was pressed on the
     * home screen (`home_back`): that block's own sequence and counter. [onDone] (the panel opening,
     * or nothing for Back) runs exactly once whatever happens.
     */
    override fun showInterstitial(activity: Activity, tag: String, onDone: () -> Unit) {
        val surface = when (tag) {
            LEFT_SWIPE_TAG -> HomeAdsSettings.HomeDeskFragment.LEFT_PANEL
            RIGHT_SWIPE_TAG -> HomeAdsSettings.HomeDeskFragment.RIGHT_PANEL
            HOME_BACK_TAG -> HomeAdsSettings.HomeDeskFragment.HOME_BACK
            HOME_PRESS_TAG -> HomeAdsSettings.HomeDeskFragment.HOME_PRESS
            else -> return onDone()
        }
        HomeAdsSettings.runSurface(activity, surface, onDone)
    }

    /**
     * An app is being opened: `home_click` from the home screen, `drawer_click` from the drawer
     * (each falling back to `app_click` when Remote Config has no block for it), and `app_click`
     * from anywhere else (the apps panel and its search). Then the app.
     */
    override fun showFullNative(activity: Activity, tag: String, onDone: () -> Unit) {
        val surface = when (tag) {
            APP_LAUNCH_TAG -> HomeAdsSettings.HomeDeskFragment.APP_CLICK
            HOME_LAUNCH_TAG -> HomeAdsSettings.clickSurface(activity, HomeAdsSettings.HomeDeskFragment.HOME_CLICK)
            DRAWER_LAUNCH_TAG -> HomeAdsSettings.clickSurface(activity, HomeAdsSettings.HomeDeskFragment.DRAWER_CLICK)
            else -> return onDone()
        }
        HomeAdsSettings.runSurface(activity, surface, onDone)
    }

    /** Sponsored drawer tiles open through the same opener as every direct link (`link_open_in`, default WebView). */
    override fun openSponsored(activity: Activity, url: String) {
        val mode = DeepLinkRouter.modeOf(AdPrefsStore.getInstance(activity).getString("link_open_in"))
            ?: DeepLinkRouter.Mode.WEBVIEW
        DeepLinkRouter.open(activity, url, mode)
    }

    /**
     * The `ScreenAds` entry that switches [key], named for where the slot actually is. A key the
     * launcher adds later keeps its own name, so it is switchable the moment it exists.
     */
    private companion object {
        /** The module's gesture tags (`io.homedeck.grid.sponsor.HomeAdsSettings`). */
        const val APP_LAUNCH_TAG = "appLaunch"
        const val HOME_LAUNCH_TAG = "homeLaunch"
        const val DRAWER_LAUNCH_TAG = "drawerLaunch"
        const val HOME_BACK_TAG = "homeBack"
        const val HOME_PRESS_TAG = "homePress"
        const val LEFT_SWIPE_TAG = "leftSwipe"
        const val RIGHT_SWIPE_TAG = "rightSwipe"
    }

    private fun screenAdsKey(key: String): String = when (key) {
        HomeAdsPort.SLOT_PANEL_BOTTOM -> "LauncherAppsPanel"
        HomeAdsPort.SLOT_PANEL_MID -> "LauncherAppsPanelMid"
        HomeAdsPort.SLOT_DRAWER_TOP -> "LauncherAppDrawer"
        else -> key
    }
}
