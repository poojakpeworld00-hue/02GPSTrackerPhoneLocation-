package io.homedeck.grid.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyGridLayoutManager
import io.homedeck.grid.lists.ConcealedAppsAdapter
import io.homedeck.grid.databinding.HdkScreenHiddenIconsBinding
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.support.GlyphMasker
import io.homedeck.grid.ext.getDrawableForPackageName
import io.homedeck.grid.ext.hiddenIconsDB
import io.homedeck.grid.entities.ConcealedApp

class ConcealedAppsPane : HomeFoundationPane(), RefreshRecyclerViewListener {
    private val binding by viewBinding(HdkScreenHiddenIconsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateIcons()

        setupEdgeToEdge(padBottomSystem = listOf(binding.manageHiddenIconsListView))
        setupMaterialScrollListener(binding.manageHiddenIconsListView, binding.manageHiddenIconsAppbarView)

        val layoutManager = binding.manageHiddenIconsListView.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = launcherConfig.drawerColumnCount
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageHiddenIconsAppbarView, NavigationIcon.Arrow)
    }

    private fun updateIcons() {
        ensureBackgroundThread {
            val hiddenIcons = hiddenIconsDB.getHiddenIcons().sortedWith(
                compareBy({
                    it.title.normalizeString().lowercase()
                }, {
                    it.packageName
                })
            ).toMutableList() as ArrayList<ConcealedApp>

            val hiddenIconsEmpty = hiddenIcons.isEmpty()
            runOnUiThread {
                binding.manageHiddenIconsPlaceholderView.beVisibleIf(hiddenIconsEmpty)
            }

            if (hiddenIcons.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_MAIN, null)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)

                val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
                for (info in list) {
                    val componentInfo = info.activityInfo.applicationInfo
                    val packageName = componentInfo.packageName
                    val activityName = info.activityInfo.name
                    hiddenIcons.firstOrNull { it.getIconIdentifier() == "$packageName/$activityName" }?.apply {
                        drawable = info.loadIcon(packageManager)?.let { GlyphMasker.shape(it, launcherConfig.legacyIconTray) }
                            ?: getDrawableForPackageName(packageName)
                    }
                }

                hiddenIcons.firstOrNull { it.packageName == applicationContext.packageName }?.apply {
                    drawable = getDrawableForPackageName(packageName)
                }
            }

            val iconsToRemove = hiddenIcons.filter { it.drawable == null }
            if (iconsToRemove.isNotEmpty()) {
                hiddenIconsDB.removeHiddenIcons(iconsToRemove)
                hiddenIcons.removeAll(iconsToRemove)
            }

            runOnUiThread {
                ConcealedAppsAdapter(this, hiddenIcons, this, binding.manageHiddenIconsListView) {
                }.apply {
                    binding.manageHiddenIconsListView.adapter = this
                }
            }
        }
    }

    override fun refreshItems() {
        updateIcons()
    }
}
