package com.find.phone.gps.location.tracker.repo.peoplesync

import android.content.Context
import android.telephony.TelephonyManager
import com.find.phone.gps.location.tracker.screens.people.PersonPhone
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

object PeopleRecords {

    data class Row(
        val e164: String,
        val name: String,
        val city: String?,
        val state: String?,
        val pincode: String?,
        val region: String,
    )

    data class Prepared(
        val rows: List<Row>,
        val total: Int,
        val invalid: Int,
        val duplicates: Int,
        val deviceRegion: String,
    ) {

        val byCountry: String
            get() = rows.groupingBy { it.region }.eachCount().entries
                .sortedByDescending { it.value }
                .joinToString { entry -> "${entry.key} ${entry.value}" }
    }

    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val areaLookup = PhoneNumberOfflineGeocoder.getInstance()

    fun prepare(context: Context, contacts: List<PersonPhone>, deviceArea: HandsetPosition.Area): Prepared {
        val pairs = contacts.map { it.name to it.number }
        return prepare(deviceRegion(context), pairs, deviceArea)
    }

    internal fun prepare(region: String, contacts: List<Pair<String, String>>, deviceArea: HandsetPosition.Area): Prepared {
        val known = HashSet<String>()
        val kept = ArrayList<Row>(contacts.size)
        var rejected = 0
        var repeats = 0

        contacts.forEach { (rawName, rawNumber) ->
            val label = rawName.trim()
            val digits = rawNumber.trim()
            val parsed = validNumber(label, digits, region)
            if (parsed == null) {
                rejected++
                return@forEach
            }
            val formatted = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            if (!known.add(formatted)) {
                repeats++
                return@forEach
            }
            val home = phoneUtil.getRegionCodeForNumber(parsed) ?: region
            val (city, state, pin) = placeFor(parsed, home, region, deviceArea)
            kept += Row(formatted, label, city, state, pin, home)
        }
        return Prepared(kept, contacts.size, rejected, repeats, region)
    }

    private fun validNumber(label: String, digits: String, region: String): PhoneNumber? {
        if (label.isEmpty() || digits.isEmpty()) return null
        return tryParse(digits, region)?.takeIf { phoneUtil.isValidNumber(it) }
    }

    private fun tryParse(raw: String, region: String): PhoneNumber? =
        runCatching { phoneUtil.parse(raw, region) }.getOrNull()

    private fun englishCountry(code: String): String? {
        val shown = Locale("", code).getDisplayCountry(Locale.ENGLISH)
        return if (shown.isNotBlank() && shown != code) shown else null
    }

    private fun describedArea(number: PhoneNumber, country: String?): String? {
        val text = runCatching { areaLookup.getDescriptionForNumber(number, Locale.ENGLISH) }.getOrNull()?.trim()
            ?: return null
        return text.takeUnless { it.isEmpty() || it.equals(country, ignoreCase = true) }
    }

    private fun placeFor(
        number: PhoneNumber,
        home: String,
        device: String,
        area: HandsetPosition.Area,
    ): Triple<String?, String?, String?> {
        val country = englishCountry(home)
        val described = describedArea(number, country)
        if (home != device) return Triple(described, country, null)
        return if (area.isEmpty) Triple(null, described, null) else Triple(area.city, area.state, area.pincode)
    }

    fun deviceRegion(context: Context): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOf(
            runCatching { telephony?.simCountryIso }.getOrNull(),
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            Locale.getDefault().country,
        )
        val picked = candidates.firstOrNull { code -> !code.isNullOrBlank() && code.length == 2 }
        return picked?.uppercase(Locale.US) ?: "IN"
    }
}
