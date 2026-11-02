package dev.voidfps.core;

import java.util.Arrays;

/**
 * Low-allocation rolling frame-time recorder.
 *
 * Keeps the most recent 30 seconds at 60 FPS, allowing exports to include
 * useful 1% low and percentile measurements rather than a very short sample.
 */
public final class FrameTimeMonitor {
    private static final int CAPACITY = 1800;
    private final double[] values = new double[CAPACITY];
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

    public synchronized double averageFrameTimeMs() {
        if (count == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < count; i++) sum += values[i];
        return sum / count;
    }

    public synchronized double percentileFrameTimeMs(double percentile) {
        if (count == 0) return 0.0;
        double[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(Math.max(0.0, Math.min(1.0, percentile)) * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    public synchronized double onePercentLowFps() {
        double slowFrame = percentileFrameTimeMs(0.99);
        return slowFrame <= 0.0 ? 0.0 : 1000.0 / slowFrame;
    }

    public synchronized double minimumFrameTimeMs() {
        if (count == 0) return 0.0;
        double minimum = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) minimum = Math.min(minimum, values[i]);
        return minimum;
    }

    public synchronized double maximumFrameTimeMs() {
        if (count == 0) return 0.0;
        double maximum = 0.0;
        for (int i = 0; i < count; i++) maximum = Math.max(maximum, values[i]);
        return maximum;
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
}
