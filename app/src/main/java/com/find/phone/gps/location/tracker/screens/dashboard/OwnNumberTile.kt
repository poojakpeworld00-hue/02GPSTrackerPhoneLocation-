package com.find.phone.gps.location.tracker.screens.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.logKeyEvent
import com.find.phone.gps.location.tracker.monetize.engine.logPermissionResult
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLines
import com.find.phone.gps.location.tracker.databinding.PartMyNumberCardBinding
import com.find.phone.gps.location.tracker.databinding.CardMyNumberSimBinding
import com.find.phone.gps.location.tracker.screens.simcard.OwnNumberActivity
import com.find.phone.gps.location.tracker.helpers.SimLineJournal

/**
 * The "My number" card in the drawer header (design 1a, "SIMs visible"): the phone's own SIMs with
 * their number, carrier and region (no name).
 *
 * Phone access is asked here, when the user taps the card's button, so the system dialog appears
 * next to the feature that needs it. Denied twice (Android stops showing its dialog), the button
 * opens this app's settings instead. The card is hidden on a phone with no ready SIM.
 *
 * The host activity owns the permission launcher (it must be registered before the view exists)
 * and forwards its result to [onPermissionResult].
 */
class OwnNumberTile(
    private val activity: AppCompatActivity,
    private val views: PartMyNumberCardBinding,
    private val prefs: SecurePrefsKeeper,
    private val requestPermissions: (Array<String>) -> Unit,
) {

    init {
        views.padMyNumber.setOnClickListener { onButton() }
        views.padManage.setOnClickListener {
            activity.logKeyEvent("MyNumber_Manage_Click")
            activity.startActivity(OwnNumberActivity.intent(activity))
        }
    }

    /** Redraws from the current SIM / permission state; call on view creation and on resume. */
    fun render() {
        val context: Context = activity
        if (!OwnSimLines.hasReadySim(context)) {
            views.root.isVisible = false
            return
        }
        views.root.isVisible = true

        val lines = OwnSimLines.read(context)
        val showing = lines.isNotEmpty()
        views.padMyNumber.isVisible = !showing
        // SIMs visible → offer "Manage" (the My number page); otherwise the "Show" button.
        views.padManage.isVisible = showing
        views.padMyNumber.setText(if (isPermanentlyDenied()) R.string.my_number_settings else R.string.my_number_show)
        // Only the "ask for access" state needs the subtitle; once the SIMs show, the rows say it all.
        views.lblMyNumberSub.isVisible = !showing
        views.lblMyNumberSub.setText(R.string.my_number_sub)

        views.simRowsVw.removeAllViews()
        views.simRowsVw.isVisible = showing
        val inflater = LayoutInflater.from(views.root.context)
        lines.forEach { line -> views.simRowsVw.addView(bindRow(inflater, context, line)) }
    }

    fun onPermissionResult(result: Map<String, Boolean>) {
        val context: Context = activity
        result.forEach { (permission, granted) -> context.logPermissionResult(permission, granted) }
        if (OwnSimLines.isGranted(context)) SimLineJournal.log(context)
        render()
    }

    private fun onButton() {
        val context: Context = activity
        context.logKeyEvent("MyNumber_Show_Click")
        val permission = OwnSimLines.PERMISSIONS.first()
        when {
            OwnSimLines.isGranted(context) -> render()
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                requestPermissions(OwnSimLines.PERMISSIONS)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> requestPermissions(OwnSimLines.PERMISSIONS)
            else -> openAppSettings(context)
        }
    }

    /** Asked before and Android no longer shows its dialog: only Settings can grant it now. */
    private fun isPermanentlyDenied(): Boolean {
        val permission = OwnSimLines.PERMISSIONS.first()
        return !OwnSimLines.isGranted(activity) &&
            prefs.hasRequestedPermission(permission) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    private fun openAppSettings(context: Context) {
        Toast.makeText(context, R.string.my_number_denied, Toast.LENGTH_LONG).show()
        ResumeAdKeeper.skipNextAppOpenAd = true
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }
    }

    private fun bindRow(inflater: LayoutInflater, context: Context, line: OwnSimLines.SimSlotLine) =
        CardMyNumberSimBinding.inflate(inflater, views.simRowsVw, false).apply {
            // Badge "SIM1" / "SIM2": slot 1 filled primary, the rest a light tint.
            val primarySlot = line.slot <= 1
            lblSimBadge.text = context.getString(R.string.my_number_sim_badge, line.slot)
            lblSimBadge.setBackgroundResource(
                if (primarySlot) R.drawable.vs_sim_badge_primary else R.drawable.vs_sim_badge_tint
            )
            lblSimBadge.setTextColor(
                ContextCompat.getColor(context, if (primarySlot) R.color.white else R.color.ds_splash_blue)
            )

            val number = line.number
            if (number != null) {
                lblSimNumber.text = number
                lblSimNumber.setTextColor(ContextCompat.getColor(context, R.color.ds_ink_fixed))
                padCopySim.setOnClickListener { copy(context, number) }
            } else {
                lblSimNumber.setText(R.string.my_number_not_stored)
                lblSimNumber.setTextColor(ContextCompat.getColor(context, R.color.ds_muted_fixed))
            }
            padCopySim.isVisible = number != null

            // Secondary line: "carrier · region", e.g. "Jio · Gujarat, India".
            val region = line.region
            lblSimRegion.text = if (!region.isNullOrBlank()) "${line.carrier} · $region" else line.carrier
            lblSimRegion.isVisible = lblSimRegion.text.isNotBlank()
        }.root

    private fun copy(context: Context, number: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.my_number_title), number))
        Toast.makeText(context, R.string.my_number_copied, Toast.LENGTH_SHORT).show()
        context.logKeyEvent("MyNumber_Copy")
    }
}
