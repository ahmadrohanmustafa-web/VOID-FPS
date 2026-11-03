package dev.voidfps.core;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for VOID FPS's block and terrain rendering work.
 *
 * This initializer intentionally registers no key bindings, HUD, or
 * user-facing optimization toggles. Rendering optimizations are automatic.
 */
public final class VoidFpsClient implements ClientModInitializer {
    public static final String MOD_ID = "voidfps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("VOID FPS block-rendering module initialized.");
    }
}
