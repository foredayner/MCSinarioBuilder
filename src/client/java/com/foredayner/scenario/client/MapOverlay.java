package com.foredayner.scenario.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** The keeper's map as a texture, drawn over (or as) the layout preview. */
final class MapOverlay {

    private static final Identifier ID = Identifier.of("scenario-builder", "map_overlay");
    private static byte[] loaded;
    private static boolean ready;
    private static int width;
    private static int height;

    private MapOverlay() {
    }

    /** Make sure the texture shows these PNG bytes; false when there is no picture. */
    private static boolean load(byte[] png) {
        if (png == null || png.length == 0) {
            if (ready) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
                ready = false;
            }
            loaded = png;
            return false;
        }
        if (png != loaded) {
            loaded = png;
            try {
                NativeImage image = NativeImage.read(png);
                width = image.getWidth();
                height = image.getHeight();
                MinecraftClient.getInstance().getTextureManager().registerTexture(ID, new NativeImageBackedTexture(image));
                ready = true;
            } catch (Exception e) {
                ready = false;
            }
        }
        return ready;
    }

    /** Whether the selected scenario has a map picture to show. */
    static boolean available() {
        return load(ScenarioClientState.mapPng());
    }

    /** Draw the map stretched over a square, with the given alpha (0-255). */
    static void draw(DrawContext ctx, int x, int y, int size, int alpha) {
        if (!available()) {
            return;
        }
        int color = (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
        ctx.drawTexture(RenderLayer::getGuiTextured, ID, x, y, 0, 0, size, size, width, height, width, height, color);
    }
}
