package dev.voidfps.core;

import java.util.Arrays;

/**
 * Low-allocation rolling frame-time recorder.
 *
 * Keeps the most recent 30 seconds at 60 FPS. Summary calculations reuse a
 * fixed scratch buffer so periodic HUD refreshes do not allocate and sort a
 * new array every time.
 */
public final class FrameTimeMonitor {
    private static final int CAPACITY = 1800;
    private final double[] values = new double[CAPACITY];
    private final double[] percentileScratch = new double[CAPACITY];
    private int next;
    private int count;
    private long previous;

    public synchronized void recordFrame(long now) {
        if (previous != 0L) {
            double ms = (now - previous) / 1_000_000.0;
            if (ms > 0.0 && ms < 10_000.0) {
                values[next] = ms;
                next = (next + 1) % CAPACITY;
                count = Math.min(count + 1, CAPACITY);
            }
        }
        previous = now;
    }

    /** Calculates all commonly displayed statistics in one pass and one sort. */
    public synchronized Stats stats() {
        if (count == 0) return new Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0L)

        double sum = 0.0;
        double minimum = Double.MAX_VALUE;
        double maximum = 0.0;
        long framesOver50Ms = 0L;
        long framesOver100Ms = 0L;
        for (int i = 0; i < count; i++) {
            double value = values[i];
            sum += value;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            if (value > 50.0) framesOver50Ms++;
            if (value > 100.0) framesOver100Ms++;
            percentileScratch[i] = value;
        }

        Arrays.sort(percentileScratch, 0, count);
        int p99Index = Math.max(0, Math.min(count - 1, (int) Math.ceil(count * 0.99) - 1));
        double p99 = percentileScratch[p99Index];
        return new Stats(count, sum / count, p99,
                p99 <= 0.0 ? 0.0 : 1000.0 / p99, minimum, maximum,
                framesOver50Ms, framesOver100Ms);
    }

    public synchronized double averageFrameTimeMs() {
        return stats().averageFrameTimeMs();
    }

    public synchronized double percentileFrameTimeMs(double percentile) {
        if (count == 0) return 0.0;
        for (int i = 0; i < count; i++) percentileScratch[i] = values[i];
        Arrays.sort(percentileScratch, 0, count);
        int index = (int) Math.ceil(Math.max(0.0, Math.min(1.0, percentile)) * count) - 1;
        return percentileScratch[Math.max(0, Math.min(count - 1, index))];
    }

    public synchronized double onePercentLowFps() {
        return stats().onePercentLowFps();
    }

    public synchronized double minimumFrameTimeMs() {
        return stats().minimumFrameTimeMs();
    }

    public synchronized double maximumFrameTimeMs() {
        return stats().maximumFrameTimeMs();
    }

    public synchronized int sampleCount() {
        return count;
    }

    public synchronized double[] snapshot() {
        double[] copy = new double[count];
        int start = (next - count + CAPACITY) % CAPACITY;
        for (int i = 0; i < count; i++) copy[i] = values[(start + i) % CAPACITY];
        return copy;
    }

    public record Stats(
            int sampleCount,
            double averageFrameTimeMs,
            double p99FrameTimeMs,
            double onePercentLowFps,
            double minimumFrameTimeMs,
            double maximumFrameTimeMs,
            long framesOver50Ms,
            long framesOver100Ms
    ) {}
}
