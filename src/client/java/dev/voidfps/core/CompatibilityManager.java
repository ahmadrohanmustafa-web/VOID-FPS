package dev.voidfps.core;

import net.fabricmc.loader.api.FabricLoader;
import java.util.List;

public final class CompatibilityManager {
    private static final List<String> KNOWN_OPTIMIZERS = List.of(
            "sodium", "lithium", "ferritecore", "immediatelyfast", "entityculling");
    private CompatibilityManager() {}
    public static void logDetectedMods() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : KNOWN_OPTIMIZERS) {
            if (loader.isModLoaded(modId)) VoidFpsClient.LOGGER.info(
                    "Detected optional optimization mod '{}'. VOID FPS Core will not replace its systems.", modId);
        }
    }
}
