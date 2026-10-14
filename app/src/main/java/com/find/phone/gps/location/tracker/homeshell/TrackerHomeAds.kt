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

        val emptyFrame = container.childCount == 0
        if (slot.needsNativePreload && emptyFrame && !InlineSponsor.hasPreloadedNative()) {
            InlineSponsor().fetchNativeADs(activity, object : InlineSponsor.InlineAdCallback {
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

        HomeAdsSettings.renewSlot(activity, slot, container)

        ViewCompat.requestApplyInsets(container)
        if (key == HomeAdsPort.SLOT_DRAWER_TOP) shapeAdIcon(container)
    }

    private fun shapeAdIcon(container: FrameLayout) {
        val apply = {
            (container.locateNativeAdView()?.iconView as? ImageView)?.let(HomeGlyphPort::applyTo)
        }
        runCatching(apply)
        container.post { runCatching(apply) }
    }

    private fun View.locateNativeAdView(): NativeAdView? {
        if (this is NativeAdView) return this
        if (this !is ViewGroup) return null
        for (i in 0 until childCount) getChildAt(i).locateNativeAdView()?.let { return it }
        return null
    }

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

    override fun showFullNative(activity: Activity, tag: String, onDone: () -> Unit) {
        val surface = when (tag) {
            APP_LAUNCH_TAG -> HomeAdsSettings.HomeDeskFragment.APP_CLICK
            HOME_LAUNCH_TAG -> HomeAdsSettings.clickSurface(activity, HomeAdsSettings.HomeDeskFragment.HOME_CLICK)
            DRAWER_LAUNCH_TAG -> HomeAdsSettings.clickSurface(activity, HomeAdsSettings.HomeDeskFragment.DRAWER_CLICK)
            else -> return onDone()
        }
        HomeAdsSettings.runSurface(activity, surface, onDone)
    }

    override fun openSponsored(activity: Activity, url: String) {
        val mode = DeepLinkRouter.modeOf(AdPrefsStore.getInstance(activity).getString("link_open_in"))
            ?: DeepLinkRouter.Mode.WEBVIEW
        DeepLinkRouter.open(activity, url, mode)
    }

    private companion object {

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
