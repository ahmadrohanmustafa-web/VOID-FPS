package dev.voidfps.core;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class BenchmarkExporter {
    private BenchmarkExporter() {}

    public static void export(FrameTimeMonitor monitor, ClientTickMonitor tickMonitor) {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("voidfps");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = directory.resolve("performance-" + stamp + ".csv");
        try {
            Files.createDirectories(directory);
            FrameTimeMonitor.Stats frameStats = monitor.stats();
            double[] frameSamples = monitor.snapshot();
            ClientTickMonitor.Stats tickStats = tickMonitor.stats();
            long[] tickSamples = tickMonitor.snapshotNanos();

            StringBuilder csv = new StringBuilder(1024 + frameSamples.length * 12 + tickSamples.length * 14);
            csv.append("metric,value\n");
            csv.append("frame_sample_count,").append(frameStats.sampleCount()).append('\n');
            csv.append("average_frame_time_ms,").append(format(frameStats.averageFrameTimeMs())).append('\n');
            csv.append("p99_frame_time_ms,").append(format(frameStats.p99FrameTimeMs())).append('\n');
            csv.append("one_percent_low_fps,").append(format(frameStats.onePercentLowFps())).append('\n');
            csv.append("minimum_frame_time_ms,").append(format(frameStats.minimumFrameTimeMs())).append('\n');
            csv.append("maximum_frame_time_ms,").append(format(frameStats.maximumFrameTimeMs())).append('\n');
            csv.append("client_tick_sample_count,").append(tickStats.sampleCount()).append('\n');
            GarbageCollectionMonitor.Stats gcStats = VoidFpsClient.garbageCollectionStats();
            csv.append("gc_collections_since_start,").append(gcStats.collectionsSinceStart()).append('\n');
            csv.append("gc_collection_time_ms,").append(gcStats.collectionTimeMs()).append('\n');
            csv.append("gc_last_sample_pause_ms,").append(gcStats.lastSamplePauseMs()).append('\n');
            csv.append("average_client_tick_ms,").append(format(tickStats.averageMs())).append('\n');
            csv.append("p95_client_tick_ms,").append(format(tickStats.p95Ms())).append('\n');
            csv.append("maximum_client_tick_ms,").append(format(tickStats.maximumMs())).append('\n');
            csv.append("item_entity_render_states_skipped,").append(PickupCullingStats.itemEntityStatesSkipped()).append('\n');
            csv.append("experience_orb_render_states_skipped,").append(PickupCullingStats.experienceOrbStatesSkipped()).append('\n');
            csv.append("particle_render_states_skipped,").append(ParticleCullingStats.totalStatesSkipped()).append('\n');
            csv.append("\nframe_sample,frame_time_ms\n");
            for (int i = 0; i < frameSamples.length; i++) {
                csv.append(i + 1).append(',').append(format(frameSamples[i])).append('\n');
            }
            csv.append("\ntick_sample,client_tick_time_ms\n");
            for (int i = 0; i < tickSamples.length; i++) {
                csv.append(i + 1).append(',').append(format(tickSamples[i] / 1_000_000.0)).append('\n');
            }
            Files.writeString(file, csv);
            VoidFpsClient.LOGGER.info("Exported {} frame samples and {} client tick samples to {}",
                    frameSamples.length, tickSamples.length, file);
        } catch (IOException e) {
            VoidFpsClient.LOGGER.error("Could not export performance CSV.", e);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
