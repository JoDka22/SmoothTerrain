package de.jorda.smoothterrain;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

/** Registered in Options before loading, so vanilla saves/remaps the binding. */
public final class TerrainKeys {
    public static final KeyMapping SETTINGS=new KeyMapping("key.smoothterrain.settings",InputConstants.KEY_M,
        KeyMapping.Category.register(Identifier.parse("smoothterrain:settings")));
    public static void tick(Minecraft client) {
        while(SETTINGS.consumeClick()) if(client.level!=null&&client.gui.screen()==null&&client.gui.overlay()==null)
            client.gui.setScreen(new RoundingScreen(null));
    }
    private TerrainKeys() {}
}
