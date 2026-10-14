package com.find.phone.gps.location.tracker.repo.finder

import androidx.annotation.Keep
import java.util.Locale

@Keep
data class HandsetReply(
    val success: Boolean? = null,
    val count: Int? = null,
    val country: String? = null,
    val spam: Boolean? = null,
    val location: HandsetPlace? = null,
    val data: List<HandsetRecord?>? = null,
) {
    val ok: Boolean get() = success == true

    val names: List<String>
        get() = data.orEmpty().mapNotNull { it?.name?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase(Locale.ROOT) }

    val area: HandsetPlace?
        get() = location?.takeIf { it.hasAny }
            ?: data.orEmpty().firstNotNullOfOrNull { d -> d?.location?.takeIf { it.hasAny } }

    val countryCode: String?
        get() = country?.trim()?.takeIf { it.isNotEmpty() }
            ?: data.orEmpty().firstNotNullOfOrNull { it?.country?.trim()?.takeIf(String::isNotEmpty) }

    val carrier: String?
        get() = data.orEmpty().firstNotNullOfOrNull { it?.carrier?.trim()?.takeIf(String::isNotEmpty) }

    val isSpam: Boolean
        get() = spam == true || data.orEmpty().any { it?.is_spam == true || it?.is_user_spam == true }
}

@Keep
data class HandsetRecord(
    val is_spam: Boolean? = null,
    val is_user_spam: Boolean? = null,
    val name: String? = null,
    val country: String? = null,
    val carrier: String? = null,
    val location: HandsetPlace? = null,
)

@Keep
data class HandsetPlace(
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
) {
    val hasAny: Boolean get() = !city.isNullOrBlank() || !state.isNullOrBlank() || !pincode.isNullOrBlank()
}
