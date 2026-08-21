# geometry-api-kotlin

A Kotlin Multiplatform geometry abstraction, built for
[KnowYourCity](https://github.com/jwtue)'s Android→web (Kotlin
Multiplatform/Compose Multiplatform) expansion. Two parts, deliberately kept
separate because they have very different portability stories:

- **`GeoMath`** — great-circle (haversine) point distance and polyline
  length. Pure Kotlin, no platform dependency, fully implemented on every
  target. Matches the formula and Earth radius (6,371,009 m) of
  [`com.google.maps.android.SphericalUtil`](https://github.com/googlemaps/android-maps-utils),
  since that's what the KnowYourCity Android app's equivalent calculations
  are built on (`isSameStreet`, way/segment length in
  `BackendCalculationHelper`) — this is a from-scratch reimplementation of
  that formula, not a port of Android-maps-utils' code.
- **`GeometryEngine`** — an interface for the heavier polygon operations
  (area, contains, intersects, intersection, difference) the Android app
  currently gets from the
  [Esri Geometry API](https://github.com/Esri/geometry-api-java)
  (`com.esri.geometry:esri-geometry-api`, public, Apache-2.0). Esri's
  library is JVM-only, so it can't be used from a Kotlin/Wasm browser
  target — `GeometryEngine` exists so callers can depend on one interface
  and swap the backend per platform. **Implementations are not part of this
  repo yet** — see [`AGENTS.md`](AGENTS.md) for the current status.

## Why not `expect`/`actual`?

`GeometryEngine` is a plain `interface` in `commonMain`, not an
`expect`/`actual` declaration. There's no single canonical implementation to
declare `expect` for, and doing so would force every target to ship a
(possibly stub) `actual` before any real implementation exists. Plain
per-platform classes implementing the interface compose better here.

## Targets

- `jvm`
- `wasmJs` (browser)

## Usage

```kotlin
dependencies {
    implementation("de.jonaswolf.geometry:geometry-api-kotlin:<version>")
}
```

```kotlin
import de.jonaswolf.geometry.GeoMath
import de.jonaswolf.geometry.GeoPoint

val meters = GeoMath.distanceMeters(GeoPoint(52.52, 13.405), GeoPoint(48.1351, 11.582))
```

`GeometryEngine` can be depended on today for its type, but there is no
constructible implementation yet — see below.

## License

Apache-2.0 — see [`LICENSE`](LICENSE).
