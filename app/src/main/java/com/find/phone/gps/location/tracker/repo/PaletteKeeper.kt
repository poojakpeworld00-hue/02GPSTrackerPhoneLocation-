package com.find.phone.gps.location.tracker.repo

import androidx.appcompat.app.AppCompatDelegate

object PaletteKeeper {

    val modes = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    fun apply(mode: Int) = AppCompatDelegate.setDefaultNightMode(mode)

    fun indexOf(mode: Int): Int = modes.indexOf(mode).coerceAtLeast(0)
}
