package app.pedal.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Location updates straight from the platform LocationManager (no Play Services needed,
 * so the app also works on de-Googled devices).
 */
object LocationSource {

    @SuppressLint("MissingPermission")
    fun updates(
        context: Context,
        intervalMs: Long,
        includeNetwork: Boolean,
        emitLastKnown: Boolean,
        looper: Looper = Looper.getMainLooper(),
    ): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            // Explicit overrides: these are abstract on API < 30.
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        val all = lm.allProviders
        val providers = buildList {
            if (LocationManager.GPS_PROVIDER in all) add(LocationManager.GPS_PROVIDER)
            if (includeNetwork && LocationManager.NETWORK_PROVIDER in all) add(LocationManager.NETWORK_PROVIDER)
        }
        if (emitLastKnown) {
            providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { trySend(it) }
        }
        for (p in providers) {
            runCatching { lm.requestLocationUpdates(p, intervalMs, 0f, listener, looper) }
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    fun Location.toLive() = LiveLocation(
        lat = latitude,
        lon = longitude,
        accuracy = if (hasAccuracy()) accuracy else 50f,
        bearing = if (hasBearing() && hasSpeed() && speed > 1f) bearing else null,
        speed = if (hasSpeed()) speed else null,
        time = time,
    )
}
