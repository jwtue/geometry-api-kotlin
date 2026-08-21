package de.jonaswolf.geometry

import de.stefan_oltmann.polybool.Epsilon
import de.stefan_oltmann.polybool.PolyBool
import de.stefan_oltmann.polybool.models.Polygon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GeoJSON Polygon/MultiPolygon <-> the vendored `polybool`'s [Polygon] (a flat
 * list of simple rings, with no inherent outer/hole distinction). Ported from
 * [lib/geojson.js in velipso/polybooljs](https://github.com/velipso/polybooljs/blob/master/lib/geojson.js)
 * (MIT, Copyright (c) 2016 Sean Connelly), since `polybool-kotlin` only ports
 * the core clipping engine, not that conversion module.
 *
 * `polybool-kotlin`'s public API doesn't expose the low-level
 * `segments`/`combine`/`select*` primitives `geojson.js` uses internally
 * (they're `private` in its `PolyBool` object) — this is rebuilt on top of
 * the public `PolyBool.union`/`difference` operations instead, which is
 * behaviorally equivalent (holes: subtract each interior ring from the
 * exterior ring one at a time; MultiPolygon parts: union together) at the
 * cost of re-running self-intersection resolution per step rather than once
 * at the very end — irrelevant for the polygon sizes this is used on
 * (region/street boundaries, at most a few thousand vertices).
 */
internal typealias Ring = List<DoubleArray>

private fun parseRing(ringJson: JsonArray): Ring =
    ringJson.map { point ->
        val coords = point.jsonArray
        doubleArrayOf(coords[0].jsonPrimitive.double, coords[1].jsonPrimitive.double)
    }.let { ring ->
        // GeoJSON rings are explicitly closed (first point == last point);
        // polybool's ring representation is implicitly closed, no duplicate
        // final vertex — matches polybooljs's `LineString`: `ls.slice(0, ls.length - 1)`.
        if (ring.size > 1 &&
            ring.first()[0] == ring.last()[0] &&
            ring.first()[1] == ring.last()[1]
        ) {
            ring.dropLast(1)
        } else {
            ring
        }
    }

/** One GeoJSON Polygon's `coordinates`: first ring is the exterior, the rest are holes. */
private fun ringsToPolygon(rings: List<Ring>, epsilon: Epsilon): Polygon {

    if (rings.isEmpty())
        return Polygon(regions = emptyList())

    var out = Polygon(regions = listOf(rings[0]))

    for (i in 1 until rings.size) {
        val hole = Polygon(regions = listOf(rings[i]))
        out = PolyBool.difference(epsilon, out, hole)
    }

    return out
}

internal fun geoJsonToPolygon(geoJsonText: String, epsilon: Epsilon = Epsilon.default): Polygon {

    val root = Json.parseToJsonElement(geoJsonText).jsonObject
    val coordinates = root["coordinates"]?.jsonArray
        ?: throw IllegalArgumentException("GeoJSON geometry has no 'coordinates': $geoJsonText")

    return when (val type = root["type"]?.jsonPrimitive?.content) {
        "Polygon" ->
            ringsToPolygon(coordinates.map { parseRing(it.jsonArray) }, epsilon)

        "MultiPolygon" -> {
            var out = Polygon(regions = emptyList())
            for (polygonCoords in coordinates) {
                val rings = polygonCoords.jsonArray.map { parseRing(it.jsonArray) }
                val part = ringsToPolygon(rings, epsilon)
                out = if (out.regions.isEmpty()) part else PolyBool.union(epsilon, out, part)
            }
            out
        }

        else -> throw IllegalArgumentException("Unsupported GeoJSON geometry type: $type")
    }
}

/**
 * Nesting hierarchy used both for GeoJSON (re)construction and for signed-area
 * calculation. Ported from `geojson.js`'s `fromPolygon` — builds a
 * parent/child tree of rings by point-in-polygon containment, alternating
 * exterior/interior by nesting depth, and normalizes winding (exterior
 * counter-clockwise, interior/hole clockwise, per the GeoJSON spec).
 */
private class RegionNode(val region: Ring?) {
    val children: MutableList<RegionNode> = mutableListOf()
}

private fun pointInsideRegion(epsilon: Epsilon, point: DoubleArray, region: Ring): Boolean {
    val x = point[0]
    val y = point[1]
    var lastX = region.last()[0]
    var lastY = region.last()[1]
    var inside = false
    for (p in region) {
        val currX = p[0]
        val currY = p[1]
        if ((currY - y > epsilon.eps) != (lastY - y > epsilon.eps) &&
            (lastX - currX) * (y - currY) / (lastY - currY) + currX - x > epsilon.eps
        ) {
            inside = !inside
        }
        lastX = currX
        lastY = currY
    }
    return inside
}

private fun regionInsideRegion(epsilon: Epsilon, r1: Ring, r2: Ring): Boolean =
    pointInsideRegion(
        epsilon,
        doubleArrayOf((r1[0][0] + r1[1][0]) * 0.5, (r1[0][1] + r1[1][1]) * 0.5),
        r2,
    )

/** Signed shoelace sum (not divided by 2) — negative means clockwise, matching `geojson.js`. */
private fun windingSum(ring: Ring): Double {
    var sum = 0.0
    var lastX = ring.last()[0]
    var lastY = ring.last()[1]
    for (p in ring) {
        val currX = p[0]
        val currY = p[1]
        sum += currY * lastX - currX * lastY
        lastX = currX
        lastY = currY
    }
    return sum
}

private fun forceWinding(ring: Ring, clockwise: Boolean): Ring {
    val isClockwise = windingSum(ring) < 0
    val oriented = if (isClockwise != clockwise) ring.asReversed() else ring
    return oriented + listOf(oriented.first())
}

private fun buildHierarchy(regions: List<Ring>, epsilon: Epsilon): RegionNode {

    val root = RegionNode(null)

    fun addChild(parent: RegionNode, region: Ring) {
        for (child in parent.children) {
            if (child.region != null && regionInsideRegion(epsilon, region, child.region)) {
                addChild(child, region)
                return
            }
        }
        val node = RegionNode(region)
        val iterator = parent.children.iterator()
        while (iterator.hasNext()) {
            val child = iterator.next()
            if (child.region != null && regionInsideRegion(epsilon, child.region, region)) {
                node.children.add(child)
                iterator.remove()
            }
        }
        parent.children.add(node)
    }

    for (region in regions) {
        if (region.size >= 3) addChild(root, region)
    }

    return root
}

// addExterior/getInterior are mutually recursive (an exterior's children are interiors,
// an interior's children are exteriors) — member functions of a class can reference each
// other regardless of declaration order, unlike local functions in the same block.
private class ExteriorGroupBuilder {

    val geoPolys: MutableList<List<Ring>> = mutableListOf()

    fun addExterior(node: RegionNode) {
        val group = mutableListOf(forceWinding(node.region!!, clockwise = false))
        geoPolys.add(group)
        for (child in node.children) group.add(getInterior(child))
    }

    private fun getInterior(node: RegionNode): Ring {
        for (child in node.children) addExterior(child)
        return forceWinding(node.region!!, clockwise = true)
    }
}

/** Exterior rings (with their direct hole children) — each entry is [exterior, hole, hole, ...]. */
private fun exteriorGroups(root: RegionNode): List<List<Ring>> {
    val builder = ExteriorGroupBuilder()
    for (child in root.children) builder.addExterior(child)
    return builder.geoPolys
}

internal fun polygonToGeoJson(polygon: Polygon, epsilon: Epsilon = Epsilon.default): String {

    val root = buildHierarchy(polygon.regions, epsilon)
    val geoPolys = exteriorGroups(root)

    fun ringToJson(ring: Ring) = buildJsonArray {
        for (p in ring) add(buildJsonArray { add(JsonPrimitive(p[0])); add(JsonPrimitive(p[1])) })
    }

    return when {
        geoPolys.isEmpty() -> buildJsonObject {
            put("type", "Polygon")
            put("coordinates", buildJsonArray { })
        }

        geoPolys.size == 1 -> buildJsonObject {
            put("type", "Polygon")
            put("coordinates", buildJsonArray { for (ring in geoPolys[0]) add(ringToJson(ring)) })
        }

        else -> buildJsonObject {
            put("type", "MultiPolygon")
            put(
                "coordinates",
                buildJsonArray {
                    for (group in geoPolys) {
                        add(buildJsonArray { for (ring in group) add(ringToJson(ring)) })
                    }
                },
            )
        }
    }.toString()
}

/** Net area (holes subtracted), in the input coordinates' own units squared — see [GeometryEngine.area]. */
internal fun polygonArea(polygon: Polygon, epsilon: Epsilon = Epsilon.default): Double {
    val root = buildHierarchy(polygon.regions, epsilon)
    val geoPolys = exteriorGroups(root)
    var total = 0.0
    for (group in geoPolys) for (ring in group) total += windingSum(ring.dropLast(1))
    return kotlin.math.abs(total) / 2.0
}
