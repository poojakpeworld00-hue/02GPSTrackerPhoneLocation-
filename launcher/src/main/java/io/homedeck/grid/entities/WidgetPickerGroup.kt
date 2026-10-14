package io.homedeck.grid.entities

import android.graphics.drawable.Drawable

data class WidgetPickerGroup(var appTitle: String, var appIcon: Drawable?) : WidgetPickerEntry() {
    override fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getStringToCompare(): String {
        return copy(appIcon = null).toString()
    }
}
