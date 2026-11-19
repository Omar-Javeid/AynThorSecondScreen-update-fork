package com.exojosh.client;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Offscreen item rendering is unavailable with Minecraft 26.3's GUI submit
 * pipeline. Returning {@code null} makes the caller use its resource-texture
 * fallback; an unresolved texture is still reported explicitly to the app.
 */
public final class ItemIconRenderer {

    public static final int ICON_SIZE = 64;

    private ItemIconRenderer() {
    }

    public static void renderBase64Png(ItemStack stack, Consumer<String> onComplete) {
        onComplete.accept(null);
    }
}
