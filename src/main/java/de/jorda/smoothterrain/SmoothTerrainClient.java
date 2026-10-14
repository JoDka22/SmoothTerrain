package de.jorda.smoothterrain;

import net.fabricmc.api.ClientModInitializer;

public final class SmoothTerrainClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TerrainConfig.refreshRegistry();
        TerrainCollision.allowedWorld = world -> !(world instanceof net.minecraft.client.multiplayer.ClientLevel)
            || net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer() || TerrainConfig.multiplayerCollisions;
    }
}
