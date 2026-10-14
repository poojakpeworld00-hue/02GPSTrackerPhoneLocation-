package com.find.phone.gps.location.tracker.grants

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore

object GrantToolbox {

    val CATALOG: Map<String, GrantDescriptor> = listOf(
        GrantDescriptor(
            key = "notification",
            androidPermission = Manifest.permission.POST_NOTIFICATIONS,
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
    ).associateBy { it.key }

    fun spec(key: String): GrantDescriptor? = CATALOG[key]

    fun isApplicableOnThisSdk(spec: GrantDescriptor): Boolean =
        Build.VERSION.SDK_INT >= spec.minSdk

    fun isPrefGateOpen(context: Context, spec: GrantDescriptor): Boolean {
        val gate = spec.enabledPrefGate ?: return true
        return AdPrefsStore.getInstance(context).getBoolean(gate)
    }

    fun isOfferable(context: Context, key: String): Boolean {
        val spec = spec(key) ?: return false
        if (!isApplicableOnThisSdk(spec)) return false
        if (!isPrefGateOpen(context, spec)) return false
        val rule = runCatching { GrantOrigin.rules().firstOrNull { it.key == key } }.getOrNull()
            ?: return true
        if (!rule.enabled) return false

        if (rule.showOnce && GrantLedger(context).wasShown(key)) return false
        return true
    }

    fun isGranted(context: Context, spec: GrantDescriptor): Boolean {
        if (Build.VERSION.SDK_INT < spec.minSdk) return true
        return ContextCompat.checkSelfPermission(context, spec.androidPermission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
