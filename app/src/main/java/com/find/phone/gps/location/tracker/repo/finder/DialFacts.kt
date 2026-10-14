package com.find.phone.gps.location.tracker.repo.finder

import androidx.annotation.Keep
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.google.i18n.phonenumbers.PhoneNumberToTimeZonesMapper
import java.util.Locale

data class DialFacts(
    val e164: String,
    val international: String,
    val iso2: String,
    val valid: Boolean,
    val lineType: RouteStyle,
    val carrier: String?,

    val area: String?,
    val timeZone: String?,
)

@Keep
enum class RouteStyle { MOBILE, FIXED_LINE, FIXED_OR_MOBILE, TOLL_FREE, PREMIUM, VOIP, OTHER }

object DialFactsReader {

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    fun parse(digits: String, iso2: String): PhoneNumber? = try {
        util.parse(digits, iso2.uppercase(Locale.ROOT))
    } catch (_: NumberParseException) {
        null
    }

    fun split(raw: String, fallbackIso: String): Pair<String, String> {
        val parsed = parse(raw, fallbackIso)
            ?: return fallbackIso to raw.filter(Char::isDigit)
        val region = util.getRegionCodeForNumber(parsed)?.takeIf { it != "ZZ" } ?: fallbackIso
        return region to util.getNationalSignificantNumber(parsed)
    }

    fun isValid(digits: String, iso2: String): Boolean = parse(digits, iso2)?.let(util::isValidNumber) == true

    fun e164(digits: String, iso2: String, dial: String): String =
        parse(digits, iso2)?.let { util.format(it, PhoneNumberFormat.E164) } ?: "+$dial$digits"

    fun read(digits: String, iso2: String, dial: String): DialFacts {
        val parsed = parse(digits, iso2)
            ?: return DialFacts("+$dial$digits", "+$dial $digits", iso2.uppercase(Locale.ROOT), false, RouteStyle.OTHER, null, null, null)

        val region = util.getRegionCodeForNumber(parsed) ?: iso2.uppercase(Locale.ROOT)
        val countryName = Locale("", region).getDisplayCountry(Locale.ENGLISH)
        val area = PhoneNumberOfflineGeocoder.getInstance()
            .getDescriptionForNumber(parsed, Locale.ENGLISH).trim()
            .takeIf { it.isNotEmpty() && !it.equals(countryName, ignoreCase = true) }
        val carrier = PhoneNumberToCarrierMapper.getInstance()
            .getNameForNumber(parsed, Locale.ENGLISH).trim().takeIf { it.isNotEmpty() }
        val zone = PhoneNumberToTimeZonesMapper.getInstance().getTimeZonesForNumber(parsed)
            .firstOrNull { it != "Etc/Unknown" }

        return DialFacts(
            e164 = util.format(parsed, PhoneNumberFormat.E164),
            international = util.format(parsed, PhoneNumberFormat.INTERNATIONAL),
            iso2 = region,
            valid = util.isValidNumber(parsed),
            lineType = when (util.getNumberType(parsed)) {
                PhoneNumberType.MOBILE -> RouteStyle.MOBILE
                PhoneNumberType.FIXED_LINE -> RouteStyle.FIXED_LINE
                PhoneNumberType.FIXED_LINE_OR_MOBILE -> RouteStyle.FIXED_OR_MOBILE
                PhoneNumberType.TOLL_FREE -> RouteStyle.TOLL_FREE
                PhoneNumberType.PREMIUM_RATE -> RouteStyle.PREMIUM
                PhoneNumberType.VOIP -> RouteStyle.VOIP
                else -> RouteStyle.OTHER
            },
            carrier = carrier,
            area = area,
            timeZone = zone,
        )
    }
}
