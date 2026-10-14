package com.find.phone.gps.location.tracker.repo.finder

import android.content.Context
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object ZoneGeocoder {

    private const val CACHE_FILE = "area_geocode_cache"
    private const val LOOKUP_TIMEOUT_MS = 6_000L

    suspend fun locate(context: Context, pincode: String?, city: String?, state: String?, country: String?): MapMarkerPoint? {
        val appCtx = context.applicationContext
        return searchTerms(pincode, city, state, country).firstNotNullOfOrNull { term ->
            fromCache(appCtx, term) ?: lookup(appCtx, term)?.also { remember(appCtx, term, it) }
        }
    }

    private fun joinParts(vararg parts: String?): String =
        parts.mapNotNull { part -> part?.trim()?.ifEmpty { null } }.joinToString(", ")

    private fun searchTerms(pincode: String?, city: String?, state: String?, country: String?): List<String> =
        listOf(
            joinParts(pincode, city, state, country),
            joinParts(city, state, country),
            joinParts(pincode, country),
            joinParts(state, country),
            joinParts(country),
        ).filter { it.isNotEmpty() }.distinct()

    private fun CancellableContinuation<Address?>.finish(value: Address?) {
        if (isActive) resume(value)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun modernLookup(coder: Geocoder, term: String): Address? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                coder.getFromLocationName(term, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.finish(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        cont.finish(null)
                    }
                })
            }.onFailure { cont.finish(null) }
        }

    private suspend fun legacyLookup(coder: Geocoder, term: String): Address? =
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { coder.getFromLocationName(term, 1)?.firstOrNull() }.getOrNull()
        }

    private suspend fun lookup(context: Context, term: String): MapMarkerPoint? {
        val available = runCatching { Geocoder.isPresent() }.getOrDefault(false)
        if (!available) return null
        val coder = runCatching { Geocoder(context, Locale.ENGLISH) }.getOrNull() ?: return null
        val hit: Address? = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) modernLookup(coder, term)
            else legacyLookup(coder, term)
        }
        return runCatching {
            if (hit != null && hit.hasLatitude() && hit.hasLongitude()) MapMarkerPoint(hit.latitude, hit.longitude) else null
        }.getOrNull()
    }

    private fun cacheOf(context: Context): SharedPreferences =
        context.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE)

    private fun fromCache(context: Context, term: String): MapMarkerPoint? {
        val stored = cacheOf(context).getString(term.lowercase(Locale.ROOT), null) ?: return null
        val coords = stored.split(",").mapNotNull(String::toDoubleOrNull)
        return coords.takeIf { it.size == 2 }?.let { MapMarkerPoint(it[0], it[1]) }
    }

    private fun remember(context: Context, term: String, point: MapMarkerPoint) {
        cacheOf(context).edit().putString(term.lowercase(Locale.ROOT), "${point.lat},${point.lng}").apply()
    }
}
