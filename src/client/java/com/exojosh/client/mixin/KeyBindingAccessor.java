package com.exojosh.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code KeyMapping.clickCount}, the private counter behind
 * {@link KeyMapping#consumeClick()}.
 *
 * Discrete actions use {@code clickCount}, consumed by
 * {@link KeyMapping#consumeClick()}; continuous actions use the held state
 * exposed by {@link KeyMapping#isDown()}. The counter is private, so the
 * companion's simulated presses increment it through this accessor.
 */
@Mixin(KeyMapping.class)
public interface KeyBindingAccessor {

    @Accessor("clickCount")
    int thorhud$getClickCount();

    @Accessor("clickCount")
    void thorhud$setClickCount(int clickCount);
}
