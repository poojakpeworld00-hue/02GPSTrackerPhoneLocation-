package com.find.phone.gps.location.tracker.homeshell

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.find.phone.gps.location.tracker.monetize.render.StoreUpdateKeeper
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardShellDriver
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardShellOwner
import com.google.android.material.snackbar.Snackbar
import io.homedeck.grid.screens.HomeDeskPane
import java.util.WeakHashMap

/**
 * [DashboardShellOwner] for the launcher module's home screen.
 *
 * [HomeDeskPane] is the module's Activity, so it cannot implement our interface the way the old
 * in-app launcher did. This object stands in for it, one per HomeDeskPane instance, and the shell
 * fragments find it through [of].
 *
 * It must be created from [TrackerHomeBridge.onLauncherStart]: that runs inside the launcher's
 * `onCreate`, and [DashboardShellDriver] registers its result launchers on construction, which only
 * works before the Activity is STARTED.
 */
class HomeShellOwner private constructor(private val panel: HomeDeskPane) : DashboardShellOwner {

    override val hostActivity: AppCompatActivity get() = panel

    override val homeShellController = DashboardShellDriver(this)

    /** Set by [HomeShellPane] as the right-hand panel slides in and out. */
    var shellVisible = false

    private var updateReadySnackbar: Snackbar? = null

    override val isShellOnScreen: Boolean get() = shellVisible

    /** Back inside the panel with the shell's own tab history exhausted just closes it. */
    override fun onShellBackExhausted() = panel.closeHostPanel()

    /**
     * With the panel open the shell's own Snackbar is right; with it shut the shell is parked off
     * screen, so the home grid carries the prompt or a downloaded update has nowhere to install from.
     */
    override fun showUpdateReadyPrompt() {
        if (updateReadySnackbar?.isShown == true) return
        updateReadySnackbar = Snackbar
            .make(panel.findViewById(android.R.id.content), R.string.update_ready_msg, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { StoreUpdateKeeper.completeUpdate() }
            .also { it.show() }
    }

    /** Back from a Settings round trip (overlay / FSI grant): land in the panel, not on the grid. */
    override fun bringHostToFront() {
        runCatching {
            panel.startActivity(
                Intent(panel, HomeDeskPane::class.java)
                    .addFlags(DashboardShellDriver.REORDER_FLAGS)
                    .putExtra(HomeDeskPane.EXTRA_OPEN_HOST_PANEL, true)
            )
        }
    }

    companion object {

        private val hosts = WeakHashMap<Activity, HomeShellOwner>()

        fun attach(panel: HomeDeskPane) {
            if (hosts.containsKey(panel)) return
            val host = HomeShellOwner(panel)
            hosts[panel] = host
            host.homeShellController.onHostCreated()
            panel.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    hosts.remove(panel)?.homeShellController?.onHostDestroy()
                }
            })
        }

        fun of(activity: Activity?): HomeShellOwner? = activity?.let { hosts[it] }
    }
}
