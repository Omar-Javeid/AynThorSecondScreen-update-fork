package com.exojosh.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side mod entry point (Fabric template placeholder).
 * <p>
 * All HUD logic is initialised by {@code com.exojosh.client.ThorHudClient}
 * instead of this class.
 */
public class AynthorSecondScreenClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Client-side work is handled by ThorHudClient (ClientModInitializer).
		// This entry point exists only to satisfy the Fabric template.
	}
}