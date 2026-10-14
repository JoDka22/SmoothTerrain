# Smooth Terrain

![Smooth Terrain icon](src/main/resources/assets/smoothterrain/icon.png)

A Fabric mod that rounds Minecraft's natural terrain while preserving pixel textures and biome colours.

**Current release: 1.0.3 · Minecraft Java 26.3 · Java 25 · MIT**

## Features

- Adjustable terrain rounding, Better Grass-style sides, smoothed snow and connected shorelines.
- Separate options for leaf and log rounding.
- Block/tag blacklist and category filters for technical blocks, containers, fences, walls and panes.
- Press **M** to open settings; the key can be rebound. Mod Menu integration is optional.
- Vanilla rendering and optional Sodium/Iris integration.
- Safe vanilla movement by default. Experimental rounded player movement includes fixes for uphill sticking, singleplayer rubberbanding and downhill ground contact.

Most building materials, leaves and logs retain their original models by default. Natural materials such as raw stone can also be used in buildings; use the blacklist to exclude them. Smoothing does not replace saved block IDs or the world's block grid.

## Downloads and installation

Download the mod from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/smoothterrain) or [Modrinth](https://modrinth.com/mod/smoothterrain).

Use Minecraft **26.3**, Fabric Loader **0.19.5 or newer**, and Java **25**. Place one Smooth Terrain JAR in the `mods` folder and remove older versions before updating. Existing settings are preserved.

Mod Menu is optional. Fabric API and Cloth Config are not required. Install Sodium, Iris and shader packs separately. Do not install alongside NoCubes.

## Build from source

Install a Java 25 JDK and set `JAVA_HOME` to that JDK. The Gradle wrapper downloads the required Gradle distribution and build dependencies.

Linux/macOS:

```sh
./gradlew build verifySmoothing
```

Windows:

```powershell
.\gradlew.bat build verifySmoothing
```

The distributable mod is generated in `build/libs/`. Geometry regression checks run through `verifySmoothing` and are also part of `build`; test helpers are excluded from the production JAR.

Start a development client:

```sh
./gradlew runClient
```

For optional integrations:

```sh
./gradlew -PwithSodium -PwithModMenu runClient
```

## Compatibility and movement

Checked renderer versions include Sodium **0.9.3-alpha.1+mc26.3** and Iris **1.11.7+mc26.3**, with Iris/Mellow rendering checks. Additional renderer, shader and mod combinations have been playtested by the author.

Actual integrated-singleplayer movement checks cover uphill walking at 25/80/100% rounding, downhill walking, diagonal routes and repeated jumps with Vanilla and Sodium. Experimental movement remains opt-in. Safe movement preserves vanilla player and animal collisions; at high rounding the collision grid can differ from the visible surface. Live external multiplayer rounded movement is not certified.

See [CHANGELOG.md](CHANGELOG.md) for this release's changes and the scope of performance measurements.

## Relationship to NoCubes

Smooth Terrain is an independent mod inspired by the idea of rounded Minecraft terrain, first popularised by NoCubes by Cadiboo (LGPLv3). No NoCubes code or artwork is included. Smooth Terrain does not require or work together with NoCubes.

It is developed with the help of AI coding agents (Codex) by a non-programmer author.

### Source comparison

A token-level comparison of this repository (v1.0.3, 45 Java files) against the NoCubes `master` branch (122 Java files, build `38904f7`, 2024-08-20) found no copied code.

| Check | Result |
|---|---|
| NoCubes class and method names (`SDFMesher`, `SurfaceNets`, `NoCubesConfig`, `isSmoothable`, …) in this repo | 0 matches (and 0 the other way round) |
| Shared declared names | 19, all generic (Mixin plugin methods, `add`, `close`, `load`, …) |
| Identical comment lines | 0 |
| Identical string literals | 3, all JVM descriptors of Minecraft methods that both mods hook via Mixin |
| Longest identical code run | 21 tokens (Mixin annotations); only 2 runs of 20+ tokens, none of 30+ |
| Same structure with renamed identifiers | 5 runs of 30+ tokens, longest 36; manually checked: Mixin annotations, a standard `for` loop, a small index table, variable assignments |
| Control comparison against an unrelated Fabric mod (Mod Menu) | 2 runs of 30+ tokens, longest 42, so the overlap with NoCubes is within noise |

### Different approach

| | Smooth Terrain | NoCubes |
|---|---|---|
| Method | Surface Nets followed by Catmull-Clark subdivision on a solid/empty field | Surface Nets port (from a JavaScript library) on a float distance field, no subdivision |
| Meshers | one | several |
| Networking | none | yes |
| License | MIT | LGPLv3 |

Feature ideas such as Better Grass sides, snow, plant offsets and a block blacklist exist in both mods. Ideas are free; the code is not shared.

### Limits of this comparison

- It detects copied code and copies with renamed identifiers. It cannot detect a rewrite that only reuses logic.
- The comparison covers Java source only (no resources, no mixin target data).
- This repository was published as a single commit, so its development history cannot be inspected.
- This is not a legal analysis. If you find a specific similarity or licensing issue, please open an issue.

## Contributing and reporting issues

Describe the problem, reproduction steps and relevant Minecraft/mod versions. Before posting screenshots or logs, remove personal information and server addresses.

## License

Copyright (c) 2026 **JoDka22**. Released under the [MIT License](LICENSE).
