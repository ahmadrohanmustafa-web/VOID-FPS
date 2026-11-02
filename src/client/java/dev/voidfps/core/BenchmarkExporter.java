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

    public static void export(FrameTimeMonitor monitor) {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("voidfps");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = directory.resolve("frame-times-" + stamp + ".csv");
        try {
            Files.createDirectories(directory);
            FrameTimeMonitor.Stats stats = monitor.stats();
            double[] samples = monitor.snapshot();
            StringBuilder csv = new StringBuilder(512 + samples.length * 12);
            csv.append("metric,value\n");
            csv.append("sample_count,").append(stats.sampleCount()).append('\n');
            csv.append("average_frame_time_ms,").append(format(stats.averageFrameTimeMs())).append('\n');
            csv.append("p99_frame_time_ms,").append(format(stats.p99FrameTimeMs())).append('\n');
            csv.append("one_percent_low_fps,").append(format(stats.onePercentLowFps())).append('\n');
            csv.append("minimum_frame_time_ms,").append(format(stats.minimumFrameTimeMs())).append('\n');
            csv.append("maximum_frame_time_ms,").append(format(stats.maximumFrameTimeMs())).append('\n');
            csv.append("item_entity_render_states_skipped,").append(PickupCullingStats.itemEntityStatesSkipped()).append('\n');
            csv.append("experience_orb_render_states_skipped,").append(PickupCullingStats.experienceOrbStatesSkipped()).append('\n');
            csv.append("particle_render_states_skipped,").append(ParticleCullingStats.totalStatesSkipped()).append('\n');
            csv.append("total_pickup_render_states_skipped,").append(PickupCullingStats.totalStatesSkipped()).append('\n');
            csv.append("\nsample,frame_time_ms\n");
            for (int i = 0; i < samples.length; i++) csv.append(i + 1).append(',').append(format(samples[i])).append('\n');
            Files.writeString(file, csv);
            VoidFpsClient.LOGGER.info("Exported {} frame samples and summary metrics to {}", samples.length, file);
        } catch (IOException e) { VoidFpsClient.LOGGER.error("Could not export benchmark CSV.", e); }
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.4f", value); }
}
