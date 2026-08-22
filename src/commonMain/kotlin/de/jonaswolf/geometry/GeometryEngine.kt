package de.jonaswolf.geometry

/**
 * Abstraction over common polygon-level GIS operations (area, containment,
 * intersection, boolean set operations), so callers don't depend on a
 * specific GIS engine directly.
 *
 * Deliberately not an `expect`/`actual` pair: there is no single canonical
 * implementation, and forcing every target to provide an `actual` before any
 * implementation exists would block compiling this module at all. Instead,
 * plain classes implement this interface directly — currently just
 * [PolyBoolGeometryEngine], a pure-Kotlin implementation usable unmodified
 * on every target (see its own doc comment).
 *
 * All operations take and return GeoJSON text rather than a shared geometry
 * value type — GeoJSON is already a natural interchange format for this kind
 * of data, and it avoids needing to design a bespoke common geometry model.
 *
 * Point/line distance (e.g. "are these two points the same street", or
 * way/segment length) is intentionally NOT part of this interface — that's a
 * plain great-circle calculation with no GIS-engine dependency. See
 * [GeoMath] instead, which is fully implemented (no per-platform engine
 * needed).
 */
public interface GeometryEngine {
    /**
     * Area of a (multi)polygon, in the input coordinates' own units squared
     * (e.g. degrees² for lon/lat GeoJSON) — not necessarily m². Useful for
     * relative comparisons (implausibility checks, area ratios between two
     * geometries) rather than as an absolute measurement.
     */
    public fun area(geoJson: String): Double

    /** Whether [inner] lies entirely within [outer]. */
    public fun contains(outer: String, inner: String): Boolean

    /** Whether [a] and [b] share any area/boundary. */
    public fun intersects(a: String, b: String): Boolean

    /** The geometry shared by [a] and [b]. */
    public fun intersection(a: String, b: String): String

    /** [a] with [b]'s area removed. */
    public fun difference(a: String, b: String): String
}
