package com.find.phone.gps.location.tracker.monetize.render

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.BlockingLoaderDialog
import com.find.phone.gps.location.tracker.R

object ColdStartLoader {

    private var dialog: Dialog? = null

    fun show(activity: Activity, packageName: String?) {
        if (!BlockingLoaderDialog.isEnabled(activity)) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (packageName.isNullOrBlank()) return BlockingLoaderDialog.show(activity, true)

        hide()
        runCatching {
            dialog = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                setContentView(R.layout.part_app_launch_loader)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

                val pm = activity.packageManager
                val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
                findViewById<ImageView>(R.id.launchLoaderIconVw).setImageDrawable(
                    info?.let { pm.getApplicationIcon(it) } ?: pm.defaultActivityIcon
                )
                findViewById<TextView>(R.id.launchLoaderNameVw).text =
                    info?.let { pm.getApplicationLabel(it) } ?: ""

                show()
            }
        }.onFailure { Log.e("ColdStartLoader", "could not show loader", it) }
    }

    fun hide() {
        runCatching {
            dialog?.takeIf { it.isShowing }?.let { d ->
                val owner = d.ownerActivity
                if (owner == null || (!owner.isFinishing && !owner.isDestroyed)) d.dismiss()
            }
        }
        dialog = null
        BlockingLoaderDialog.hide()
    }
}
