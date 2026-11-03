package dev.voidfps.client;

import dev.voidfps.VoidFps;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Runs automatically; there is no configuration, keybind or UI.
 *
 * <p>No optimization is active in this build. This class says so in the log so that nobody
 * mistakes a successful launch for a performance gain.</p>
 */
public final class VoidFpsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VoidFps.LOGGER.info(
                "VOID FPS loaded. No block/terrain optimizations are active in this build: "
                        + "no mixin target has been verified against Minecraft 26.3 yet.");
    }
}
