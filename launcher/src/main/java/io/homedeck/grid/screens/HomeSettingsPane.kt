package io.homedeck.grid.screens

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.RadioItem
import io.homedeck.grid.R
import io.homedeck.grid.support.HelpIntentFilter
import io.homedeck.grid.databinding.HdkScreenSettingsBinding
import io.homedeck.grid.oemprofile.TrayLayoutMode
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.support.MAX_COLUMN_COUNT
import io.homedeck.grid.support.MAX_ROW_COUNT
import io.homedeck.grid.support.MIN_COLUMN_COUNT
import io.homedeck.grid.support.MIN_ROW_COUNT
import io.homedeck.grid.hooks.ScreenLockAdminReceiver
import java.util.Locale
import kotlin.system.exitProcess

private const val DRAWER_STYLE_VERTICAL = 0
private const val DRAWER_STYLE_PAGED = 1

class HomeSettingsPane : HomeFoundationPane() {

    private val binding by viewBinding(HdkScreenSettingsBinding::inflate)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollviewView))
        setupMaterialScrollListener(binding.settingsNestedScrollviewView, binding.settingsAppbarView)
        prepareOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbarView, NavigationIcon.Arrow)
        renewMenuItems()

        prepareCustomizeColors()
        prepareUseEnglish()
        prepareDoubleTapToLock()
        prepareAppDrawerEnabled()
        prepareDrawerStyle()
        prepareCloseAppDrawerOnOtherAppOpen()
        prepareOpenKeyboardOnAppDrawer()
        prepareDrawerColumnCount()
        prepareDrawerSearchBar()
        prepareShowDrawerAppLabels()
        prepareHomeRowCount()
        prepareHomeColumnCount()
        prepareShowHomeAppLabels()
        prepareLanguage()
        prepareManageHiddenIcons()
        updateTextColors(binding.settingsHolderView)

        arrayOf(
            binding.settingsColorCustomizationSectionLabelView,
            binding.settingsGeneralSettingsLabelView,
            binding.settingsDrawerSettingsLabelView,
            binding.settingsHomeScreenLabelView
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun prepareOptionsMenu() {
        binding.settingsToolbarView.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.aboutView -> fireAbout()
                R.id.more_apps_from_usView -> launchMoreAppsFromUsIntent()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun renewMenuItems() {
        binding.settingsToolbarView.menu.apply {
            findItem(R.id.more_apps_from_usView).isVisible =
                !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun prepareCustomizeColors() {
        binding.settingsColorCustomizationHolderView.setOnClickListener {

            withFossifyPackageNameSpoofed { startCustomizationActivity() }
        }
    }

    private fun prepareUseEnglish() {
        binding.settingsUseEnglishHolderView.beVisibleIf(
            beVisible = (launcherConfig.wasUseEnglishToggled || Locale.getDefault().language != "en")
                    && !isTiramisuPlus()
        )

        binding.settingsUseEnglishView.isChecked = launcherConfig.useEnglish
        binding.settingsUseEnglishHolderView.setOnClickListener {
            binding.settingsUseEnglishView.toggle()
            launcherConfig.useEnglish = binding.settingsUseEnglishView.isChecked
            exitProcess(0)
        }
    }

    private fun prepareDoubleTapToLock() {
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        binding.settingsDoubleTapToLockView.isChecked = devicePolicyManager.isAdminActive(
            ComponentName(this, ScreenLockAdminReceiver::class.java)
        )

        binding.settingsDoubleTapToLockHolderView.setOnClickListener {
            val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
                ComponentName(this, ScreenLockAdminReceiver::class.java)
            )
            if (isLockDeviceAdminActive) {
                devicePolicyManager.removeActiveAdmin(
                    ComponentName(this, ScreenLockAdminReceiver::class.java)
                )
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, ScreenLockAdminReceiver::class.java)
                )
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.launcher_lock_device_admin_hint)
                )
                startActivity(intent)
            }
        }
    }

    private fun prepareAppDrawerEnabled() {
        fun refresh() {
            val enabled = launcherConfig.isDrawerEnabled
            binding.settingsAppDrawerEnabledView.isChecked = enabled
            binding.settingsAppDrawerDisabledHintView.beVisibleIf(!enabled)
            binding.settingsDrawerStyleHolderView.beVisibleIf(enabled)
            binding.settingsDrawerColumnCountHolderView.beVisibleIf(enabled)
            binding.settingsDrawerSearchHolderView.beVisibleIf(enabled)
            binding.settingsOpenKeyboardOnAppDrawerHolderView.beVisibleIf(enabled && launcherConfig.showSearchBar)
            binding.settingsCloseAppDrawerOnOtherAppHolderView.beVisibleIf(enabled)
            binding.settingsShowDrawerAppLabelsHolderView.beVisibleIf(enabled)
        }
        refresh()
        binding.settingsAppDrawerEnabledHolderView.setOnClickListener {

            if (launcherConfig.isDrawerEnabled) {
                launcherConfig.drawerStyleWhenOn = launcherConfig.drawerMode
                launcherConfig.drawerMode = TrayLayoutMode.NONE
            } else {
                launcherConfig.drawerMode = launcherConfig.drawerStyleWhenOn
            }
            refresh()
            prepareDrawerStyle()
        }
    }

    private fun prepareDrawerStyle() {
        val paged = launcherConfig.drawerMode == TrayLayoutMode.PAGED
        binding.settingsDrawerStyleView.text = getString(if (paged) R.string.launcher_drawer_style_paged else R.string.launcher_drawer_style_vertical)
        binding.settingsDrawerStyleHolderView.setOnClickListener {
            val items = arrayListOf(
                RadioItem(DRAWER_STYLE_VERTICAL, getString(R.string.launcher_drawer_style_vertical)),
                RadioItem(DRAWER_STYLE_PAGED, getString(R.string.launcher_drawer_style_paged)),
            )
            val current = if (launcherConfig.drawerMode == TrayLayoutMode.PAGED) DRAWER_STYLE_PAGED else DRAWER_STYLE_VERTICAL
            RadioGroupDialog(this, items, current) {
                launcherConfig.drawerMode = if (it as Int == DRAWER_STYLE_PAGED) TrayLayoutMode.PAGED else TrayLayoutMode.VERTICAL
                prepareDrawerStyle()
            }
        }
    }

    private fun prepareOpenKeyboardOnAppDrawer() {
        binding.settingsOpenKeyboardOnAppDrawerHolderView.beVisibleIf(launcherConfig.showSearchBar && launcherConfig.isDrawerEnabled)
        binding.settingsOpenKeyboardOnAppDrawerView.isChecked = launcherConfig.autoShowKeyboardInAppDrawer
        binding.settingsOpenKeyboardOnAppDrawerHolderView.setOnClickListener {
            binding.settingsOpenKeyboardOnAppDrawerView.toggle()
            launcherConfig.autoShowKeyboardInAppDrawer = binding.settingsOpenKeyboardOnAppDrawerView.isChecked
        }
    }

    private fun prepareCloseAppDrawerOnOtherAppOpen() {
        binding.settingsCloseAppDrawerOnOtherAppView.isChecked = launcherConfig.shutAppDrawer
        binding.settingsCloseAppDrawerOnOtherAppHolderView.setOnClickListener {
            binding.settingsCloseAppDrawerOnOtherAppView.toggle()
            launcherConfig.shutAppDrawer = binding.settingsCloseAppDrawerOnOtherAppView.isChecked
        }
    }

    private fun prepareDrawerColumnCount() {
        val currentColumnCount = launcherConfig.drawerColumnCount
        binding.settingsDrawerColumnCountView.text = currentColumnCount.toString()
        binding.settingsDrawerColumnCountHolderView.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in 1..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    launcherConfig.drawerColumnCount = newColumnCount
                    prepareDrawerColumnCount()
                }
            }
        }
    }

    private fun prepareDrawerSearchBar() {
        val showSearchBar = launcherConfig.showSearchBar
        binding.settingsShowSearchBarView.isChecked = showSearchBar
        binding.settingsDrawerSearchHolderView.setOnClickListener {
            binding.settingsShowSearchBarView.toggle()
            launcherConfig.showSearchBar = binding.settingsShowSearchBarView.isChecked
            binding.settingsOpenKeyboardOnAppDrawerHolderView.beVisibleIf(launcherConfig.showSearchBar && launcherConfig.isDrawerEnabled)
        }
    }

    private fun prepareShowDrawerAppLabels() {
        binding.settingsShowDrawerAppLabelsView.isChecked = launcherConfig.showDrawerAppLabels
        binding.settingsShowDrawerAppLabelsHolderView.setOnClickListener {
            binding.settingsShowDrawerAppLabelsView.toggle()
            launcherConfig.showDrawerAppLabels = binding.settingsShowDrawerAppLabelsView.isChecked
        }
    }

    private fun prepareHomeRowCount() {
        val currentRowCount = launcherConfig.homeRowCount
        binding.settingsHomeScreenRowCountView.text = currentRowCount.toString()
        binding.settingsHomeScreenRowCountHolderView.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_ROW_COUNT..MAX_ROW_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.row_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentRowCount) {
                val newRowCount = it as Int
                if (currentRowCount != newRowCount) {
                    launcherConfig.homeRowCount = newRowCount
                    prepareHomeRowCount()
                }
            }
        }
    }

    private fun prepareHomeColumnCount() {
        val currentColumnCount = launcherConfig.homeColumnCount
        binding.settingsHomeScreenColumnCountView.text = currentColumnCount.toString()
        binding.settingsHomeScreenColumnCountHolderView.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_COLUMN_COUNT..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    launcherConfig.homeColumnCount = newColumnCount
                    prepareHomeColumnCount()
                }
            }
        }
    }

    private fun prepareShowHomeAppLabels() {
        binding.settingsShowHomeAppLabelsView.isChecked = launcherConfig.showHomeAppLabels
        binding.settingsShowHomeAppLabelsHolderView.setOnClickListener {
            binding.settingsShowHomeAppLabelsView.toggle()
            launcherConfig.showHomeAppLabels = binding.settingsShowHomeAppLabelsView.isChecked
        }
    }

    @SuppressLint("NewApi")
    private fun prepareLanguage() {
        binding.settingsLanguageView.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolderView.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolderView.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun prepareManageHiddenIcons() {
        binding.settingsManageHiddenIconsHolderView.setOnClickListener {
            startActivity(Intent(this, ConcealedAppsPane::class.java))
        }
    }

    private fun fireAbout() {
        val licenses = 0L
        val faqItems = HelpIntentFilter.launcherFaqItems(this)

        startAboutActivity(
            appNameId = R.string.launcher_app_launcher_name,
            licenseMask = licenses,
            versionName = hostVersionName(),
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    private fun hostVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
