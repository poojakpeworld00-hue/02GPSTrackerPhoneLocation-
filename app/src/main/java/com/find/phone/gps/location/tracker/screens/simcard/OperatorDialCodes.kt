package com.find.phone.gps.location.tracker.screens.simcard

import java.util.Locale

object OperatorDialCodes {

    data class Code(val title: String, val subtitle: String, val code: String)
    data class Section(val title: String, val codes: List<Code>)

    private val VI_MARKERS = listOf("vodafone", "idea", "vi india")

    private fun isVi(name: String): Boolean = VI_MARKERS.any { name.contains(it) } || name == "vi"

    fun forCarrier(carrier: String?): List<Section> {
        val name = carrier?.lowercase(Locale.ROOT) ?: ""
        if (name.contains("jio")) return JIO
        if (name.contains("airtel")) return AIRTEL
        if (isVi(name)) return VI
        if (name.contains("bsnl")) return BSNL
        return GENERIC
    }

    private val GENERIC = listOf(
        Section("Account", listOf(
            Code("My number", "Show your SIM number", "*1#"),
        )),
        Section("Help", listOf(
            Code("Customer care", "Talk to support", "198"),
        )),
    )

    private val BSNL = listOf(
        Section("Account", listOf(
            Code("Main balance", "Talk-time and validity", "*123#"),
            Code("Data balance", "Remaining data", "*123*6#"),
            Code("My number", "Show your SIM number", "*222#"),
        )),
        Section("Help", listOf(
            Code("Customer care", "Talk to support", "1503"),
            Code("Current plan", "Active pack details", "*123#"),
        )),
    )

    private val VI = listOf(
        Section("Account", listOf(
            Code("Main balance", "Talk-time and validity", "*199#"),
            Code("Data balance", "Remaining data", "*199*2#"),
            Code("My number", "Show your SIM number", "*199#"),
        )),
        Section("Help", listOf(
            Code("Customer care", "Talk to support", "199"),
            Code("Current plan", "Active pack details", "*199#"),
        )),
    )

    private val AIRTEL = listOf(
        Section("Account", listOf(
            Code("Main balance", "Talk-time and validity", "*121#"),
            Code("Data balance", "Remaining data", "*121*51#"),
            Code("My number", "Show your SIM number", "*121*9#"),
        )),
        Section("Help", listOf(
            Code("Customer care", "Talk to support", "198"),
            Code("Current plan", "Active pack details", "*121#"),
        )),
    )

    private val JIO = listOf(
        Section("Account", listOf(
            Code("Main balance", "Talk-time and validity", "*333#"),
            Code("Data balance", "Remaining daily data", "*333*1#"),
            Code("My number", "Show your SIM number", "*1#"),
        )),
        Section("Help", listOf(
            Code("Customer care", "Talk to support", "198"),
            Code("Current plan", "Active pack details", "*333*3#"),
        )),
    )
}
