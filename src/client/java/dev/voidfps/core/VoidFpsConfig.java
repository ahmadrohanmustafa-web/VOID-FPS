package dev.voidfps.core;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VoidFpsConfig {
    public boolean hudEnabled = false;
    public boolean tinyPickupCullingEnabled = true;
    public boolean itemEntityCullingEnabled = true;
    public boolean experienceOrbCullingEnabled = true;
    public boolean tinyParticleCullingEnabled = true;
    public boolean adaptiveCullingEnabled = true;
    public double minimumPickupPixels = 0.75;
    public double minimumParticlePixels = 0.35;
    private double pickupProjectionScale = calculateProjectionScale(minimumPickupPixels);
    private double particleProjectionScale = calculateProjectionScale(minimumParticlePixels);
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("voidfps.properties");

    public void load() {
        if (!Files.exists(file)) { refreshDerivedValues(); save(); return; }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            hudEnabled = readBoolean(properties, "hudEnabled", false);
            tinyPickupCullingEnabled = readBoolean(properties, "tinyPickupCullingEnabled", true);
            itemEntityCullingEnabled = readBoolean(properties, "itemEntityCullingEnabled", true);
            experienceOrbCullingEnabled = readBoolean(properties, "experienceOrbCullingEnabled", true);
            tinyParticleCullingEnabled = readBoolean(properties, "tinyParticleCullingEnabled", true);
            adaptiveCullingEnabled = readBoolean(properties, "adaptiveCullingEnabled", true);
            minimumPickupPixels = readDouble(properties, "minimumPickupPixels", 0.75, 0.25, 2.0);
            minimumParticlePixels = readDouble(properties, "minimumParticlePixels", 0.35, 0.1, 1.0);
            refreshDerivedValues();
        } catch (IOException e) {
            refreshDerivedValues();
            VoidFpsClient.LOGGER.warn("Could not read VOID FPS config; using defaults.", e);
        }
    }

    private static boolean readBoolean(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static double readDouble(Properties p, String key, double fallback, double min, double max) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(p.getProperty(key, Double.toString(fallback))))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public double pickupProjectionScale() { return pickupProjectionScale; }
    public double particleProjectionScale() { return particleProjectionScale; }

    private void refreshDerivedValues() {
        pickupProjectionScale = calculateProjectionScale(minimumPickupPixels);
        particleProjectionScale = calculateProjectionScale(minimumParticlePixels);
    }

    private static double calculateProjectionScale(double pixels) {
        return 1.0 / (2.0 * pixels * Math.tan(Math.toRadians(35.0)));
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("hudEnabled", Boolean.toString(hudEnabled));
        p.setProperty("tinyPickupCullingEnabled", Boolean.toString(tinyPickupCullingEnabled));
        p.setProperty("itemEntityCullingEnabled", Boolean.toString(itemEntityCullingEnabled));
        p.setProperty("experienceOrbCullingEnabled", Boolean.toString(experienceOrbCullingEnabled));
        p.setProperty("tinyParticleCullingEnabled", Boolean.toString(tinyParticleCullingEnabled));
        p.setProperty("adaptiveCullingEnabled", Boolean.toString(adaptiveCullingEnabled));
        p.setProperty("minimumPickupPixels", Double.toString(minimumPickupPixels));
        p.setProperty("minimumParticlePixels", Double.toString(minimumParticlePixels));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) { p.store(writer, "VOID FPS Core settings"); }
        } catch (IOException e) { VoidFpsClient.LOGGER.warn("Could not save VOID FPS config.", e); }
    }
}
