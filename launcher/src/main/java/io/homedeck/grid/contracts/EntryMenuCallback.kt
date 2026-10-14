package io.homedeck.grid.contracts

import io.homedeck.grid.entities.DeskGridEntry

interface EntryMenuCallback {
    fun onAnyClick()
    fun hide(gridItem: DeskGridEntry)
    fun rename(gridItem: DeskGridEntry)
    fun resize(gridItem: DeskGridEntry)
    fun appInfoUi(gridItem: DeskGridEntry)
    fun remove(gridItem: DeskGridEntry)
    fun uninstall(gridItem: DeskGridEntry)
    fun onDismiss()
}

abstract class EntryMenuCallbackBase : EntryMenuCallback {
    override fun onAnyClick() = Unit
    override fun hide(gridItem: DeskGridEntry) = Unit
    override fun rename(gridItem: DeskGridEntry) = Unit
    override fun resize(gridItem: DeskGridEntry) = Unit
    override fun appInfoUi(gridItem: DeskGridEntry) = Unit
    override fun remove(gridItem: DeskGridEntry) = Unit
    override fun uninstall(gridItem: DeskGridEntry) = Unit
    override fun onDismiss() = Unit
}
