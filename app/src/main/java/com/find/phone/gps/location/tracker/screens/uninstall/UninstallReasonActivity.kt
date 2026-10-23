package com.find.phone.gps.location.tracker.screens.uninstall

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.databinding.CardUninstallReasonBinding
import com.find.phone.gps.location.tracker.databinding.PageUninstallReasonBinding
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResults
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionShow
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens

/**
 * Exit survey: six reasons, the first preselected. A reason the app can fix shows a tip
 * (location permission while not granted; notification settings). "Keep app" leads; "Uninstall
 * anyway" and Back continue.
 */
class UninstallReasonActivity : UninstallPage<PageUninstallReasonBinding>() {

    override val layoutId: Int = R.layout.page_uninstall_reason
    override val page = UninstallConfig.PAGE_REASON

    private class Tip(
        @StringRes val title: Int, @StringRes val text: Int, @StringRes val button: Int, val event: String,
        val applies: (Context) -> Boolean, val action: (UninstallReasonActivity) -> Unit,
    )

    private class Reason(
        @StringRes val label: Int, @DrawableRes val icon: Int, @DrawableRes val tile: Int, @ColorRes val tint: Int,
        val tip: Tip? = null,
    )

    private val reasons = listOf(
        Reason(
            R.string.un_reason_1, R.drawable.vs_ic_my_location, R.drawable.vs_tile_tint_r12, R.color.ds_primary,
            Tip(
                R.string.un_tip_location_title, R.string.un_tip_location_text, R.string.un_tip_location_btn, "location",
                applies = { !hasLocation(it) }, action = { it.requestLocation() },
            ),
        ),
        Reason(
            R.string.un_reason_2, R.drawable.ic_un_g_notifications_off, R.drawable.vs_tile_amber_r12, R.color.ds_warn,
            Tip(
                R.string.un_tip_notif_title, R.string.un_tip_notif_text, R.string.un_tip_notif_btn, "notif",
                applies = { true }, action = { it.openNotificationSettings() },
            ),
        ),
        Reason(R.string.un_reason_3, R.drawable.ic_un_g_block, R.drawable.vs_tile_purple_r12, R.color.ds_purple),
        Reason(R.string.un_reason_4, R.drawable.ic_un_g_hourglass, R.drawable.vs_tile_gray_r12, R.color.ds_ink2),
        Reason(R.string.un_reason_5, R.drawable.ic_un_g_battery_alert, R.drawable.vs_tile_green_r12, R.color.ds_success),
        Reason(R.string.un_reason_6, R.drawable.ic_un_g_swap, R.drawable.vs_tile_tint_r12, R.color.ds_primary),
    )

    private val rows = mutableListOf<CardUninstallReasonBinding>()
    private var selected = 0
    private var shownTip: Tip? = null
    private var leaving = false

    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        trackPermissionResults(it)
        updateTip()
    }

    override fun initPage() {
        reasons.forEachIndexed { i, reason ->
            val row = CardUninstallReasonBinding.inflate(layoutInflater, binding.reasonListVw, false)
            row.imgIcon.setImageResource(reason.icon)
            row.imgIcon.setBackgroundResource(reason.tile)
            row.imgIcon.imageTintList = ContextCompat.getColorStateList(this, reason.tint)
            row.lblLabel.setText(reason.label)
            row.root.setOnClickListener { if (i != selected) { selected = i; paint() } }
            binding.reasonListVw.addView(row.root)
            rows += row
        }
        paint()

        binding.padKeepApp.setOnClickListener {
            track("un_keep_app")
            UninstallFlow.keepApp(this)
        }
        binding.padUninstallAnyway.setOnClickListener {
            track("un_uninstall_anyway")
            onFunnelBack()
        }
        binding.padTip.setOnClickListener {
            val tip = shownTip ?: return@setOnClickListener
            track("un_tip_${tip.event}")
            tip.action(this)
        }
        VisualTweens.pressScale(binding.padKeepApp)
    }

    override fun onResume() {
        super.onResume()
        // back from Settings or the permission dialog, the tip may be moot
        if (rows.isNotEmpty()) updateTip()
    }

    private fun paint() {
        rows.forEachIndexed { i, row ->
            val on = i == selected
            row.root.setBackgroundResource(if (on) R.drawable.vs_row_selected_r14 else 0)
            row.radio.setBackgroundResource(if (on) R.drawable.vs_radio_on else R.drawable.vs_radio_off)
            row.root.isSelected = on
        }
        updateTip()
    }

    private fun updateTip() {
        val tip = reasons[selected].tip?.takeIf { it.applies(this) }
        shownTip = tip
        binding.tipCardVw.visibility = if (tip == null) View.GONE else View.VISIBLE
        tip ?: return
        binding.lblTip.text = SpannableStringBuilder().apply {
            append(getString(tip.title), StyleSpan(android.graphics.Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" ")
            append(getString(tip.text))
        }
        binding.padTip.setText(tip.button)
    }

    private fun requestLocation() {
        LOCATION.forEach { trackPermissionShow(it) }
        locationLauncher.launch(LOCATION)
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
        }
        ResumeAdKeeper.skipNextAppOpenAd = true
        runCatching { startActivity(intent) }.onFailure { ResumeAdKeeper.skipNextAppOpenAd = false }
    }

    override fun onFunnelBack() {
        if (leaving) return
        leaving = true
        track("un_reason_${selected + 1}")
        val reason = getString(reasons[selected].label)
        val next = if (advanced) UninstallProgressActivity.newIntent(this, reason)
        else UninstallThanksActivity.newIntent(this, advanced = false, reason = reason)
        UninstallAds.showInterThen(this, page) {
            startActivity(next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    companion object {
        private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        private fun hasLocation(context: Context) = LOCATION.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        fun newIntent(ctx: Context, advanced: Boolean): Intent =
            Intent(ctx, UninstallReasonActivity::class.java).putExtra(UninstallFlow.EXTRA_ADVANCED, advanced)
    }
}
