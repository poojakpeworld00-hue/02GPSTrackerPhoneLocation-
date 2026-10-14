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

    private val phones: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    private fun upper(code: String) = code.uppercase(Locale.ROOT)

    private fun styleOf(type: PhoneNumberType?): RouteStyle = when (type) {
        PhoneNumberType.MOBILE -> RouteStyle.MOBILE
        PhoneNumberType.FIXED_LINE -> RouteStyle.FIXED_LINE
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> RouteStyle.FIXED_OR_MOBILE
        PhoneNumberType.TOLL_FREE -> RouteStyle.TOLL_FREE
        PhoneNumberType.PREMIUM_RATE -> RouteStyle.PREMIUM
        PhoneNumberType.VOIP -> RouteStyle.VOIP
        else -> RouteStyle.OTHER
    }

    private fun unparsed(digits: String, iso2: String, dial: String) = DialFacts(
        e164 = "+$dial$digits",
        international = "+$dial $digits",
        iso2 = upper(iso2),
        valid = false,
        lineType = RouteStyle.OTHER,
        carrier = null,
        area = null,
        timeZone = null,
    )

    private fun areaOf(number: PhoneNumber, region: String): String? {
        val country = Locale("", region).getDisplayCountry(Locale.ENGLISH)
        val text = PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(number, Locale.ENGLISH).trim()
        return text.takeUnless { it.isEmpty() || it.equals(country, ignoreCase = true) }
    }

    private fun carrierOf(number: PhoneNumber): String? =
        PhoneNumberToCarrierMapper.getInstance().getNameForNumber(number, Locale.ENGLISH).trim().ifEmpty { null }

    private fun zoneOf(number: PhoneNumber): String? =
        PhoneNumberToTimeZonesMapper.getInstance().getTimeZonesForNumber(number).firstOrNull { it != "Etc/Unknown" }

    fun parse(digits: String, iso2: String): PhoneNumber? {
        return try {
            phones.parse(digits, upper(iso2))
        } catch (_: NumberParseException) {
            null
        }
    }

    fun split(raw: String, fallbackIso: String): Pair<String, String> {
        val number = parse(raw, fallbackIso) ?: return Pair(fallbackIso, raw.filter { it.isDigit() })
        val code = phones.getRegionCodeForNumber(number)
        val region = if (code == null || code == "ZZ") fallbackIso else code
        return Pair(region, phones.getNationalSignificantNumber(number))
    }

    fun isValid(digits: String, iso2: String): Boolean {
        val number = parse(digits, iso2) ?: return false
        return phones.isValidNumber(number)
    }

    fun e164(digits: String, iso2: String, dial: String): String {
        val number = parse(digits, iso2) ?: return "+$dial$digits"
        return phones.format(number, PhoneNumberFormat.E164)
    }

    fun read(digits: String, iso2: String, dial: String): DialFacts {
        val number = parse(digits, iso2) ?: return unparsed(digits, iso2, dial)
        val region = phones.getRegionCodeForNumber(number) ?: upper(iso2)
        val area = areaOf(number, region)
        val carrier = carrierOf(number)
        val zone = zoneOf(number)
        return DialFacts(
            e164 = phones.format(number, PhoneNumberFormat.E164),
            international = phones.format(number, PhoneNumberFormat.INTERNATIONAL),
            iso2 = region,
            valid = phones.isValidNumber(number),
            lineType = styleOf(phones.getNumberType(number)),
            carrier = carrier,
            area = area,
            timeZone = zone,
        )
    }
}
