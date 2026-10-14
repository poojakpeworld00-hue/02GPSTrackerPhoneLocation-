package com.find.phone.gps.location.tracker.screens.appevent

import android.Manifest
import android.animation.ObjectAnimator
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.find.phone.gps.location.tracker.monetize.engine.HomeSlotAds
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsor
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.PagePackageResultBinding
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.helpers.OsDialogAssist

class InstallOutcomeActivity : FoundationActivity<PagePackageResultBinding>() {

    override val layoutId: Int = R.layout.page_package_result

    private val installed: Boolean by lazy { intent.getBooleanExtra(EXTRA_INSTALLED, true) }
    private val affectedPackage: String by lazy { intent.getStringExtra(EXTRA_PACKAGE).orEmpty() }

    private val cachedMeta by lazy {
        if (installed) null else InstallInfoMemo.read(this, affectedPackage)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var closed = false

    override fun initView() {

        lifecycle.addObserver(OsDialogAssist(this) { finishAndRemoveTask() })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        imprintInsets()
        wireHeader()
        wireButtons()
        populateAd()
        playEntrance()
        runCheck()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun imprintInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.pkgresContentNode) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.pkgresHeroNode.updatePadding(top = bars.top)
            (binding.pkgresActionsNode.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                ?.let {
                    it.bottomMargin = dp(16) + bars.bottom
                    binding.pkgresActionsNode.layoutParams = it
                }
            insets
        }
        ViewCompat.requestApplyInsets(binding.pkgresContentNode)
    }

    private fun wireHeader() = with(binding) {
        pkgresHeroNode.setBackgroundResource(R.drawable.form_pkgres_hero_installed)
        pkgresBadgeNode.setBackgroundResource(R.drawable.form_pkgres_badge_installed)
        pkgresBadgeIconNode.setImageResource(if (installed) R.drawable.icn_shield_check else R.drawable.icn_check)
        pkgresStatusNode.apply {
            setBackgroundResource(R.drawable.form_pkgres_status_installed)
            setText(if (installed) R.string.pkgres_privacy_status else R.string.pkgres_optimize_status)
            setTextColor(getColor(R.color.pkgres_installed))
        }
        pkgresTitleNode.text = getString(
            if (installed) R.string.pkgres_privacy_title else R.string.pkgres_optimize_title,
            settleLabel(),
        )
        pkgresSubtitleNode.text = ""

        val icon = if (installed) {
            runCatching { packageManager.getApplicationIcon(affectedPackage) }.getOrNull()
        } else {
            cachedMeta?.iconFile
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                ?.let { BitmapDrawable(resources, it) }
        }
        pkgresAppIconNode.setImageDrawable(icon)
    }

    private fun settleLabel(): String {
        if (installed) {
            runCatching {
                val info = packageManager.getApplicationInfo(affectedPackage, 0)
                return packageManager.getApplicationLabel(info).toString()
            }
        }
        return cachedMeta?.label?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: affectedPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    private fun runCheck() {
        val steps = if (installed) {
            listOf(R.string.pkgres_privacy_step_read, R.string.pkgres_privacy_step_sensitive, R.string.pkgres_privacy_step_allowed)
        } else {
            listOf(R.string.pkgres_optimize_step_removed, R.string.pkgres_optimize_step_storage, R.string.pkgres_optimize_step_memory)
        }
        steps.forEachIndexed { i, step ->
            handler.postDelayed({
                binding.pkgresStepNode.setText(step)
                binding.pkgresProgressBarNode.setProgressCompat((i + 1) * 100 / steps.size, true)
            }, STEP_MS * i)
        }
        handler.postDelayed({ presentResults() }, STEP_MS * steps.size)
    }

    private fun presentResults() {
        if (isFinishing || isDestroyed) return
        if (installed) presentPrivacyResults() else presentOptimizeResults()
        binding.pkgresProgressNode.visibility = View.GONE

        if (binding.pkgresChecksNode.childCount > 0) binding.pkgresChecksNode.apply {
            visibility = View.VISIBLE
            alpha = 0f
            animate().alpha(1f).setDuration(260L).start()
        }
        binding.pkgresOpenNode.isEnabled = true
    }

    private fun presentPrivacyResults() {
        val groups = sensitiveGroups()
        val allowed = groups.count { it.second }
        binding.pkgresTitleNode.text = getString(R.string.pkgres_privacy_done_title, settleLabel())
        binding.pkgresSubtitleNode.text = if (groups.isEmpty()) {
            getString(R.string.pkgres_privacy_summary_none)
        } else {
            getString(R.string.pkgres_privacy_summary, groups.size, allowed)
        }
        groups.forEach { (label, isAllowed) ->
            addRow(
                getString(label),
                getString(if (isAllowed) R.string.pkgres_privacy_allowed else R.string.pkgres_privacy_not_allowed),
                if (isAllowed) R.color.pkgres_removed else R.color.pkgres_installed,
            )
        }
    }

    private fun presentOptimizeResults() {
        binding.pkgresTitleNode.text = getString(R.string.pkgres_optimize_done_title, settleLabel())
        binding.pkgresSubtitleNode.setText(R.string.pkgres_optimize_summary)
        addRow(getString(R.string.pkgres_optimize_row_removed), getString(R.string.pkgres_optimize_removed), R.color.pkgres_installed)

        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            addRow(
                getString(R.string.pkgres_optimize_row_storage),
                getString(R.string.pkgres_of_total, size(stat.availableBytes), size(stat.totalBytes)),
                R.color.pkgres_installed,
            )
        }
        runCatching {
            val info = ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            addRow(
                getString(R.string.pkgres_optimize_row_memory),
                getString(R.string.pkgres_of_total, size(info.availMem), size(info.totalMem)),
                R.color.pkgres_installed,
            )
        }
    }

    private fun sensitiveGroups(): List<Pair<Int, Boolean>> {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    affectedPackage, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(affectedPackage, PackageManager.GET_PERMISSIONS)
            }
        }.getOrNull() ?: return emptyList()

        val requested = info.requestedPermissions ?: return emptyList()
        val granted = requested.filterIndexed { i, _ -> isGranted(info, i) }.toSet()
        return SENSITIVE.mapNotNull { (label, permissions) ->
            val asked = permissions.filter { it in requested }
            if (asked.isEmpty()) null else label to asked.any { it in granted }
        }
    }

    private fun isGranted(info: PackageInfo, index: Int): Boolean {
        val flags = info.requestedPermissionsFlags ?: return false
        return index < flags.size && flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
    }

    private fun addRow(title: String, value: String, dotColor: Int) {
        val row = LayoutInflater.from(this).inflate(R.layout.card_pkgres_check, binding.pkgresChecksNode, false)
        row.findViewById<TextView>(R.id.pkgresRowTitleNode).text = title
        row.findViewById<TextView>(R.id.pkgresRowValueNode).text = value
        row.findViewById<View>(R.id.pkgresRowDotNode).background.mutate().setTint(getColor(dotColor))
        binding.pkgresChecksNode.addView(row)
    }

    private fun size(bytes: Long): String = Formatter.formatShortFileSize(this, bytes)

    private fun wireButtons() = with(binding) {
        pkgresCloseNode.setOnClickListener { close() }
        pkgresDoneNode.setOnClickListener { close() }
        if (installed) {

            pkgresOpenNode.setText(R.string.pkgres_privacy_review)
            pkgresOpenNode.isEnabled = false
            pkgresOpenNode.setOnClickListener { unfoldNewAppSettings() }
        } else {

            pkgresOpenNode.visibility = View.GONE
            (pkgresDoneNode.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                it.marginEnd = 0
                pkgresDoneNode.layoutParams = it
            }
        }
    }

    private fun unfoldNewAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", affectedPackage, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finishSafely()
    }

    private fun playEntrance() = with(binding) {
        pkgresAppIconNode.scaleX = 0.6f
        pkgresAppIconNode.scaleY = 0.6f
        pkgresAppIconNode.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(2.2f))
            .setDuration(420L)
            .start()
        pkgresBadgeNode.alpha = 0f
        pkgresBadgeNode.animate().alpha(1f).setStartDelay(260L).setDuration(220L).start()
        listOf<View>(pkgresStatusNode, pkgresTitleNode, pkgresSubtitleNode, pkgresBodyNode)
            .forEachIndexed { index, view ->
                view.alpha = 0f
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, dp(12).toFloat(), 0f).apply {
                    startDelay = 90L * index
                    duration = 320L
                    start()
                }
                view.animate().alpha(1f).setStartDelay(90L * index).setDuration(320L).start()
            }
    }

    private fun populateAd() {
        if (!HomeAdsSettings.packageResultSettings(this).bodyNative) return
        binding.adNativeFrameVw.visibility = View.VISIBLE
        InlineSponsor().presentMidNative2(this, binding.adNativeFrameVw, binding.adShimmerVw)
    }

    private fun close() {
        if (closed) return
        closed = true
        binding.pkgresCloseNode.isEnabled = false
        binding.pkgresDoneNode.isEnabled = false
        binding.pkgresOpenNode.isEnabled = false

        val placement = if (installed) "install" else "uninstall"
        if (HomeSlotAds.hasOwnFlow(this, placement)) {
            HomeSlotAds.showInterstitial(this, placement) { finishSafely() }
            return
        }
        finishSafely()
    }

    override fun performBack() = close()

    private fun finishSafely() {
        if (!isFinishing) finish()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        const val EXTRA_INSTALLED = "package_installed"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_LABEL = "package_label"
        const val EXTRA_VERSION = "package_version"
        const val EXTRA_CLASS = "package_class"

        private const val STEP_MS = 700L

        private val SENSITIVE: List<Pair<Int, List<String>>> = listOf(
            R.string.pkgres_perm_location to listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "android.permission.ACCESS_BACKGROUND_LOCATION",
            ),
            R.string.pkgres_perm_camera to listOf(Manifest.permission.CAMERA),
            R.string.pkgres_perm_microphone to listOf(Manifest.permission.RECORD_AUDIO),
            R.string.pkgres_perm_contacts to listOf(
                Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS,
            ),
            R.string.pkgres_perm_sms to listOf(
                Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
            ),
            R.string.pkgres_perm_call_log to listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
            R.string.pkgres_perm_phone to listOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, "android.permission.READ_PHONE_NUMBERS",
            ),
            R.string.pkgres_perm_media to listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
            ),
            R.string.pkgres_perm_calendar to listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            R.string.pkgres_perm_sensors to listOf(Manifest.permission.BODY_SENSORS),
            R.string.pkgres_perm_nearby to listOf(
                "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES",
            ),
            R.string.pkgres_perm_notifications to listOf("android.permission.POST_NOTIFICATIONS"),
        )
    }
}
