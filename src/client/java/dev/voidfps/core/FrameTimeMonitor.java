package dev.voidfps.core;

import java.util.Arrays;

public final class FrameTimeMonitor {
    private static final int CAPACITY = 240;
    private final double[] frameTimesMs = new double[CAPACITY];
    private int nextIndex;
    private int sampleCount;
    private long previousFrameNanos;

    public synchronized void recordFrame(long nowNanos) {
        if (previousFrameNanos != 0L) {
            double frameMs = (nowNanos - previousFrameNanos) / 1_000_000.0;
            if (frameMs > 0.0 && frameMs < 10_000.0) {
                frameTimesMs[nextIndex] = frameMs;
                nextIndex = (nextIndex + 1) % CAPACITY;
                sampleCount = Math.min(sampleCount + 1, CAPACITY);
            }
        }
        previousFrameNanos = nowNanos;
    }

    public synchronized double averageFrameTimeMs() {
        if (sampleCount == 0) return 0.0;
        double total = 0.0;
        for (int i = 0; i < sampleCount; i++) total += frameTimesMs[i];
        return total / sampleCount;
    }

    public synchronized double percentileFrameTimeMs(double percentile) {
        if (sampleCount == 0) return 0.0;
        double[] sorted = Arrays.copyOf(frameTimesMs, sampleCount);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(Math.max(0.0, Math.min(1.0, percentile)) * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    public synchronized double[] snapshot() {
        double[] result = new double[sampleCount];
        int start = (nextIndex - sampleCount + CAPACITY) % CAPACITY;
        for (int i = 0; i < sampleCount; i++) result[i] = frameTimesMs[(start + i) % CAPACITY];
        return result;
    }
}
