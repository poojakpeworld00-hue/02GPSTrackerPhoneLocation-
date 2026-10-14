package com.find.phone.gps.location.tracker.screens.consent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.render.TipSheetActivity
import io.homedeck.grid.ext.isDefaultLauncher

object OverlayToolbox {

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun promptForOverlay(context: Context): Boolean =
        AdPrefsStore.getInstance(context).getBoolean(OVERLAY_SWITCH_KEY, true)

    const val OVERLAY_SWITCH_KEY = "Overlay_Permission_Show"

    fun isOfferable(context: Context): Boolean {
        if (!promptForOverlay(context)) return false
        if (runCatching { context.isDefaultLauncher() }.getOrDefault(false)) return false
        return !AdPrefsStore.getInstance(context).isNShowLocation
    }

    fun composeOverlayIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).addFlags(
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        )

    fun presentGuide(context: Context) =
        TipSheetActivity.show(context, TipSheetActivity.MODE_OVERLAY)
}
