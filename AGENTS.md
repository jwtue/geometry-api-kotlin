# AGENTS.md

Fact-based entry point for AI coding agents in this repository. Describes
only what is concretely observable in the code/repo; open items are marked
explicitly instead of guessed at.

## Project overview

- **What:** a Kotlin Multiplatform geometry module: a great-circle math
  helper (`GeoMath`) and a polygon-GIS abstraction (`GeometryEngine`) with
  one implementation (`PolyBoolGeometryEngine`). Both are fully implemented,
  pure Kotlin, no platform-specific code anywhere in the repo.
- **Why it exists:** built for [KnowYourCity](https://github.com/jwtue)'s
  Android→web expansion. The Android app's `BackendCalculationHelper`
  (region/street-building logic) uses the
  [Esri Geometry API](https://github.com/Esri/geometry-api-java) (public,
  Apache-2.0, `com.esri.geometry:esri-geometry-api`) for polygon operations
  and `com.google.maps.android.SphericalUtil` for point/line distance —
  both JVM-only, unusable from a Kotlin/Wasm browser target. The original
  plan (see `KnowYourCity2022/WEB_KMP_PLAN.md`) was a JVM/Esri actual plus a
  Wasm/Turf.js (JS interop) actual; that plan was abandoned in favor of the
  single pure-Kotlin implementation described below once it turned out one
  already existed to build on (see "PolyBoolGeometryEngine.kt" below) —
  full Esri itself was evaluated and rejected as a port target (its
  boolean-op engine alone depends on a ~2,200-line shared planar-topology
  helper, `OperatorSimplifyLocalHelper.java`, on top of geometry primitives
  not counted in that figure — a multi-week undertaking for something that
  already exists elsewhere under a compatible license).
- **Language/build:** Kotlin Multiplatform, Gradle Kotlin DSL, version
  catalog (`gradle/libs.versions.toml`). Targets: `jvm`, `wasmJs` (browser),
  declared in `build.gradle.kts`. Uses `kotlinx-serialization-json` for
  GeoJSON parsing.
- **group:artifact:** `de.jonaswolf.geometry:geometry-api-kotlin`
  (`build.gradle.kts`: `group = "de.jonaswolf.geometry"`, `rootProject.name`
  in `settings.gradle.kts` = `"geometry-api-kotlin"`). Single-module repo —
  the root project itself is the library, no subprojects.

## Files

### `src/commonMain/kotlin/de/jonaswolf/geometry/GeoMath.kt`

- `data class GeoPoint(val latitude: Double, val longitude: Double)`.
- `object GeoMath` — `EARTH_RADIUS_METERS = 6371009.0`, matching
  `com.google.maps.android.SphericalUtil.EARTH_RADIUS` exactly.
  - `fun distanceMeters(from: GeoPoint, to: GeoPoint): Double` — haversine
    great-circle distance, matching `SphericalUtil.computeAngleBetween`'s
    formula. Implemented from scratch (not copied from Android-maps-utils,
    which is Android-only and wasn't available as a dependency here).
  - `fun lengthMeters(points: List<GeoPoint>): Double` — sums
    `distanceMeters` over consecutive points, for polyline/way length.

### `src/commonMain/kotlin/de/jonaswolf/geometry/GeometryEngine.kt`

`interface GeometryEngine` with five methods, all operating on raw GeoJSON
text in/out (not a shared geometry value type — GeoJSON is already the
interchange format the Android app's `GeoJson.kt` uses throughout):

- `fun area(geoJson: String): Double`
- `fun contains(outer: String, inner: String): Boolean`
- `fun intersects(a: String, b: String): Boolean`
- `fun intersection(a: String, b: String): String`
- `fun difference(a: String, b: String): String`

This set matches exactly what `BackendCalculationHelper` in the Android app
calls Esri for today (`assignStreetsToRegions`, `getFittingChildRegions`,
`calculateRegionStreetRatios`, `GeoJson.calculateInverse`) — nothing broader
or more speculative.

### `src/commonMain/kotlin/de/stefan_oltmann/polybool/**` — vendored, not our code

A verbatim copy of `polybool-kotlin`'s `commonMain` (13 files: `Epsilon.kt`,
`EpsilonIntersectionResult.kt`, `PolyBool.kt`, `internal/*.kt` (8 files),
`models/Polygon.kt`, `models/Segment.kt`), cloned from tag `v0.1.1` of
[StefanOltmann/polybool-kotlin](https://github.com/StefanOltmann/polybool-kotlin)
(MIT — original copyright headers, listing Sean Connelly 2016, Davide
Menegatti 2021, Stefan Oltmann 2025, are preserved unmodified in every
file). A copy of that project's license is kept at
`LICENSE-polybool-kotlin.txt` at the repo root.

**Why vendored instead of a normal dependency**: `polybool-kotlin`'s own
README claims it's "available on Maven Central" as
`de.stefan-oltmann:polybool-kotlin:0.1.1`, but that coordinate returns zero
results from Maven Central's search index (`search.maven.org`) — checked
directly, not assumed. Its repo's `build.gradle.kts` does configure
`publishToMavenCentral()`, but no publish/deploy workflow run exists in its
GitHub Actions history (only "Build & Test" runs) — the publish step
appears to never have actually completed. Given that repo's own README
also candidly describes the code as "functional, though not especially
elegant... [with] work to do" and covered by only one regression test, and
given it depends on the `com.android.library` Gradle plugin for its
Android target (a plugin JitPack's build environment doesn't reliably
support out of the box), vendoring the 13 `commonMain`-only files directly
(no Android/iOS/Desktop-specific code among them — confirmed by inspecting
every file) was judged more robust than depending on it via JitPack. It is
**not modified** from the original beyond this copy — no edits to its
`private`/`internal` visibility or logic.

### `src/commonMain/kotlin/de/jonaswolf/geometry/PolyBoolGeoJson.kt`

GeoJSON Polygon/MultiPolygon ↔ the vendored `PolyBool`'s `Polygon` (a flat
`regions: List<List<DoubleArray>>` with no inherent outer/hole distinction).
`polybool-kotlin` only ports the core clipping engine, not
[polybooljs's `lib/geojson.js`](https://github.com/velipso/polybooljs/blob/master/lib/geojson.js)
conversion module — this file is a from-scratch Kotlin port of that
JavaScript file's logic (same algorithm, re-derived against its source,
not a mechanical transliteration), rebuilt on top of `polybool-kotlin`'s
*public* API only, since the low-level `segments`/`combine`/`select*`
primitives `geojson.js` uses internally are `private` in the Kotlin port's
`PolyBool` object:

- `geoJsonToPolygon(geoJsonText, epsilon): Polygon` — for a `Polygon`
  geometry, the first ring is the exterior, every subsequent ring is a hole
  subtracted from it one at a time via `PolyBool.difference` (matching
  `geojson.js`'s `GeoPoly` function); for a `MultiPolygon`, each part is
  built this way and then unioned together via `PolyBool.union`.
- `polygonToGeoJson(polygon, epsilon): String` and `polygonArea(polygon, epsilon): Double`
  both build a nesting hierarchy of the polygon's raw regions by
  point-in-polygon containment (`buildHierarchy`, using a ported
  `pointInsideRegion` even-odd ray-casting test — this specific test isn't
  in the vendored `Epsilon.kt`, since `polybool-kotlin` didn't port that
  part of the original `lib/epsilon.js` either; it's implemented directly
  in this file instead), alternating exterior/interior by nesting depth
  (`exteriorGroups`/`ExteriorGroupBuilder`) and forcing consistent winding
  (exterior counter-clockwise, interior/hole clockwise, per GeoJSON's own
  convention) — matching `geojson.js`'s `fromPolygon`. `polygonArea` reuses
  this same hierarchy and sums each ring's signed shoelace value (positive
  for the forced-CCW exterior rings, negative for the forced-CW hole rings)
  so holes are subtracted correctly, then takes the absolute value.
- **Implementation note**: `ExteriorGroupBuilder`'s `addExterior`/
  `getInterior` are mutually recursive in the original JS (an exterior's
  children are interiors, an interior's children are exteriors). Kotlin
  *local* functions can't forward-reference each other this way (confirmed
  by hitting the actual compile error) — they're member functions of a
  small private class instead, since member functions can reference each
  other regardless of declaration order.

### `src/commonMain/kotlin/de/jonaswolf/geometry/PolyBoolGeometryEngine.kt`

`class PolyBoolGeometryEngine(epsilon: Epsilon = Epsilon.default) : GeometryEngine`
— the only implementation. `area`/`contains`/`intersects` are derived
(`contains(outer, inner)` = `difference(inner, outer)` has no regions left;
`intersects(a, b)` = `intersect(a, b)` has any regions); `intersection`/
`difference` call the vendored `PolyBool` directly and serialize the result
back to GeoJSON via `polygonToGeoJson`.

### `src/commonTest/kotlin/de/jonaswolf/geometry/PolyBoolGeometryEngineTest.kt`

Nine tests, all currently passing: area of a simple square; intersection
and difference area of two overlapping squares; intersects true/false for
overlapping/non-overlapping squares; contains true/false for a fully-inside
vs. boundary-straddling square; **area of a polygon with a hole** (outer
10×10 minus a 2×2 hole = 96 — the test that actually exercises the
hole-subtraction/nesting-hierarchy logic, not just simple single-ring
shapes); area of a MultiPolygon (sum of its parts). Combined with
`GeoMathTest` (3 tests, unchanged), all 12 commonTest tests pass on the JVM
target; both `jvm` and `wasmJs` compile.

**Not yet done**: a cross-check against real OSM administrative-boundary
GeoJSON, comparing `PolyBoolGeometryEngine`'s output to what the Android
app's Esri-based `BackendCalculationHelper` produces for the same input —
the hand-built fixtures above validate the *logic* (holes, MultiPolygons,
basic set operations) but not behavior on real, larger, potentially
messier region/street boundary data.

## Used by

- Consumed as a composite build (`includeBuild`) from
  `KnowYourCity2022-multi/:shared` during local development (see that
  repo's `settings.gradle.kts`).

## Open items

- **Cross-check against real region data** (see above) — the actual
  remaining risk before trusting this for anything beyond development.
- **License note for consumers**: this repo is Apache-2.0, but the vendored
  `de.stefan_oltmann.polybool.*` files remain MIT-licensed (compatible,
  but a separate notice — see `LICENSE-polybool-kotlin.txt`). Keep that
  file and the in-file copyright headers intact if this code is copied
  elsewhere.
- No `iosArm64`/other Kotlin/Native targets yet — only `jvm` and `wasmJs`.
  Everything here is already pure `commonMain`, so extending to more
  targets should be low-risk (unlike when `GeometryEngine` was still
  expected to need a real per-platform GIS engine).
