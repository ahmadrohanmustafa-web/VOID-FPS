package dev.voidfps.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Samples JVM garbage-collection counters at a low frequency.
 * This is diagnostic only and never requests or triggers a collection.
 */
public final class GarbageCollectionMonitor {
    private final List<GarbageCollectorMXBean> collectors =
            ManagementFactory.getGarbageCollectorMXBeans();
    private long previousCollections;
    private long previousTimeMs;
    private long totalCollections;
    private long totalTimeMs;
    private long recentPauseMs;
    private long lastPollNanos;

    public void poll(long nowNanos) {
        if (lastPollNanos != 0L && nowNanos - lastPollNanos < 1_000_000_000L) return;
        lastPollNanos = nowNanos;

        long collections = 0L;
        long timeMs = 0L;
        for (GarbageCollectorMXBean collector : collectors) {
            long count = collector.getCollectionCount();
            long time = collector.getCollectionTime();
            if (count > 0L) collections += count;
            if (time > 0L) timeMs += time;
        }

        if (previousCollections != 0L || previousTimeMs != 0L) {
            long countDelta = Math.max(0L, collections - previousCollections);
            long timeDelta = Math.max(0L, timeMs - previousTimeMs);
            totalCollections += countDelta;
            totalTimeMs += timeDelta;
            recentPauseMs = timeDelta;
        }
        previousCollections = collections;
        previousTimeMs = timeMs;
    }

    public Stats stats() {
        return new Stats(totalCollections, totalTimeMs, recentPauseMs);
    }

    public record Stats(long collectionsSinceStart, long collectionTimeMs, long lastSamplePauseMs) {}
}
