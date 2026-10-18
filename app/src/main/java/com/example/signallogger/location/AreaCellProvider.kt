package com.example.signallogger.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

/** Collects only a coarse, approximately 5 km grid cell; exact coordinates never leave the phone. */
class AreaCellProvider(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    var currentAreaCell: String? = null
        private set

    private val listener = LocationListener { location -> currentAreaCell = GeoCell.encode(location) }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                if (System.currentTimeMillis() - it.time < MAX_LOCATION_AGE_MS) currentAreaCell = GeoCell.encode(it)
            }
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_INTERVAL_MS,
                LOCATION_DISTANCE_METERS,
                listener,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            currentAreaCell = null
        } catch (_: IllegalArgumentException) {
            currentAreaCell = null
        }
    }

    fun stop() {
        try {
            locationManager.removeUpdates(listener)
        } catch (_: SecurityException) {
            // Permission may have been revoked while logging was active.
        }
    }

    companion object {
        private const val MAX_LOCATION_AGE_MS = 30 * 60 * 1000L
        private const val LOCATION_INTERVAL_MS = 60_000L
        private const val LOCATION_DISTANCE_METERS = 100f
    }
}

private object GeoCell {
    private const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(location: Location): String {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var evenBit = true
        var value = 0
        var bitCount = 0
        val result = StringBuilder(5)

        while (result.length < 5) {
            val midpoint = if (evenBit) (lonMin + lonMax) / 2 else (latMin + latMax) / 2
            val coordinate = if (evenBit) location.longitude else location.latitude
            val upperHalf = coordinate >= midpoint
            value = (value shl 1) or if (upperHalf) 1 else 0
            if (evenBit) {
                if (upperHalf) lonMin = midpoint else lonMax = midpoint
            } else {
                if (upperHalf) latMin = midpoint else latMax = midpoint
            }
            evenBit = !evenBit
            bitCount++
            if (bitCount == 5) {
                result.append(ALPHABET[value])
                value = 0
                bitCount = 0
            }
        }
        return result.toString().lowercase(Locale.ROOT)
    }
}
