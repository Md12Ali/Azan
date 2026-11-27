package com.md12ali.sakinah.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * GPS without Google Play Services (keeps the app fully free/open & works on de-Googled phones).
 * Coarse location is enough for prayer times; we ask for both and use whatever the user grants.
 */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Precise, FRESH fix for "nearest mosque": tries real GPS first (up to [timeoutMs]), then network.
     * Never trusts an old or rough cached position, so the nearest mosque really is the nearest.
     */
    @SuppressLint("MissingPermission")
    suspend fun preciseLocation(timeoutMs: Long = 15_000): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        // A very recent, accurate cached fix is fine (≤2 min, ≤60 m).
        enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 120_000 && it.hasAccuracy() && it.accuracy <= 60f }
            .minByOrNull { it.accuracy }?.let { return it }
        var best: Location? = null
        for (p in enabled) {
            val fix = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(lm, p, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }
            }
            if (fix != null && (best == null || (fix.hasAccuracy() && fix.accuracy < best.accuracy))) best = fix
            if (best != null && best.hasAccuracy() && best.accuracy <= 50f) break
        }
        return best ?: currentLocation()
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMs: Long = 12_000): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        // A recent last-known fix (< 30 min) is good enough – prayer times barely change within a few km.
        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 30 * 60_000) return lastKnown

        // LocationManagerCompat back-ports getCurrentLocation to API 26+.
        run {
            for (p in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
                val fresh = withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        LocationManagerCompat.getCurrentLocation(lm, p, signal, ContextCompat.getMainExecutor(context)) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return lastKnown
    }
}
