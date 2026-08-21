# AGENTS.md

Fact-based entry point for AI coding agents in this repository. Describes
only what is concretely observable in the code/repo; open items are marked
explicitly instead of guessed at.

## Project overview

- **What:** a Kotlin Multiplatform geometry module with two independent
  parts (see README.md for the "why they're separate" reasoning):
  a fully-implemented great-circle math helper (`GeoMath`), and an
  interface-only abstraction over polygon GIS operations (`GeometryEngine`)
  that has **no implementation in this repo yet**.
- **Why it exists:** built for [KnowYourCity](https://github.com/jwtue)'s
  Android→web expansion. The Android app's `BackendCalculationHelper`
  (region/street-building logic) uses the
  [Esri Geometry API](https://github.com/Esri/geometry-api-java) (public,
  Apache-2.0, `com.esri.geometry:esri-geometry-api`) for polygon operations
  and `com.google.maps.android.SphericalUtil` for point/line distance —
  Esri is JVM-only and can't run on a Kotlin/Wasm browser target, which is
  the reason `GeometryEngine` exists as a swappable abstraction.
- **Language/build:** Kotlin Multiplatform, Gradle Kotlin DSL, version
  catalog (`gradle/libs.versions.toml`). Targets: `jvm`, `wasmJs` (browser),
  declared in `build.gradle.kts`.
- **group:artifact:** `de.jonaswolf.geometry:geometry-api-kotlin`
  (`build.gradle.kts`: `group = "de.jonaswolf.geometry"`, `rootProject.name`
  in `settings.gradle.kts` = `"geometry-api-kotlin"`). Single-module repo —
  the root project itself is the library, no subprojects.

## Files

### `src/commonMain/kotlin/de/jonaswolf/geometry/GeoMath.kt`

- `data class GeoPoint(val latitude: Double, val longitude: Double)`.
- `object GeoMath` — `EARTH_RADIUS_METERS = 6371009.0` (`GeoMath.kt`),
  matching `com.google.maps.android.SphericalUtil.EARTH_RADIUS` exactly.
  - `fun distanceMeters(from: GeoPoint, to: GeoPoint): Double` — standard
    haversine great-circle distance. Implemented from scratch to match
    `SphericalUtil.computeAngleBetween`'s formula
    (`2 * asin(sqrt(haversin(dLat) + cos(lat1) * cos(lat2) * haversin(dLng))))
    * EARTH_RADIUS`), not copied from Android-maps-utils' source (which is
    Android-only and wasn't available as a dependency here).
  - `fun lengthMeters(points: List<GeoPoint>): Double` — sums
    `distanceMeters` over consecutive points (`zipWithNext`), for
    polyline/way length.
  - This is **fully implemented on every target** — no platform-specific
    code, no `expect`/`actual`, since it's plain trigonometry.

### `src/commonMain/kotlin/de/jonaswolf/geometry/GeometryEngine.kt`

`interface GeometryEngine` with five methods, all operating on raw GeoJSON
text in/out (not a shared geometry value type — see the file's doc comment
for why: GeoJSON is already the interchange format the Android app's
`GeoJson.kt` uses throughout, and it avoids designing a common geometry
model both a future Esri wrapper and a future Turf.js wrapper would have to
convert to/from anyway):

- `fun area(geoJson: String): Double`
- `fun contains(outer: String, inner: String): Boolean`
- `fun intersects(a: String, b: String): Boolean`
- `fun intersection(a: String, b: String): String`
- `fun difference(a: String, b: String): String`

This set matches exactly what `BackendCalculationHelper` in the Android app
calls Esri for today (`assignStreetsToRegions`, `getFittingChildRegions`,
`calculateRegionStreetRatios`, `GeoJson.calculateInverse`) — nothing broader
or more speculative.

**No implementing class exists in this repo yet.** Planned (not started):
- JVM/Android: wraps `com.esri.geometry:esri-geometry-api` directly (the
  Android app's existing dependency — same public, Apache-2.0 artifact,
  confirmed resolvable from plain Maven Central, no separate Esri repo
  needed).
- Wasm/browser: JS interop (`external`/`@JsModule`) with
  [Turf.js](https://turfjs.org/).

Before relying on both together for real region data: their outputs won't
be bit-identical (different libraries, different algorithms/precision for
intersection/area), so a fixture-based cross-check comparing both
implementations against the same real GeoJSON regions is planned before
this is used for anything beyond development.

### `src/commonTest/kotlin/de/jonaswolf/geometry/GeoMathTest.kt`

Three tests for `GeoMath`, all currently passing: zero distance for
identical points, a mathematically-derived check that 1° of longitude at
the equator equals `EARTH_RADIUS_METERS * (π/180)` (a direct consequence of
the constant, not an external reference value), and that `lengthMeters`
sums pairwise distances correctly. No tests exist for `GeometryEngine`
(nothing to test — no implementation yet).

## Used by

- Consumed as a composite build (`includeBuild`) from
  `KnowYourCity2022-multi/:shared` during local development (see that
  repo's `settings.gradle.kts`) — not yet published to JitPack from a real
  tag.

## Open items

- **`GeometryEngine` has no implementation** — this is the single biggest
  piece of unfinished work in this repo (see "Files" above). Everything
  else (packaging, `GeoMath`, tests) is done.
- Not yet tagged/released, so JitPack can't build it yet.
- No GitHub Actions workflow yet — deliberately deferred until both this
  repo and the sibling `kotlin-spaced-repetition` repo are further along
  (in this repo's case: until `GeometryEngine` has at least one real
  implementation, since a "build so it's directly usable" CI step for an
  interface with nothing implementing it wouldn't yet produce something
  useful to consume).
- No `iosArm64`/other Kotlin/Native targets yet — only `jvm` and `wasmJs`.
  `GeoMath` has no platform dependency so extending it to more targets is
  trivial; `GeometryEngine` would need a third actual implementation per
  additional target (e.g. a native GIS library for iOS).
