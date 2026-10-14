package com.find.phone.gps.location.tracker.screens.finder

import android.content.Context
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.repo.finder.RouteStyle

/** "Mobile", "Landline"… or null when libphonenumber can't tell. */
fun RouteStyle.label(context: Context): String? = when (this) {
    RouteStyle.MOBILE -> context.getString(R.string.ds_line_mobile)
    RouteStyle.FIXED_LINE -> context.getString(R.string.ds_line_fixed)
    RouteStyle.FIXED_OR_MOBILE -> context.getString(R.string.ds_line_fixed_mobile)
    RouteStyle.TOLL_FREE -> context.getString(R.string.ds_line_toll_free)
    RouteStyle.PREMIUM -> context.getString(R.string.ds_line_premium)
    RouteStyle.VOIP -> context.getString(R.string.ds_line_voip)
    RouteStyle.OTHER -> null
}
