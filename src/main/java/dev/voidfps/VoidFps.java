package dev.voidfps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for VOID FPS.
 *
 * <p>This class intentionally contains no rendering logic. Every optimization that is added
 * must be backed by method signatures dumped from the real Minecraft 26.3 jar
 * (see {@code tools/inspect_targets.py} and {@code docs/NEXT_STEPS.md}).</p>
 */
public final class VoidFps {
    public static final String MOD_ID = "voidfps";
    public static final Logger LOGGER = LoggerFactory.getLogger("VOID FPS");

    private VoidFps() {
    }
}
