package com.find.phone.gps.location.tracker.homeshell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.HomeshellShellPanelBinding
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardMainFragment
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardSideMenu
import io.homedeck.grid.bridge.HomeDeskContent

class HomeShellPane : Fragment(), HomeDeskContent, DashboardMainFragment.MenuHost {

    private var _binding: HomeshellShellPanelBinding? = null

    private var drawerMenu: DashboardSideMenu? = null

    private val phonePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            drawerMenu?.onPhonePermissionResult(it)
        }

    private val host: HomeShellOwner? get() = HomeShellOwner.of(activity)

    private val home: DashboardMainFragment?
        get() = childFragmentManager.findFragmentById(R.id.panelContentVw) as? DashboardMainFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val themed = inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.Theme_GpsTracker))
        return HomeshellShellPanelBinding.inflate(themed, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = _binding ?: return
        if (home == null) {

            childFragmentManager.beginTransaction()
                .replace(R.id.panelContentVw, DashboardMainFragment())
                .commitNow()
        }

        binding.panelDrawerVw.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        (activity as? AppCompatActivity)?.let { activity ->
            drawerMenu = DashboardSideMenu(
                activity,
                binding.drawerMenuVw,
                requestPhonePermission = { phonePermission.launch(it) },
            ) { shutMenu() }.also { it.bind(); it.watch(binding.panelDrawerVw) }
        }
    }

    override fun onDestroyView() {
        drawerMenu = null
        _binding = null
        super.onDestroyView()
    }

    override fun openHomeMenu() {
        _binding?.panelDrawerVw?.openDrawer(GravityCompat.START)
    }

    private fun shutMenu() {
        _binding?.panelDrawerVw?.closeDrawer(GravityCompat.START)
    }

    private val isMenuOpen: Boolean
        get() = _binding?.panelDrawerVw?.isDrawerOpen(GravityCompat.START) == true

    override fun setPanelVisible(visible: Boolean) {
        host?.shellVisible = visible

        if (!visible) {
            host?.homeShellController?.onShellHidden()
            _binding?.panelDrawerVw?.closeDrawer(GravityCompat.START, false)
        }

        home?.setOnScreen(visible)
        if (!visible) return
        drawerMenu?.refresh()

        if (HomeRoleJourney.wasOnboardingCompleted(requireContext())) {
            host?.homeShellController?.kickFirstRunPriming()
        }
    }

    override fun handleBack(): Boolean {
        if (!isMenuOpen) return false
        shutMenu()
        return true
    }
}
