package com.find.phone.gps.location.tracker.screens.dashboard

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.find.phone.gps.location.tracker.homeshell.HomeShellOwner

interface DashboardShellOwner {

    val hostActivity: AppCompatActivity

    val homeShellController: DashboardShellDriver

    val isShellOnScreen: Boolean

    fun onShellBackExhausted()

    fun showUpdateReadyPrompt()

    fun bringHostToFront()
}

val Activity.homeShellHost: DashboardShellOwner?
    get() = this as? DashboardShellOwner ?: HomeShellOwner.of(this)

val Fragment.homeShellHost: DashboardShellOwner? get() = activity?.homeShellHost

val Fragment.homeShellController: DashboardShellDriver? get() = homeShellHost?.homeShellController

