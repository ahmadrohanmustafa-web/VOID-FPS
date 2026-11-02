package dev.voidfps.core;

/** Counts particles whose render-state extraction was skipped. */
public final class ParticleCullingStats {
    private static long renderStatesSkipped;

    private ParticleCullingStats() {}

    public static void recordSkipped() { renderStatesSkipped++; }
    public static long totalStatesSkipped() { return renderStatesSkipped; }
    public static void reset() { renderStatesSkipped = 0L; }
}
