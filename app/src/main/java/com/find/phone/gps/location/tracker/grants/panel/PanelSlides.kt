package com.find.phone.gps.location.tracker.grants.panel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLines
import com.find.phone.gps.location.tracker.grants.GrantToolbox

enum class PanelSlide(

    val key: String,

    val permissions: Array<String>,
    @StringRes val shortName: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @LayoutRes val illustration: Int,
    @DrawableRes val railIcon: Int,
) {
    NOTIFICATIONS(
        "notification",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
        R.string.ps_notif_name, R.string.ps_notif_title, R.string.ps_notif_body,
        R.layout.gp_ill_notifications, R.drawable.gp_ic_r_bell,
    ),
    SIM(
        "phone_state", OwnSimLines.PERMISSIONS,
        R.string.ps_sim_name, R.string.ps_sim_title, R.string.ps_sim_body,
        R.layout.gp_ill_sim, R.drawable.gp_ic_r_sim,
    ),
    CONTACTS(
        "contacts", arrayOf(Manifest.permission.READ_CONTACTS),
        R.string.ps_contacts_name, R.string.ps_contacts_title, R.string.ps_contacts_body,
        R.layout.gp_ill_contacts, R.drawable.gp_ic_r_people,
    ),
    LOCATION(
        "location",

        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
        R.string.ps_location_name, R.string.ps_location_title, R.string.ps_location_body,
        R.layout.gp_ill_location, R.drawable.gp_ic_r_pin,
    );

    val primaryPermission: String? get() = permissions.firstOrNull()

    fun isGranted(context: Context): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when (this) {
            LOCATION -> granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            else -> permissions.all(::granted)
        }
    }

    companion object {

        fun all(context: Context): List<PanelSlide> = entries.filter {
            it != NOTIFICATIONS || (it.permissions.isNotEmpty() && GrantToolbox.isOfferable(context, it.key))
        }
    }
}
