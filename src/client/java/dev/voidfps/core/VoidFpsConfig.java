package dev.voidfps.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VoidFpsConfig {
    public boolean hudEnabled = true;
    public boolean tinyPickupCullingEnabled = true;
    public double minimumPickupPixels = 0.75;

    private double pickupProjectionScale = calculateProjectionScale(minimumPickupPixels);
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("voidfps.properties");

    public void load() {
        if (!Files.exists(file)) {
            refreshDerivedValues();
            save();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            hudEnabled = Boolean.parseBoolean(properties.getProperty("hudEnabled", "true"));
            tinyPickupCullingEnabled = Boolean.parseBoolean(
                    properties.getProperty("tinyPickupCullingEnabled", "true"));
            try {
                minimumPickupPixels = Double.parseDouble(
                        properties.getProperty("minimumPickupPixels", "0.75"));
            } catch (NumberFormatException ignored) {
                minimumPickupPixels = 0.75;
            }
            minimumPickupPixels = Math.max(0.25, Math.min(2.0, minimumPickupPixels));
            refreshDerivedValues();
        } catch (IOException e) {
            refreshDerivedValues();
            VoidFpsClient.LOGGER.warn("Could not read VOID FPS config; using defaults.", e);
        }
    }

    public double pickupProjectionScale() {
        return pickupProjectionScale;
    }

    private void refreshDerivedValues() {
        pickupProjectionScale = calculateProjectionScale(minimumPickupPixels);
    }

    private static double calculateProjectionScale(double minimumPixels) {
        return 1.0 / (2.0 * minimumPixels * Math.tan(Math.toRadians(35.0)));
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("hudEnabled", Boolean.toString(hudEnabled));
        properties.setProperty("tinyPickupCullingEnabled", Boolean.toString(tinyPickupCullingEnabled));
        properties.setProperty("minimumPickupPixels", Double.toString(minimumPickupPixels));

        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                properties.store(writer, "VOID FPS Core settings");
            }
        } catch (IOException e) {
            VoidFpsClient.LOGGER.warn("Could not save VOID FPS config.", e);
        }
    }
}
