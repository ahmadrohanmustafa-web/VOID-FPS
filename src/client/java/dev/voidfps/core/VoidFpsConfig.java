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
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("voidfps.properties");

    public void load() {
        if (!Files.exists(file)) { save(); return; }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            hudEnabled = Boolean.parseBoolean(properties.getProperty("hudEnabled", "true"));
        } catch (IOException exception) {
            VoidFpsClient.LOGGER.warn("Could not read VOID FPS config; defaults will be used.", exception);
        }
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("hudEnabled", Boolean.toString(hudEnabled));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) { properties.store(writer, "VOID FPS Core settings"); }
        } catch (IOException exception) {
            VoidFpsClient.LOGGER.warn("Could not save VOID FPS config.", exception);
        }
    }
}
