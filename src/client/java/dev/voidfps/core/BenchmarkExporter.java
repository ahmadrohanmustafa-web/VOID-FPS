package dev.voidfps.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Exports raw frame samples together with a compact summary of the same capture. */
public final class BenchmarkExporter {
    private BenchmarkExporter() {}

    public static void export(FrameTimeMonitor monitor) {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("voidfps");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = directory.resolve("frame-times-" + stamp + ".csv");

        try {
            Files.createDirectories(directory);
            double[] samples = monitor.snapshot();
            double average = monitor.averageFrameTimeMs();
            double p99 = monitor.percentileFrameTimeMs(0.99);
            StringBuilder csv = new StringBuilder(128 + samples.length * 12);
            csv.append("metric,value\n");
            csv.append("sample_count,").append(samples.length).append('\n');
            csv.append("average_frame_time_ms,").append(format(average)).append('\n');
            csv.append("p99_frame_time_ms,").append(format(p99)).append('\n');
            csv.append("one_percent_low_fps,").append(format(p99 <= 0.0 ? 0.0 : 1000.0 / p99)).append('\n');
            csv.append("minimum_frame_time_ms,").append(format(monitor.minimumFrameTimeMs())).append('\n');
            csv.append("maximum_frame_time_ms,").append(format(monitor.maximumFrameTimeMs())).append('\n');
            csv.append("\nsample,frame_time_ms\n");
            for (int i = 0; i < samples.length; i++) {
                csv.append(i + 1).append(',').append(format(samples[i])).append('\n');
            }
            Files.writeString(file, csv);
            VoidFpsClient.LOGGER.info("Exported {} frame samples and summary metrics to {}", samples.length, file);
        } catch (IOException e) {
            VoidFpsClient.LOGGER.error("Could not export benchmark CSV.", e);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
