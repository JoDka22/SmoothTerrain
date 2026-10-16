# Smooth Terrain 2.0.0

Changes since **1.0.3** for Minecraft **26.3** / Fabric.

## Rounded movement and collisions

- Improved experimental uphill traversal, step selection and downhill contact while retaining Minecraft's normal jumping, gravity, falling and legal step height.
- Fixed jump-building beneath the player being blocked at high rounding. Placement checks now consider the prospective rounded collision geometry while preserving normal placement restrictions and obstruction by other entities.
- Fixed phantom collision columns above narrow pillars when collision rays only touched a shared mesh ridge.
- Fixed collision coverage at steep surfaces, re-entrant corners, narrow passages and thin mixed-material walls.
- Fixed stale neighbouring collision shapes after material-only block changes and chunk refreshes.
- **Safe Movement remains enabled by default.** Experimental rounded movement is opt-in; player hitboxes and legal step height have not been increased.

## Terrain and rendering

- Improved rounding around caves, ridges and narrow openings with directional clearance protection.
- Fixed exposed dirt triangles on upward-facing snow-covered slopes and snow discontinuities at vertical chunk-section boundaries.
- Improved material-boundary consistency while retaining Minecraft's pixel textures and biome colours.
- Retained configurable leaf/log rounding, Better Grass-style sides, vegetation offsets, blacklists and connected water/ice shorelines.

## Reliability and performance

- Hardened mesh-resource ownership and exception cleanup, configuration snapshots and cache invalidation.
- Retained bounded collision caches and earlier terrain-baking/allocation optimizations.
- Added a bounded placement-preview cache so repeated attempts do not continuously rebuild unchanged geometry.
- Expanded geometry, movement, placement-safety and deterministic fuzz regression checks. Local performance measurements cover specific workloads; this is not a promise of higher FPS, particularly with shaders.

## Compatibility and limits

- Fabric **26.3**, Java **25+**, Fabric Loader **0.19.5+**. Optional Sodium, Iris and Mod Menu integrations remain supported; install those mods separately.
- Vanilla and Sodium/Iris/Mellow scenarios were checked during development. The author has also playtested additional shader, Sodium and mod combinations; universal compatibility is not guaranteed.
- Final jump-building checks passed at **0/25/50/80/100%** rounding and in Safe Movement. Experimental movement remains a setting to choose deliberately.
- Dedicated and remote multiplayer were not tested in the final audit. Existing remote-server opt-in restrictions remain in place.
- Some steep obstacles require a normal jump. Short automated runs do not establish long-term memory-leak freedom or replace human playtesting.

See [VALIDATION.md](VALIDATION.md) for verification scope. Author: **JoDka22** · License: **MIT**.
