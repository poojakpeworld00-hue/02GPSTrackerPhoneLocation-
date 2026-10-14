package com.find.phone.gps.location.tracker.repo

import android.content.Context
import com.find.phone.gps.location.tracker.monetize.model.getLocationFromIP
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.screens.finder.RegionZones
import com.find.phone.gps.location.tracker.helpers.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TerritoryFinder {

    private const val TAG = "TerritoryFinder"

    private val DEBUG_FORCE_ISO: String? = "JP"

    data class IpNation(val iso: String, val dial: String)

    suspend fun senseCountry(context: Context): IpNation? {

        if (BuildConfig.DEBUG) {
            validCountry(DEBUG_FORCE_ISO)?.let { forced ->
                SafeLogger.log(TAG, "DEBUG force country=$forced (cache/network bypassed)")
                return IpNation(forced, RegionZones.dialOf(forced).orEmpty())
            }
        }

        val prefs = SecurePrefsKeeper(context)

        val cached = validCountry(prefs.homeCountryIso) ?: validCountry(prefs.geoCountryIso)
        if (cached != null) {
            SafeLogger.log(TAG, "reuse cached country=$cached (no IP call)")
            return IpNation(cached, RegionZones.dialOf(cached).orEmpty())
        }

        val geo = senseCountryFromIp() ?: return null

        prefs.geoCountryIso = geo.iso
        SafeLogger.log(TAG, "cached detected country=${geo.iso} for app-wide reuse")
        return geo
    }

    private fun validCountry(raw: String?): String? {
        val iso = raw?.takeIf { it.length == 2 }?.uppercase() ?: return null
        return iso.takeIf { RegionZones.dialOf(it) != null }
    }

    private suspend fun senseCountryFromIp(): IpNation? = withContext(Dispatchers.IO) {
        SafeLogger.log(TAG, "no cache → getLocationFromIP()")
        val location = getLocationFromIP()
        if (location == null) {
            SafeLogger.log(TAG, "getLocationFromIP() returned null → null")
            return@withContext null
        }
        SafeLogger.log(TAG, "location: country=${location.country} code=${location.countryCode}")

        val iso = location.countryCode?.takeIf { it.length == 2 }?.uppercase()
        if (iso == null) {
            SafeLogger.log(TAG, "no valid countryCode → null")
            return@withContext null
        }
        val dial = RegionZones.dialOf(iso).orEmpty()
        SafeLogger.log(TAG, "resolved country=$iso dial=$dial")
        IpNation(iso, dial)
    }
}
