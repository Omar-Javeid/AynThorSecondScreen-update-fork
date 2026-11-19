package com.exojosh;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both-side) mod entry point.
 * <p>
 * This is a minimal placeholder required by the Fabric template. All HUD logic
 * lives in the client-only source set ({@code src/client}) and is initialised
 * by {@code com.exojosh.client.ThorHudClient}.
 */
public class AynthorSecondScreen implements ModInitializer {
	public static final String MOD_ID = "aynthor_secondscreen";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Client-side work is handled by ThorHudClient (ClientModInitializer).
		// This common entry point exists only to satisfy the Fabric template.
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
