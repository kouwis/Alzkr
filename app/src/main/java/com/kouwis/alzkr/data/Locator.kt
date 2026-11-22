package com.kouwis.alzkr.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** One rough fix of where the phone is (for prayer times), and the place's name if known. */
object Locator {
    data class Fix(val lat: Double, val lng: Double, val place: String)

    sealed interface Result {
        data class Found(val fix: Fix) : Result
        /** location is switched off on the phone */
        data object Off : Result
        data object NotFound : Result
    }

    /**
     * A fresh fix when the phone can give one quickly, else the most recent fix it already has
     * (no older than [maxAgeMs]). Coarse location is enough: prayer times move about a minute
     * per 15–20 km.
     */
    @SuppressLint("MissingPermission") // called only after the permission is granted
    suspend fun locate(context: Context, maxAgeMs: Long = 6 * 3600_000L): Result {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) return Result.Off
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }

        val fresh: Location? = providers.firstOrNull()?.let { provider ->
            withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine { cont ->
                    val cancel = androidx.core.os.CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) {
                            if (cont.isActive) cont.resume(it)
                        }
                    } catch (e: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    } catch (e: IllegalArgumentException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        val loc = fresh ?: (providers + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < maxAgeMs }
            .maxByOrNull { it.time }
            ?: return Result.NotFound
        return Result.Found(Fix(loc.latitude, loc.longitude, placeName(context, loc.latitude, loc.longitude)))
    }

    /** "أول الشيخ زايد، الشيخ زايد" style: district then city, in Arabic; needs the network. */
    @Suppress("DEPRECATION")
    suspend fun placeName(context: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            val a = Geocoder(context, Locale("ar")).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@runCatching ""
            val parts = listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea ?: a.adminArea)
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            when {
                parts.size == 2 && (parts[0].contains(parts[1]) || parts[1].contains(parts[0])) -> parts.maxBy { it.length }
                else -> parts.joinToString("، ")
            }
        }.getOrNull().orEmpty()
    }

    /** Distance in km (good enough at these scales). */
    fun km(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLng, bLat, bLng, r)
        return r[0] / 1000.0
    }
}
