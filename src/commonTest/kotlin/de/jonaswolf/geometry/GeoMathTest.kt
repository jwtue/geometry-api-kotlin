package de.jonaswolf.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoMathTest {

    @Test
    fun sameLocationHasZeroDistance() {
        val p = GeoPoint(52.52, 13.405)
        assertEquals(0.0, GeoMath.distanceMeters(p, p))
    }

    @Test
    fun oneDegreeOfLongitudeAtTheEquatorMatchesTheEarthRadiusConstant() {
        // At the equator, 1 degree of longitude subtends exactly
        // EARTH_RADIUS_METERS * (pi / 180) meters along the great circle.
        val expected = 6_371_009.0 * (PI / 180.0)
        val actual = GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        assertTrue(abs(actual - expected) < 0.01, "expected=$expected actual=$actual")
    }

    @Test
    fun lengthMetersSumsConsecutiveSegments() {
        val a = GeoPoint(52.5, 13.4)
        val b = GeoPoint(52.51, 13.4)
        val c = GeoPoint(52.51, 13.41)
        val expected = GeoMath.distanceMeters(a, b) + GeoMath.distanceMeters(b, c)
        assertEquals(expected, GeoMath.lengthMeters(listOf(a, b, c)))
    }
}
