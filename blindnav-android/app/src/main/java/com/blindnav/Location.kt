package com.blindnav

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.blindnav.core.Fix
import com.blindnav.core.LatLon

/** Plain LocationManager, no Play Services. Callers must hold ACCESS_FINE_LOCATION. */
class LocationProvider(ctx: Context) {
    private val lm = ctx.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun lastKnown(): LatLon? = try {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }?.let { LatLon(it.latitude, it.longitude) }
    } catch (e: SecurityException) { null }

    @SuppressLint("MissingPermission")
    fun start(onFix: (Fix) -> Unit) {
        stop()
        val l = LocationListener { loc -> onFix(Fix(LatLon(loc.latitude, loc.longitude), loc.accuracy)) }
        listener = l
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, l, Looper.getMainLooper())
        } catch (e: SecurityException) { listener = null } catch (e: IllegalArgumentException) { listener = null }
    }

    fun stop() {
        listener?.let { lm.removeUpdates(it) }
        listener = null
    }
}
