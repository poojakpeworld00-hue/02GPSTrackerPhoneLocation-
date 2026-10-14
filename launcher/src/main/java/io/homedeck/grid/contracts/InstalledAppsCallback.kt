package io.homedeck.grid.contracts

import io.homedeck.grid.entities.LaunchableApp

interface InstalledAppsCallback {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: LaunchableApp)
}
