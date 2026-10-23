package com.find.phone.gps.location.tracker.screens.dashboard

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.monetize.render.StoreUpdateCallback
import com.find.phone.gps.location.tracker.monetize.render.StoreUpdateKeeper
import com.find.phone.gps.location.tracker.grants.GrantPanelDialog
import com.find.phone.gps.location.tracker.screens.consent.OverlayToolbox

class DashboardShellDriver(private val host: DashboardShellOwner) {

    private val activity get() = host.hostActivity

    private var permissionSheetPending = false

    private var shellOffScreen = false

    private var sheetTakenDownByUs = false

    private var permissionSheetDismissed = false

    private var primingStarted = false

    private var hostCreated = false

    private val handler = Handler(Looper.getMainLooper())

    private val overlayLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        stopOverlayGrantPoll()
    }

    /** [restored]: the host is being recreated (theme/locale change), so the permission sheet is not re-offered. */
    fun onHostCreated(restored: Boolean = false) {
        hostCreated = true
        if (restored) primingStarted = true
        StoreUpdateKeeper.enrollLauncher(activity)
        perhapsCheckForUpdate()
    }

    fun kickFirstRunPriming() {
        shellOffScreen = false
        if (primingStarted) {
            if (permissionSheetPending) perhapsAutoShowPermissionSheet()
            return
        }
        primingStarted = true
        perhapsAutoShowPermissionSheet()
    }

    fun onHostResume() {

        StoreUpdateKeeper.resumeUpdate()

        perhapsCheckForUpdate()
    }

    fun onHostDestroy() {
        hostCreated = false
        stopOverlayGrantPoll()
        handler.removeCallbacksAndMessages(null)

        StoreUpdateKeeper.destroy(activity)
    }

    private fun perhapsCheckForUpdate() {
        if (!hostCreated) return
        val pref = AdPrefsStore.getInstance(activity)
        if (!pref.getBoolean("In_App_Update_Show")) return

        val isForceUpdate = pref.getBoolean("In_App_Update_Force_Show")

        if (!isForceUpdate) {
            val age = System.currentTimeMillis() - pref.getLong(LAST_UPDATE_CHECK_KEY, 0L)
            if (age in 0 until UPDATE_CHECK_WINDOW_MS) return
            pref.putLong(LAST_UPDATE_CHECK_KEY, System.currentTimeMillis())
        }

        StoreUpdateKeeper.init(
            activity = activity,
            isForceUpdate = isForceUpdate,
            callback = object : StoreUpdateCallback {
                override fun onUpdateSuccess() {}
                override fun onUpdateCanceled() {}
                override fun onUpdateFailed() {}
                override fun onUpdateDownloaded() {
                    host.showUpdateReadyPrompt()
                }
            }
        )
    }

    fun presentPermissionSheet() {
        GrantPanelDialog.show(activity) {

            if (sheetTakenDownByUs) sheetTakenDownByUs = false else permissionSheetDismissed = true
        }
    }

    fun onShellHidden() {
        shellOffScreen = true
        if (!GrantPanelDialog.isShowing(activity)) return
        sheetTakenDownByUs = true
        GrantPanelDialog.retireIfShowing(activity)
        permissionSheetPending = true
    }

    private fun perhapsAutoShowPermissionSheet() {
        if (shellOffScreen || !host.isShellOnScreen) {
            permissionSheetPending = true
            return
        }
        permissionSheetPending = false
        if (GrantPanelDialog.shouldAutoShow(activity)) presentPermissionSheet()
    }

    fun shouldShowPermissionHint(): Boolean =
        permissionSheetDismissed && GrantPanelDialog.hasPending(activity)

    fun kickOverlayPermissionFlow() {
        if (!OverlayToolbox.isOfferable(activity) || OverlayToolbox.isGranted(activity)) return

        ResumeAdKeeper.skipNextAppOpenAd = true

        val launched = runCatching {
            overlayLauncher.launch(OverlayToolbox.composeOverlayIntent(activity.packageName))
        }.isSuccess
        if (!launched) return

        OverlayToolbox.presentGuide(activity)
        kickOverlayGrantPoll()
    }

    private val overlayGrantPollHandler = Handler(Looper.getMainLooper())
    private var overlayGrantPolling = false
    private val overlayGrantPoll = object : Runnable {
        override fun run() {
            if (activity.isDestroyed) { overlayGrantPolling = false; return }
            if (OverlayToolbox.isGranted(activity)) {
                overlayGrantPolling = false
                host.bringHostToFront()
            } else {
                overlayGrantPollHandler.postDelayed(this, GRANT_POLL_MS)
            }
        }
    }

    private fun kickOverlayGrantPoll() {
        if (overlayGrantPolling) return
        overlayGrantPolling = true
        overlayGrantPollHandler.removeCallbacks(overlayGrantPoll)
        overlayGrantPollHandler.postDelayed(overlayGrantPoll, GRANT_POLL_MS)
    }

    private fun stopOverlayGrantPoll() {
        overlayGrantPolling = false
        overlayGrantPollHandler.removeCallbacks(overlayGrantPoll)
    }

    companion object {

        private const val GRANT_POLL_MS = 350L

        private const val LAST_UPDATE_CHECK_KEY = "last_inapp_update_check_at"

        private const val UPDATE_CHECK_WINDOW_MS = 6 * 60 * 60 * 1000L

        const val REORDER_FLAGS =
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP

        const val EXTRA_SELF_REORDER = "extra_self_reorder"
    }
}
