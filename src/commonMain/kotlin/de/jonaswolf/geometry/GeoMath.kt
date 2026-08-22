package de.jonaswolf.geometry

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

public data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Great-circle (haversine) distance/length helpers, pure Kotlin — no
 * platform-specific GIS engine needed, unlike [GeometryEngine]'s polygon
 * operations. Matches `com.google.maps.android.SphericalUtil`'s formula and
 * Earth radius exactly (6371009 m, its `computeAngleBetween` haversine
 * formula) for compatibility with applications already built on that library.
 */
public object GeoMath {
    private const val EARTH_RADIUS_METERS = 6371009.0

    public fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude.toRadians()
        val lat2 = to.latitude.toRadians()
        val dLat = lat2 - lat1
        val dLng = to.longitude.toRadians() - from.longitude.toRadians()
        val angle = 2 * asin(sqrt(haversin(dLat) + cos(lat1) * cos(lat2) * haversin(dLng)))
        return angle * EARTH_RADIUS_METERS
    }

    /** Sum of great-circle distances between consecutive points, e.g. for an OSM way. */
    public fun lengthMeters(points: List<GeoPoint>): Double =
        points.zipWithNext { a, b -> distanceMeters(a, b) }.sum()

    private fun haversin(x: Double): Double = sin(x / 2).let { it * it }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
