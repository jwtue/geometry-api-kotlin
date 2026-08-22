# geometry-api-kotlin

A Kotlin Multiplatform geometry library for common GIS-style needs when
working with OSM/GeoJSON-shaped data. Two parts, deliberately kept separate
because they started with very different portability stories:

- **`GeoMath`** — great-circle (haversine) point distance and polyline
  length. Pure Kotlin, no platform dependency. Matches the formula and Earth
  radius (6,371,009 m) of
  [`com.google.maps.android.SphericalUtil`](https://github.com/googlemaps/android-maps-utils)
  — a from-scratch reimplementation of that formula, not a port of
  Android-maps-utils' code (which is Android-only and JVM-bound).
- **`GeometryEngine`** — an interface, plus one implementation, for the
  heavier polygon operations (area, contains, intersects, intersection,
  difference) that libraries like the
  [Esri Geometry API](https://github.com/Esri/geometry-api-java)
  (`com.esri.geometry:esri-geometry-api`, public, Apache-2.0) provide on the
  JVM but that have no equivalent usable from a Kotlin/Wasm browser target.

Both are now fully implemented and pure Kotlin (`commonMain`), so there ends
up being no per-platform split at all: one implementation, every target.

## `PolyBoolGeometryEngine`

`GeometryEngine`'s only implementation right now. Built on top of a vendored
copy of [polybool-kotlin](https://github.com/StefanOltmann/polybool-kotlin)
(MIT, Stefan Oltmann's Kotlin Multiplatform port of
[polybool-java](https://github.com/Menecats/polybool-java)/
[polybooljs](https://github.com/velipso/polybooljs), Copyright (c) 2016
Sean Connelly, 2021 Davide Menegatti, 2025 Stefan Oltmann) — see
[`AGENTS.md`](AGENTS.md) for exactly what's vendored, why (no working Maven
Central artifact currently exists for it, despite what its own README
claims), and how GeoJSON Polygon/MultiPolygon conversion (including holes)
was ported on top of its public API from `polybooljs`'s own `geojson.js`
reference implementation, since the Kotlin port doesn't include that part.

**Not numerically identical to Esri's results** — different algorithm.
Verified so far only against hand-built fixtures (squares, a polygon with a
hole, a MultiPolygon) in `PolyBoolGeometryEngineTest` — a cross-check
against real OSM administrative-boundary GeoJSON (comparing to what an
Esri-based implementation produces for the same input) is still outstanding
before trusting this for real region data.

## Why not `expect`/`actual`?

`GeometryEngine` is a plain `interface` in `commonMain`, not an
`expect`/`actual` declaration. There's no single canonical implementation to
declare `expect` for — plain classes implementing the interface (of which
there could in principle be more than one) compose better here.

## Targets

- `jvm`
- `wasmJs` (browser)
- `js` (browser + Node.js)
- `linuxX64`, `linuxArm64`, `mingwX64` (Windows), `macosX64`, `macosArm64`
- `iosX64`, `iosArm64`, `iosSimulatorArm64`

Everything is pure `commonMain` Kotlin (no platform APIs), so all 12 tests
(`GeoMathTest` + `PolyBoolGeometryEngineTest`) genuinely run — not just
compile — on `jvm`, `js` (both browser and Node.js), and `mingwX64`,
verified locally; the remaining targets are compile-verified only (no host
available to execute their test binaries in this project's CI).

## Usage

```kotlin
dependencies {
    implementation("de.jonaswolf.geometry:geometry-api-kotlin:<version>")
}
```

```kotlin
import de.jonaswolf.geometry.GeoMath
import de.jonaswolf.geometry.GeoPoint
import de.jonaswolf.geometry.PolyBoolGeometryEngine

val meters = GeoMath.distanceMeters(GeoPoint(52.52, 13.405), GeoPoint(48.1351, 11.582))

val engine = PolyBoolGeometryEngine()
val area = engine.area(someGeoJsonPolygonString)
val overlap = engine.intersection(regionAGeoJson, regionBGeoJson)
```

## License

Apache-2.0 — see [`LICENSE`](LICENSE). Vendored `polybool-kotlin` code under
`src/commonMain/kotlin/de/stefan_oltmann/polybool/` keeps its own original
MIT license and copyright headers — see [`LICENSE-polybool-kotlin.txt`](LICENSE-polybool-kotlin.txt)
and [`AGENTS.md`](AGENTS.md).
