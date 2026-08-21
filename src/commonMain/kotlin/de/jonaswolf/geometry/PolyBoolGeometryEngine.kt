package de.jonaswolf.geometry

import de.stefan_oltmann.polybool.Epsilon
import de.stefan_oltmann.polybool.PolyBool

/**
 * [GeometryEngine] backed by the vendored `polybool-kotlin` (see
 * `src/commonMain/kotlin/de/stefan_oltmann/polybool/`, MIT, ported from
 * [polybool-java](https://github.com/Menecats/polybool-java)/
 * [polybooljs](https://github.com/velipso/polybooljs)) — pure Kotlin, no
 * platform-specific backend needed, unlike the originally planned
 * Esri-JVM/Turf.js-Wasm split (see this repo's README for why).
 *
 * Not numerically identical to Esri's results (different algorithm) — see
 * this repo's AGENTS.md for the fixture cross-check this should get before
 * being relied on for real region data.
 */
public class PolyBoolGeometryEngine(
    private val epsilon: Epsilon = Epsilon.default,
) : GeometryEngine {

    override fun area(geoJson: String): Double =
        polygonArea(geoJsonToPolygon(geoJson, epsilon), epsilon)

    override fun contains(outer: String, inner: String): Boolean {
        val outerPolygon = geoJsonToPolygon(outer, epsilon)
        val innerPolygon = geoJsonToPolygon(inner, epsilon)
        // inner is fully inside outer iff nothing of inner remains outside it.
        return PolyBool.difference(epsilon, innerPolygon, outerPolygon).regions.isEmpty()
    }

    override fun intersects(a: String, b: String): Boolean {
        val polygonA = geoJsonToPolygon(a, epsilon)
        val polygonB = geoJsonToPolygon(b, epsilon)
        return PolyBool.intersect(epsilon, polygonA, polygonB).regions.isNotEmpty()
    }

    override fun intersection(a: String, b: String): String {
        val polygonA = geoJsonToPolygon(a, epsilon)
        val polygonB = geoJsonToPolygon(b, epsilon)
        return polygonToGeoJson(PolyBool.intersect(epsilon, polygonA, polygonB), epsilon)
    }

    override fun difference(a: String, b: String): String {
        val polygonA = geoJsonToPolygon(a, epsilon)
        val polygonB = geoJsonToPolygon(b, epsilon)
        return polygonToGeoJson(PolyBool.difference(epsilon, polygonA, polygonB), epsilon)
    }
}
