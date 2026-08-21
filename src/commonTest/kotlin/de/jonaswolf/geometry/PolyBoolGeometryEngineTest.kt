package de.jonaswolf.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolyBoolGeometryEngineTest {

    private val engine = PolyBoolGeometryEngine()

    private fun square(minX: Double, minY: Double, maxX: Double, maxY: Double): String =
        """{"type":"Polygon","coordinates":[[[$minX,$minY],[$maxX,$minY],[$maxX,$maxY],[$minX,$maxY],[$minX,$minY]]]}"""

    @Test
    fun areaOfASimpleSquare() {
        assertEquals(16.0, engine.area(square(0.0, 0.0, 4.0, 4.0)), absoluteTolerance = 1e-9)
    }

    @Test
    fun intersectionOfTwoOverlappingSquaresHasTheExpectedArea() {
        val a = square(0.0, 0.0, 2.0, 2.0)
        val b = square(1.0, 0.0, 3.0, 2.0)
        val intersection = engine.intersection(a, b)
        assertEquals(2.0, engine.area(intersection), absoluteTolerance = 1e-9)
    }

    @Test
    fun differenceOfTwoOverlappingSquaresHasTheExpectedArea() {
        val a = square(0.0, 0.0, 2.0, 2.0)
        val b = square(1.0, 0.0, 3.0, 2.0)
        val difference = engine.difference(a, b)
        assertEquals(2.0, engine.area(difference), absoluteTolerance = 1e-9)
    }

    @Test
    fun nonOverlappingSquaresDoNotIntersect() {
        val a = square(0.0, 0.0, 1.0, 1.0)
        val b = square(5.0, 5.0, 6.0, 6.0)
        assertFalse(engine.intersects(a, b))
    }

    @Test
    fun overlappingSquaresIntersect() {
        val a = square(0.0, 0.0, 2.0, 2.0)
        val b = square(1.0, 0.0, 3.0, 2.0)
        assertTrue(engine.intersects(a, b))
    }

    @Test
    fun smallSquareFullyInsideBigSquareIsContained() {
        val outer = square(0.0, 0.0, 10.0, 10.0)
        val inner = square(2.0, 2.0, 4.0, 4.0)
        assertTrue(engine.contains(outer, inner))
    }

    @Test
    fun squareStraddlingTheBoundaryIsNotContained() {
        val outer = square(0.0, 0.0, 10.0, 10.0)
        val straddling = square(8.0, 8.0, 12.0, 12.0)
        assertFalse(engine.contains(outer, straddling))
    }

    @Test
    fun polygonWithAHoleHasTheOuterAreaMinusTheHoleArea() {
        // Outer 10x10 = 100, hole 2x2 = 4 -> net 96. Ring order (not winding) determines
        // outer-vs-hole for geoJsonToPolygon, matching this repo's PolyBoolGeoJson.kt.
        val outerRing = "[0.0,0.0],[10.0,0.0],[10.0,10.0],[0.0,10.0],[0.0,0.0]"
        val holeRing = "[4.0,4.0],[6.0,4.0],[6.0,6.0],[4.0,6.0],[4.0,4.0]"
        val geoJson = """{"type":"Polygon","coordinates":[[$outerRing],[$holeRing]]}"""
        assertEquals(96.0, engine.area(geoJson), absoluteTolerance = 1e-9)
    }

    @Test
    fun multiPolygonAreaIsTheSumOfItsParts() {
        val partA = "[[0.0,0.0],[2.0,0.0],[2.0,2.0],[0.0,2.0],[0.0,0.0]]"
        val partB = "[[10.0,10.0],[13.0,10.0],[13.0,13.0],[10.0,13.0],[10.0,10.0]]"
        val geoJson = """{"type":"MultiPolygon","coordinates":[[$partA],[$partB]]}"""
        // area = 4 (2x2) + 9 (3x3) = 13
        assertEquals(13.0, engine.area(geoJson), absoluteTolerance = 1e-9)
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        assertTrue(
            abs(expected - actual) <= absoluteTolerance,
            "expected=$expected actual=$actual (tolerance=$absoluteTolerance)",
        )
    }
}
