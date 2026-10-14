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

## Contributing and reporting issues

Describe the problem, reproduction steps and relevant Minecraft/mod versions. Before posting screenshots or logs, remove personal information and server addresses.

## License

Copyright (c) 2026 **JoDka22**. Released under the [MIT License](LICENSE).
