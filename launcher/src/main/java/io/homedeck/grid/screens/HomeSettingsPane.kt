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
        setupOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbarView, NavigationIcon.Arrow)
        refreshMenuItems()

        setupCustomizeColors()
        setupUseEnglish()
        setupDoubleTapToLock()
        setupAppDrawerEnabled()
        setupDrawerStyle()
        setupCloseAppDrawerOnOtherAppOpen()
        setupOpenKeyboardOnAppDrawer()
        setupDrawerColumnCount()
        setupDrawerSearchBar()
        setupShowDrawerAppLabels()
        setupHomeRowCount()
        setupHomeColumnCount()
        setupShowHomeAppLabels()
        setupLanguage()
        setupManageHiddenIcons()
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

    private fun setupOptionsMenu() {
        binding.settingsToolbarView.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.aboutView -> launchAbout()
                R.id.more_apps_from_usView -> launchMoreAppsFromUsIntent()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbarView.menu.apply {
            findItem(R.id.more_apps_from_usView).isVisible =
                !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolderView.setOnClickListener {
            // see HomeFoundationPane.withFossifyPackageNameSpoofed — startCustomizationActivity()
            // carries its own copy of the anti-rebrand check, separate from the one in onCreate()
            withFossifyPackageNameSpoofed { startCustomizationActivity() }
        }
    }

    private fun setupUseEnglish() {
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

    private fun setupDoubleTapToLock() {
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

    /**
     * The drawer on/off switch. Off is the classic no-drawer home: HomeDeskPane puts every app on
     * a page the next time it resumes and stops answering the swipe-up; the drawer's own rows are
     * hidden here because nothing reads them. Icons already on the pages are left where they are
     * when the drawer is turned back on.
     */
    private fun setupAppDrawerEnabled() {
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
            // Turning the drawer back on restores the style it had before it went off.
            if (launcherConfig.isDrawerEnabled) {
                launcherConfig.drawerStyleWhenOn = launcherConfig.drawerMode
                launcherConfig.drawerMode = TrayLayoutMode.NONE
            } else {
                launcherConfig.drawerMode = launcherConfig.drawerStyleWhenOn
            }
            refresh()
            setupDrawerStyle()
        }
    }

    private fun setupDrawerStyle() {
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
                setupDrawerStyle()
            }
        }
    }

    private fun setupOpenKeyboardOnAppDrawer() {
        binding.settingsOpenKeyboardOnAppDrawerHolderView.beVisibleIf(launcherConfig.showSearchBar && launcherConfig.isDrawerEnabled)
        binding.settingsOpenKeyboardOnAppDrawerView.isChecked = launcherConfig.autoShowKeyboardInAppDrawer
        binding.settingsOpenKeyboardOnAppDrawerHolderView.setOnClickListener {
            binding.settingsOpenKeyboardOnAppDrawerView.toggle()
            launcherConfig.autoShowKeyboardInAppDrawer = binding.settingsOpenKeyboardOnAppDrawerView.isChecked
        }
    }

    private fun setupCloseAppDrawerOnOtherAppOpen() {
        binding.settingsCloseAppDrawerOnOtherAppView.isChecked = launcherConfig.closeAppDrawer
        binding.settingsCloseAppDrawerOnOtherAppHolderView.setOnClickListener {
            binding.settingsCloseAppDrawerOnOtherAppView.toggle()
            launcherConfig.closeAppDrawer = binding.settingsCloseAppDrawerOnOtherAppView.isChecked
        }
    }

    private fun setupDrawerColumnCount() {
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
                    setupDrawerColumnCount()
                }
            }
        }
    }

    private fun setupDrawerSearchBar() {
        val showSearchBar = launcherConfig.showSearchBar
        binding.settingsShowSearchBarView.isChecked = showSearchBar
        binding.settingsDrawerSearchHolderView.setOnClickListener {
            binding.settingsShowSearchBarView.toggle()
            launcherConfig.showSearchBar = binding.settingsShowSearchBarView.isChecked
            binding.settingsOpenKeyboardOnAppDrawerHolderView.beVisibleIf(launcherConfig.showSearchBar && launcherConfig.isDrawerEnabled)
        }
    }

    private fun setupShowDrawerAppLabels() {
        binding.settingsShowDrawerAppLabelsView.isChecked = launcherConfig.showDrawerAppLabels
        binding.settingsShowDrawerAppLabelsHolderView.setOnClickListener {
            binding.settingsShowDrawerAppLabelsView.toggle()
            launcherConfig.showDrawerAppLabels = binding.settingsShowDrawerAppLabelsView.isChecked
        }
    }

    private fun setupHomeRowCount() {
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
                    setupHomeRowCount()
                }
            }
        }
    }

    private fun setupHomeColumnCount() {
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
                    setupHomeColumnCount()
                }
            }
        }
    }

    private fun setupShowHomeAppLabels() {
        binding.settingsShowHomeAppLabelsView.isChecked = launcherConfig.showHomeAppLabels
        binding.settingsShowHomeAppLabelsHolderView.setOnClickListener {
            binding.settingsShowHomeAppLabelsView.toggle()
            launcherConfig.showHomeAppLabels = binding.settingsShowHomeAppLabelsView.isChecked
        }
    }

    @SuppressLint("NewApi")
    private fun setupLanguage() {
        binding.settingsLanguageView.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolderView.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolderView.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupManageHiddenIcons() {
        binding.settingsManageHiddenIconsHolderView.setOnClickListener {
            startActivity(Intent(this, ConcealedAppsPane::class.java))
        }
    }

    private fun launchAbout() {
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

    /**
     * The host app version, shown on the About screen.
     *
     * Read off the package manager rather than a BuildConfig constant: the module has its own
     * BuildConfig, and its version is the launcher library version, which is not what a user
     * looking at "About" is asking for.
     */
    private fun hostVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
