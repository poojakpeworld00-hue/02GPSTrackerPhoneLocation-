package com.find.phone.gps.location.tracker.grants

data class GrantPolicy(

    val key: String,

    val enabled: Boolean,

    val activities: List<String>,

    val delayMs: Long,

    val priority: Int,

    val showOnce: Boolean,
)

data class GrantDescriptor(

    val key: String,

    val androidPermission: String,

    val minSdk: Int,

    val enabledPrefGate: String? = null,
)
