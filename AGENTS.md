# AGENTS.md

Fact-based entry point for AI coding agents in this repository. Describes
only what is concretely observable in the code/repo; open items are marked
explicitly instead of guessed at.

## Project overview

- **What:** a Kotlin Multiplatform geometry module: a great-circle math
  helper (`GeoMath`) and a polygon-GIS abstraction (`GeometryEngine`) with
  one implementation (`PolyBoolGeometryEngine`). Both are fully implemented,
  pure Kotlin, no platform-specific code anywhere in the repo.
- **Why it exists:** GIS toolkits like the
  [Esri Geometry API](https://github.com/Esri/geometry-api-java) (public,
  Apache-2.0, `com.esri.geometry:esri-geometry-api`) for polygon operations
  and `com.google.maps.android.SphericalUtil` for point/line distance are
  JVM-only, unusable from a Kotlin/Wasm browser target (or other non-JVM
  Kotlin Multiplatform targets). A JVM/Esri actual plus a Wasm/Turf.js (JS
  interop) actual per-platform split was the original plan; that was
  abandoned in favor of the single pure-Kotlin implementation described
  below once it turned out one already existed to build on (see
  "PolyBoolGeometryEngine.kt" below) — full Esri itself was evaluated and
  rejected as a port target (its boolean-op engine alone depends on a
  ~2,200-line shared planar-topology helper, `OperatorSimplifyLocalHelper.java`,
  on top of geometry primitives not counted in that figure — a multi-week
  undertaking for something that already exists elsewhere under a
  compatible license).
- **Language/build:** Kotlin Multiplatform, Gradle Kotlin DSL, version
  catalog (`gradle/libs.versions.toml`). Targets (`build.gradle.kts`): `jvm`,
  `wasmJs`, `js` (browser+Node.js), `linuxX64`, `linuxArm64`, `mingwX64`,
  `macosX64`, `macosArm64`, `iosX64`, `iosArm64`, `iosSimulatorArm64`. Uses
  `kotlinx-serialization-json` for GeoJSON parsing — resolves and works
  correctly on every target above, confirmed by actually running all 12
  `commonTest` tests (not just compiling) on `jvm`, `js` (browser and
  Node.js), and `mingwX64`; the rest are compile-verified only (no host
  available locally or in CI to execute their test binaries). `macosX64`
  triggers a deprecation warning as of Kotlin 2.3.20
  (`KotlinNativeTargetWithHostTests` / "Target is no longer available",
  https://kotl.in/native-targets-tiers) — still compiles, worth
  re-evaluating whether to drop it on a future Kotlin upgrade.
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
text in/out (not a shared geometry value type — GeoJSON is already a
natural interchange format for this kind of data):

- `fun area(geoJson: String): Double`
- `fun contains(outer: String, inner: String): Boolean`
- `fun intersects(a: String, b: String): Boolean`
- `fun intersection(a: String, b: String): String`
- `fun difference(a: String, b: String): String`

This is a deliberately narrow set — the common operations an
administrative-region/street-boundary processing pipeline needs (area
comparisons, containment/intersection tests, boolean set operations) —
rather than a broad general-purpose GIS API surface.

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
`GeoMathTest` (3 tests, unchanged), all 12 commonTest tests genuinely pass
on `jvm`, `js` (browser and Node.js), and `mingwX64`; every target compiles.

**Not yet done**: a cross-check against real OSM administrative-boundary
GeoJSON, comparing `PolyBoolGeometryEngine`'s output to what an Esri-based
implementation produces for the same input — the hand-built fixtures above
validate the *logic* (holes, MultiPolygons, basic set operations) but not
behavior on real, larger, potentially messier region/street boundary data.

## Released as

Tagged `v0.2.0` (the first release, `v0.1.0`, predates the target expansion
above and only published `jvm`/`wasmJs` — `v0.2.0` is the first release with
every target's package actually published, confirmed via the publish
workflow's own logs, not assumed from a green CI badge), published to this
repo's GitHub Packages Maven registry via
`.github/workflows/publish.yml` (triggers on `v*` tags); `.github/workflows/ci.yml`
runs `jvmTest`/`jsNodeTest` plus a compile check for every other target.

## Open items

- **Cross-check against real region data** (see above) — the actual
  remaining risk before trusting this for anything beyond development.
- **License note for consumers**: this repo is Apache-2.0, but the vendored
  `de.stefan_oltmann.polybool.*` files remain MIT-licensed (compatible,
  but a separate notice — see `LICENSE-polybool-kotlin.txt`). Keep that
  file and the in-file copyright headers intact if this code is copied
  elsewhere.
- `androidTarget()` deliberately not added — the plain `jvm` artifact
  already works as a normal dependency from Android projects (no
  Android-specific APIs used anywhere here).
- `macosX64`'s deprecation warning (see "Project overview") — revisit on a
  future Kotlin upgrade.
