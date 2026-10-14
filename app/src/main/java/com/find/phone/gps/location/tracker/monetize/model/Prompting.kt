package com.find.phone.gps.location.tracker.monetize.model

data class Prompting(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val dateTime: Long,
    val color: Int
)

