package dev.voidfps.core;

/**
 * Small hysteresis controller for render-only culling.
 * It never changes world simulation, block rendering, or graphics quality.
 */
public final class AdaptiveCullingController {
    private double multiplier = 1.0;
    private int slowSamples;
    private int fastSamples;

    public void update(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0.0) return;

        if (frameTimeMs >= 50.0) {
            slowSamples++;
            fastSamples = 0;
        } else if (frameTimeMs <= 28.0) {
            fastSamples++;
            slowSamples = 0;
        } else {
            slowSamples = Math.max(0, slowSamples - 1);
            fastSamples = 0;
        }

        if (slowSamples >= 3) {
            multiplier = Math.min(2.5, multiplier + 0.5);
            slowSamples = 0;
        } else if (fastSamples >= 12) {
            multiplier = Math.max(1.0, multiplier - 0.25);
            fastSamples = 0;
        }
    }

    public double multiplier() {
        return multiplier;
    }

    public void reset() {
        multiplier = 1.0;
        slowSamples = 0;
        fastSamples = 0;
    }
}
