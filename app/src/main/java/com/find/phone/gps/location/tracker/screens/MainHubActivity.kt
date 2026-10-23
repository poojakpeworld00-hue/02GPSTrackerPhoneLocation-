package com.find.phone.gps.location.tracker.screens

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.render.StoreUpdateKeeper
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.databinding.PageMainBinding
import com.find.phone.gps.location.tracker.homeshell.HomeModeToggle
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardMainFragment
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardSideMenu
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardShellDriver
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardShellOwner
import com.google.android.material.snackbar.Snackbar
import io.homedeck.grid.screens.HomeDeskPane as LauncherHomeActivity

class MainHubActivity : FoundationActivity<PageMainBinding>(), DashboardShellOwner, DashboardMainFragment.MenuHost {

    override val layoutId: Int = R.layout.page_main

    override val hostActivity: AppCompatActivity get() = this

    override val homeShellController = DashboardShellDriver(this)

    override val isShellOnScreen: Boolean get() = true

    private var lastBackAt = 0L
    private var exitToast: Toast? = null

    private var drawerMenu: DashboardSideMenu? = null

    private val phonePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            drawerMenu?.onPhonePermissionResult(it)
        }

    private val homeFragment: DashboardMainFragment?
        get() = supportFragmentManager.findFragmentById(R.id.homeContainerVw) as? DashboardMainFragment

    override fun showUpdateReadyPrompt() {
        Snackbar.make(binding.mainVw, R.string.update_ready_msg, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { StoreUpdateKeeper.completeUpdate() }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        homeShellController.onHostCreated()
    }

    override fun initView() {

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        homeFragment?.setOnScreen(true)

        drawerMenu = DashboardSideMenu(
            this,
            binding.drawerMenuVw,
            requestPhonePermission = { phonePermission.launch(it) },
        ) {
            binding.mainVw.closeDrawer(GravityCompat.START)
        }.also { it.bind(); it.watch(binding.mainVw) }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.mainVw.isDrawerOpen(GravityCompat.START)) binding.mainVw.closeDrawer(GravityCompat.START)
            else onShellBackExhausted()
        }

        homeShellController.kickFirstRunPriming()
    }

    override fun showBottomBanner() = Unit

    override fun openHomeMenu() {
        binding.mainVw.openDrawer(GravityCompat.START)
    }

    override fun onResume() {
        super.onResume()
        homeShellController.onHostResume()

        drawerMenu?.refresh()
    }

    override fun onDestroy() {
        homeShellController.onHostDestroy()
        super.onDestroy()
    }

    override fun onShellBackExhausted() {

        if (!HomeModeToggle.isEnabled(this)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackAt <= EXIT_WINDOW_MS) {
                exitToast?.cancel()
                finish()
            } else {
                lastBackAt = now
                exitToast?.cancel()
                exitToast = Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).also { it.show() }
            }
            return
        }
        runCatching {
            startActivity(
                Intent(this, LauncherHomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
        finish()
    }

    override fun bringHostToFront() {
        runCatching {
            startActivity(Intent(this, MainHubActivity::class.java).addFlags(DashboardShellDriver.REORDER_FLAGS))
        }
    }

    private companion object {

        const val EXIT_WINDOW_MS = 2_000L
    }
}
