# 2.0.0 validation scope

The final source includes the jump-building fixes validated in the private development candidate. Automated runtime checks used disposable singleplayer worlds. No personal saves or raw runtime logs are included in this repository.

## Automated checks

- A clean Java 25 `build verifySmoothing` checks the production JAR, metadata and geometry regressions. These dependency-free geometry checks are included under `src/test` and can be run from this repository.
- Geometry coverage includes 600 ray-tangency/order checks, 12,420 passage-clearance/wall-sign checks, 8,640 seeded volume samples, 5,000 independent triangle/cell comparisons, 4,096 prepared ray columns, UV density and section-seam checks.
- Private runtime QA covered 240 deterministic solver cases, 80 travel cases, 2,000 step-selection properties and 4,000 seeded fuzz moves. Those 320 solver/travel results remained functionally unchanged after the jump-building fix.
- Additional runtime fixtures covered thin walls, shafts, corridors, mixed stone/ice boundaries, material changes and chunk-section boundaries. The boundary suite performed 1,920 traces, 57,600 mesh/body checks and 172,800 interpolated eye-position checks; 1,080 material-cache comparisons passed.
- Jump-building passed ten direct placement configurations: Safe/Experimental movement at 0/25/50/80/100% rounding, with three consecutive blocks in each. Placement inside a standing body, through excluded solid blocks or obstructing another entity remained rejected. Preview invalidation was checked after terrain edits and rounding changes.
- Actual singleplayer input/placement-packet checks passed six configurations, placing 18 blocks and landing stably. The final candidate recorded no body penetrations or position corrections across 162 measured movement packets. These are automated client tests, not a human evaluation of movement feel or a remote multiplayer certification.

## Measured limits

The placement-preview cache avoids repeated mesh generation for unchanged attempts. Its warmed cache-hit workload is a microbenchmark; new targets and invalidation still require geometry work. No general FPS speedup is claimed.

Safe Movement remains the default and preserves vanilla collision behavior. Experimental rounded movement changes player collision geometry intentionally. Normal jump, gravity, fall processing, legal step height and player dimensions remain Minecraft's responsibility.

Vanilla and Sodium/Iris/Mellow development scenarios were exercised. Additional combinations were playtested by the author. Dedicated/remote servers, network latency and packet-loss scenarios were not tested in the final audit. Short runs do not prove freedom from every collision defect, compatibility issue or long-term memory leak.

Runtime helpers and instrumentation are excluded from the production JAR and this public source export. Only the portable geometry checks are distributed here. Reports should include the relevant mod versions, settings and reproduction steps, with private information removed from logs and screenshots.
