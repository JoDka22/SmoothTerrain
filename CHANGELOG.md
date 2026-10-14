# Smooth Terrain 1.0.3

Changes since the published **1.0.2** release.

## Performance improvements

- Optimized terrain rendering with fewer redundant vertices, temporary objects and repeated block checks.
- Local terrain-bake benchmarks measured **4–9% less CPU time** and **8–15% fewer emitted vertices** compared with 1.0.2.
- Faster generation of rounded collision shapes: approximately **44% less calculation time** in the tested uncached collision workload.
- Reduced unnecessary selection-outline rebuilds when distant blocks change.

These measurements cover specific CPU workloads, not a guaranteed increase in overall FPS.

## Rounded movement fixes

- Fixed rubberbanding and position resets caused by mismatched server collision checks, including in singleplayer.
- Fixed the client repeatedly pushing players away from rounded slopes, causing uphill movement to stall or feel jerky.
- Improved ground contact when walking downhill, including where rounded surfaces extend above their original blocks.
- Improved walking across diagonal slopes and small terrain transitions while preserving jumping, landings, solid obstacles and normal cliff falling.

## Other fixes

- Fixed collision and selection-outline caches remaining outdated after changing appearance settings.
- Improved cache invalidation and reduced temporary allocations during selection-outline rendering.
- Preserved existing water, ice, grass, foliage and shader rendering fixes.

## Testing and compatibility

- Tested real singleplayer movement with both Vanilla and Sodium at **25%, 80% and 100% rounding**, including uphill and downhill walking, diagonal paths and repeated jumps.
- All twelve final movement routes completed without stalled ticks or corrective server teleports.
- Build, geometry, configuration, native-buffer cleanup and Vanilla/Sodium/Iris startup checks passed. Rendering optimizations were also checked with Iris and Mellow.
- Additional shader packs, Sodium versions and mod combinations have been playtested by the author.

**Rounded movement remains experimental and optional. Safe vanilla movement is still the default.** Existing settings are preserved when updating. Press **M** to open Smooth Terrain's settings.
