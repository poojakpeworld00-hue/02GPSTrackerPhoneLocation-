package io.homedeck.grid.contracts

import io.homedeck.grid.entities.DeskWidgetInfo

interface WidgetPickerCallback {
    fun onWidgetLongPressed(appWidget: DeskWidgetInfo)
}
