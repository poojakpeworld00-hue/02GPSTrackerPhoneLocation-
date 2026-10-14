package io.homedeck.grid.bridge

interface HomeDeskContent {

    fun setPanelVisible(visible: Boolean) {}

    fun handleBack(): Boolean = false
}
