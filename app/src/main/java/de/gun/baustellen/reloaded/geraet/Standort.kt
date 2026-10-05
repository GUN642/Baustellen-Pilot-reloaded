package de.gun.baustellen.reloaded.geraet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object Standort {
    fun erlaubt(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Aktueller Standort (Breite, Länge) über den LocationManager. */
    @SuppressLint("MissingPermission")
    suspend fun holen(ctx: Context): Pair<Double, Double>? {
        if (!erlaubt(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val anbieter = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (Build.VERSION.SDK_INT >= 30) {
            for (a in anbieter.filter { it != LocationManager.PASSIVE_PROVIDER }) {
                val l: Location? = withTimeoutOrNull(15_000) {
                    suspendCancellableCoroutine { k ->
                        try {
                            lm.getCurrentLocation(a, null, ContextCompat.getMainExecutor(ctx)) { k.resume(it) }
                        } catch (e: Exception) { k.resume(null) }
                    }
                }
                if (l != null) return l.latitude to l.longitude
            }
        }
        val letzte = anbieter.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return letzte?.let { it.latitude to it.longitude }
    }
}
