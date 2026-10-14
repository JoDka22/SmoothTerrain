package de.jorda.smoothterrain;

import net.fabricmc.api.DedicatedServerModInitializer;

public final class SmoothTerrainServer implements DedicatedServerModInitializer {
    @Override public void onInitializeServer() { TerrainConfig.refreshRegistry(); }
}
