package io.homedeck.grid.hooks

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import io.homedeck.grid.R

class ScreenLockAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.launcher_lock_device_admin_warning)
    }
}
