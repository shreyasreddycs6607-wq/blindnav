package com.blindnav.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val R = 6_371_000.0
    private fun rad(d: Double) = d * PI / 180

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R * asin(min(1.0, sqrt(h)))
    }

    /** Initial bearing a -> b in degrees 0..360. */
    fun bearing(a: LatLon, b: LatLon): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return (atan2(y, x) * 180 / PI + 360) % 360
    }

    /** Distance from p to the segment a-b (equirectangular approximation, fine at walking scale). */
    fun distanceToSegmentM(p: LatLon, a: LatLon, b: LatLon): Double {
        val k = cos(rad(p.lat))
        val ax = (a.lon - p.lon) * k; val ay = a.lat - p.lat
        val bx = (b.lon - p.lon) * k; val by = b.lat - p.lat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / len2))
        val cx = ax + t * dx; val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy) * (PI / 180) * R
    }

    fun distanceToPathM(p: LatLon, path: List<LatLon>): Double =
        if (path.size < 2) path.firstOrNull()?.let { distanceM(p, it) } ?: Double.MAX_VALUE
        else path.zipWithNext().minOf { (a, b) -> distanceToSegmentM(p, a, b) }
}
