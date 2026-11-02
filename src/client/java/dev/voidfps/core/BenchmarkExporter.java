package dev.voidfps.core;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class BenchmarkExporter {
    private BenchmarkExporter() {}
    public static void export(FrameTimeMonitor monitor) {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("voidfps");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path output = directory.resolve("frame-times-" + stamp + ".csv");
        try {
            Files.createDirectories(directory);
            StringBuilder csv = new StringBuilder("sample,frame_time_ms\n");
            double[] samples = monitor.snapshot();
            for (int i = 0; i < samples.length; i++) {
                csv.append(i + 1).append(',').append(String.format(java.util.Locale.ROOT, "%.4f", samples[i])).append('\n');
            }
            Files.writeString(output, csv);
            VoidFpsClient.LOGGER.info("Exported {} frame-time samples to {}", samples.length, output);
        } catch (IOException exception) {
            VoidFpsClient.LOGGER.error("Could not export VOID FPS benchmark.", exception);
        }
    }
}
