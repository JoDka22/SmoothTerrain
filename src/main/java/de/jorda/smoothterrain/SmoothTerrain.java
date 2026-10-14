package de.jorda.smoothterrain;
import net.fabricmc.api.ModInitializer;
public final class SmoothTerrain implements ModInitializer {
    @Override public void onInitialize() { TerrainConfig.load(); }
}
