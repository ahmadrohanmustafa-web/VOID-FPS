package dev.voidfps.core;

import java.util.Arrays;

/**
 * Low-overhead rolling monitor for total client tick duration.
 * This measures the complete client tick callback window, not server tick time.
 */
public final class ClientTickMonitor {
    private static final int CAPACITY = 1200;
    private final long[] samplesNanos = new long[CAPACITY];
    private final long[] sortedScratch = new long[CAPACITY];
    private int next;
    private int count;
    private long tickStartedAt;

    public void beginTick(long nowNanos) {
        tickStartedAt = nowNanos;
    }

    public void endTick(long nowNanos) {
        if (tickStartedAt == 0L) return;
        long elapsed = nowNanos - tickStartedAt;
        tickStartedAt = 0L;
        if (elapsed <= 0L || elapsed > 10_000_000_000L) return;
        samplesNanos[next] = elapsed;
        next = (next + 1) % CAPACITY;
        count = Math.min(count + 1, CAPACITY);
    }

    public Stats stats() {
        if (count == 0) return new Stats(0, 0.0, 0.0, 0.0, 0L, 0L);
        long total = 0L;
        long maximum = 0L;
        long ticksOver50Ms = 0L;
        long ticksOver100Ms = 0L;
        
        for (int i = 0; i < count; i++) {
            long value = samplesNanos[i];
            total += value;
            maximum = Math.max(maximum, value);
            if (value > 50000000L) ticksOver50Ms++;
            if (value > 100000000L) ticksOver100Ms++;
            sortedScratch[i] = value;
        }
        Arrays.sort(sortedScratch, 0, count);
        int p95Index = Math.max(0, Math.min(count - 1, (int) Math.ceil(count * 0.95) - 1));
        return new Stats(count, total / (double) count / 1_000_000.0,
                sortedScratch[p95Index] / 1_000_000.0, maximum / 1_000_000.0,
                ticksOver50Ms, ticksOver100Ms);
    }

    public long[] snapshotNanos() {
        long[] copy = new long[count];
        int start = (next - count + CAPACITY) % CAPACITY;
        for (int i = 0; i < count; i++) copy[i] = samplesNanos[(start + i) % CAPACITY];
        return copy;
    }

    public record Stats(int sampleCount, double averageMs, double p95Ms, double maximumMs,
                        long ticksOver50Ms, long ticksOver100Ms) {}
}
