package com.kouwis.alzkr.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
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

    @SuppressLint("MissingPermission") // called only after the permission is granted
    suspend fun locate(context: Context): Fix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        val loc: Location = last ?: withTimeoutOrNull(15_000) {
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return@withTimeoutOrNull null
            suspendCancellableCoroutine { cont ->
                val cancel = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
            }
        } ?: return null
        return Fix(loc.latitude, loc.longitude, placeName(context, loc.latitude, loc.longitude))
    }

    /** The neighbourhood or city in Arabic; needs the network, so it may come back empty. */
    @Suppress("DEPRECATION")
    suspend fun placeName(context: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            val a = Geocoder(context, Locale("ar")).getFromLocation(lat, lng, 1)?.firstOrNull()
            a?.subLocality ?: a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull().orEmpty()
    }
}
