package com.find.phone.gps.location.tracker.monetize.model

import kotlin.text.lowercase

enum class AdFormatKind {
    GOOGLE,
    FACEBOOK,
    CUSTOM,
    UNKNOWN;
/**/
    companion object {
        fun fromString(value: String?): AdFormatKind {
            return when (value?.lowercase()) {  // convert input to lowercase
                "google" -> GOOGLE
                "facebook", "fb" -> FACEBOOK
                "custom" -> CUSTOM
                else -> UNKNOWN
            }
        }
    }
}