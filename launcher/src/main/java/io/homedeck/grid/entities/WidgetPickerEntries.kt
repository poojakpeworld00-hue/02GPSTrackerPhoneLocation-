package io.homedeck.grid.entities

open class WidgetPickerEntries(val widgets: ArrayList<DeskWidgetInfo>) : WidgetPickerEntry() {
    override fun getHashToCompare() = widgets.sumOf { it.getHashToCompare() }
}
