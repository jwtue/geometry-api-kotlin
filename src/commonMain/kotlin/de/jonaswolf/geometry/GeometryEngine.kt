package de.jonaswolf.geometry

/**
 * Abstraction over the polygon-level GIS operations KnowYourCity's region/
 * street-building logic (`BackendCalculationHelper` in the Android app)
 * needs, so callers in `:shared`/`:webApp` don't depend on a specific GIS
 * engine directly.
 *
 * Deliberately not an `expect`/`actual` pair: there is no single canonical
 * implementation, and forcing every target to provide an `actual` before any
 * implementation exists would block compiling this module at all. Instead,
 * platform modules provide plain implementing classes — planned:
 * - JVM/Android: wraps the Esri Geometry API (`com.esri.geometry:esri-geometry-api`,
 *   public, Apache-2.0, the same library the Android app already depends on).
 * - Wasm/browser: JS interop with Turf.js.
 *
 * All operations take and return GeoJSON text rather than a shared geometry
 * value type — GeoJSON is already the interchange format used throughout the
 * Android app's `GeoJson.kt`, and it avoids needing to design a common
 * geometry model that both an Esri wrapper and a Turf.js wrapper would have
 * to convert to/from anyway.
 *
 * Point/line distance (`isSameStreet`, way/segment length) is intentionally
 * NOT part of this interface — the Android app computes those via
 * `com.google.maps.android.SphericalUtil`, not Esri, and that's a plain
 * great-circle calculation with no platform dependency. See [GeoMath]
 * instead, which is fully implemented (no per-platform engine needed).
 */
public interface GeometryEngine {
    /**
     * Area of a (multi)polygon. Matches `BackendCalculationHelper`'s existing
     * use of Esri's `calculateArea2D()` — a normalized/unitless area, not m²
     * (used there only as a relative implausibility check and for area-ratio
     * comparisons between two geometries, never as an absolute measurement).
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
