package dev.voidfps.core;

/**
 * Lightweight client-thread counters for the pickup visibility optimization.
 * Counts skipped render-state extractions, not removed or despawned entities.
 */
public final class PickupCullingStats {
    private static long itemEntityStatesSkipped;
    private static long experienceOrbStatesSkipped;

    private PickupCullingStats() {}

    public static void recordItemEntitySkipped() {
        itemEntityStatesSkipped++;
    }

    public static void recordExperienceOrbSkipped() {
        experienceOrbStatesSkipped++;
    }

    public static long itemEntityStatesSkipped() {
        return itemEntityStatesSkipped;
    }

    public static long experienceOrbStatesSkipped() {
        return experienceOrbStatesSkipped;
    }

    public static long totalStatesSkipped() {
        return itemEntityStatesSkipped + experienceOrbStatesSkipped;
    }

    public static void reset() {
        itemEntityStatesSkipped = 0L;
        experienceOrbStatesSkipped = 0L;
    }
}
