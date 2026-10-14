package com.find.phone.gps.location.tracker.screens.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLines
import com.find.phone.gps.location.tracker.databinding.PartMyNumberCardBinding
import com.find.phone.gps.location.tracker.databinding.CardMyNumberSimBinding
import com.find.phone.gps.location.tracker.databinding.CardHomeSimBinding
import com.find.phone.gps.location.tracker.databinding.PartHomeMyNumberBinding
import com.find.phone.gps.location.tracker.screens.simcard.OwnNumberActivity
import com.find.phone.gps.location.tracker.helpers.SimLineJournal

class OwnNumberTile private constructor(
    private val activity: AppCompatActivity,
    private val views: Views,
    private val prefs: SecurePrefsKeeper,
    private val requestPermissions: (Array<String>) -> Unit,
) {

    class SimRow(val root: View, val badge: TextView, val number: TextView, val region: TextView, val copy: ImageView)

    class Views(
        val root: View,
        val padManage: View,
        val padMyNumber: TextView,
        val lblMyNumberSub: TextView,
        val simRowsVw: LinearLayout,
        val prompt: View?,
        val inflateRow: (LayoutInflater, ViewGroup) -> SimRow,
        val styleBadge: (TextView, Boolean) -> Unit,
        val inkColor: Int = R.color.ds_ink_fixed,
        val mutedColor: Int = R.color.ds_muted_fixed,
    )

    constructor(
        activity: AppCompatActivity,
        views: PartMyNumberCardBinding,
        prefs: SecurePrefsKeeper,
        requestPermissions: (Array<String>) -> Unit,
    ) : this(
        activity,
        Views(
            views.root, views.padManage, views.padMyNumber, views.lblMyNumberSub, views.simRowsVw, null,
            { inflater, parent ->
                CardMyNumberSimBinding.inflate(inflater, parent, false).run {
                    SimRow(root, lblSimBadge, lblSimNumber, lblSimRegion, padCopySim)
                }
            },
            { badge, primary ->
                badge.setBackgroundResource(if (primary) R.drawable.vs_sim_badge_primary else R.drawable.vs_sim_badge_tint)
                badge.setTextColor(ContextCompat.getColor(badge.context, if (primary) R.color.white else R.color.ds_splash_blue))
            },
        ),
        prefs,
        requestPermissions,
    )

    constructor(
        activity: AppCompatActivity,
        views: PartHomeMyNumberBinding,
        prefs: SecurePrefsKeeper,
        requestPermissions: (Array<String>) -> Unit,
    ) : this(
        activity,
        Views(
            views.root, views.padManage, views.padMyNumber, views.lblMyNumberSub, views.simRowsVw, views.promptCardVw,
            { inflater, parent ->
                CardHomeSimBinding.inflate(inflater, parent, false).run {
                    SimRow(root, lblSimBadge, lblSimNumber, lblSimRegion, padCopySim)
                }
            },
            { badge, primary ->
                badge.setBackgroundResource(if (primary) R.drawable.hb_badge_sim1_r6 else R.drawable.hb_badge_sim2_r6)
                badge.setTextColor(ContextCompat.getColor(badge.context, R.color.white))
            },
            R.color.hb_ink,
            R.color.hb_muted,
        ),
        prefs,
        requestPermissions,
    )

    init {
        views.padMyNumber.setOnClickListener { onButton() }
        views.padManage.setOnClickListener {
            activity.trackEvent("MyNumber_Manage_Click")
            activity.startActivity(OwnNumberActivity.intent(activity))
        }
    }

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

        views.padManage.isVisible = showing
        views.padMyNumber.setText(if (isPermanentlyDenied()) R.string.my_number_settings else R.string.my_number_show)

        views.lblMyNumberSub.isVisible = !showing
        views.prompt?.isVisible = !showing
        views.lblMyNumberSub.setText(R.string.my_number_sub)

        views.simRowsVw.removeAllViews()
        views.simRowsVw.isVisible = showing
        val inflater = LayoutInflater.from(views.root.context)
        lines.forEach { line -> views.simRowsVw.addView(wireRow(inflater, context, line)) }
    }

    fun onPermissionResult(result: Map<String, Boolean>) {
        val context: Context = activity
        result.forEach { (permission, granted) -> context.trackPermissionResult(permission, granted) }
        if (OwnSimLines.isGranted(context)) SimLineJournal.log(context)
        render()
    }

    private fun onButton() {
        val context: Context = activity
        context.trackEvent("MyNumber_Show_Click")
        val permission = OwnSimLines.PERMISSIONS.first()
        when {
            OwnSimLines.isGranted(context) -> render()
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                requestPermissions(OwnSimLines.PERMISSIONS)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> requestPermissions(OwnSimLines.PERMISSIONS)
            else -> unfoldAppSettings(context)
        }
    }

    private fun isPermanentlyDenied(): Boolean {
        val permission = OwnSimLines.PERMISSIONS.first()
        return !OwnSimLines.isGranted(activity) &&
            prefs.hasRequestedPermission(permission) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    private fun unfoldAppSettings(context: Context) {
        Toast.makeText(context, R.string.my_number_denied, Toast.LENGTH_LONG).show()
        ResumeAdKeeper.skipNextAppOpenAd = true
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }
    }

    private fun wireRow(inflater: LayoutInflater, context: Context, line: OwnSimLines.SimSlotLine): View {
        val row = views.inflateRow(inflater, views.simRowsVw)
        val primarySlot = line.slot <= 1
        row.badge.text = context.getString(R.string.my_number_sim_badge, line.slot)
        views.styleBadge(row.badge, primarySlot)

        val number = line.number
        if (number != null) {
            row.number.text = number
            row.number.setTextColor(ContextCompat.getColor(context, views.inkColor))
            row.copy.setOnClickListener { copy(context, number) }
        } else {
            row.number.setText(R.string.my_number_not_stored)
            row.number.setTextColor(ContextCompat.getColor(context, views.mutedColor))
        }
        row.copy.isVisible = number != null

        val region = line.region
        row.region.text = if (!region.isNullOrBlank()) "${line.carrier} · $region" else line.carrier
        row.region.isVisible = row.region.text.isNotBlank()
        return row.root
    }

    private fun copy(context: Context, number: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.my_number_title), number))
        Toast.makeText(context, R.string.my_number_copied, Toast.LENGTH_SHORT).show()
        context.trackEvent("MyNumber_Copy")
    }
}
