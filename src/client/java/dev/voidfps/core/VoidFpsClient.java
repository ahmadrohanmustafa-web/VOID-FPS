package dev.voidfps.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoidFpsClient implements ClientModInitializer {
    public static final String MOD_ID = "voidfps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final FrameTimeMonitor MONITOR = new FrameTimeMonitor();
    private static final VoidFpsConfig CONFIG = new VoidFpsConfig();

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static KeyMapping exportKey;
    private static KeyMapping hudKey;
    private static KeyMapping pickupCullingKey;
    private static KeyMapping resetCountersKey;

    private static int refreshCounter;
    private static String cachedText = "";
    private static volatile int cachedScreenHeight;

    @Override
    public void onInitializeClient() {
        CONFIG.load();

        exportKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.voidfps.export_benchmark", InputConstants.KEY_F9, CATEGORY));
        hudKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.voidfps.toggle_hud", InputConstants.KEY_F10, CATEGORY));
        pickupCullingKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.voidfps.toggle_pickup_culling", InputConstants.KEY_F11, CATEGORY));
        resetCountersKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.voidfps.reset_counters", InputConstants.KEY_F12, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            cachedScreenHeight = client.getWindow().getHeight();

            while (exportKey.consumeClick()) {
                BenchmarkExporter.export(MONITOR);
                notifyPlayer(client, "VOID FPS: CSV exported to config/voidfps/");
            }

            while (hudKey.consumeClick()) {
                CONFIG.hudEnabled = !CONFIG.hudEnabled;
                CONFIG.save();
                notifyPlayer(client, "VOID FPS monitor: " + (CONFIG.hudEnabled ? "ON" : "OFF"));
            }

            while (pickupCullingKey.consumeClick()) {
                CONFIG.tinyPickupCullingEnabled = !CONFIG.tinyPickupCullingEnabled;
                CONFIG.save();
                notifyPlayer(client, "VOID FPS pickup culling: "
                        + (CONFIG.tinyPickupCullingEnabled ? "ON" : "OFF"));
            }

            while (resetCountersKey.consumeClick()) {
                PickupCullingStats.reset();
                notifyPlayer(client, "VOID FPS pickup counters reset.");
            }
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "performance_monitor"),
                (graphics, delta) -> renderHud(Minecraft.getInstance(), graphics));

        CompatibilityManager.logDetectedMods();
        LOGGER.info("VOID FPS Core initialized. Graphics settings are left unchanged. F9 exports benchmark data, F10 toggles the monitor, F11 toggles pickup culling, F12 resets pickup counters.");
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        } else {
            LOGGER.info(message);
        }
    }

    public static boolean shouldCullItemEntities() {
        return CONFIG.tinyPickupCullingEnabled && CONFIG.itemEntityCullingEnabled;
    }

    public static boolean shouldCullExperienceOrbs() {
        return CONFIG.tinyPickupCullingEnabled && CONFIG.experienceOrbCullingEnabled;
    }

    public static int cachedScreenHeight() {
        return cachedScreenHeight;
    }

    public static double pickupProjectionScale() {
        return CONFIG.pickupProjectionScale();
    }

    private static void renderHud(Minecraft client, GuiGraphicsExtractor graphics) {
        MONITOR.recordFrame(System.nanoTime());

        if (!CONFIG.hudEnabled || client.level == null || client.player == null) {
            return;
        }
        if (++refreshCounter >= 8) {
            refreshCounter = 0;
            cachedText = buildHudText(client);
        }
        graphics.text(client.font, cachedText, 8, 8, 0xFFE8F2FF);
    }

    private static String buildHudText(Minecraft client) {
        long ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                / (1024L * 1024L);
        FrameTimeMonitor.Stats stats = MONITOR.stats();

        return String.format(
                "VOID FPS | %d FPS | %.1f ms avg / %.1f ms p99 | 1%% low %.0f | heap %d MB | item %d / orb %d",
                client.getFps(),
                stats.averageFrameTimeMs(),
                stats.p99FrameTimeMs(),
                stats.onePercentLowFps(),
                ram,
                PickupCullingStats.itemEntityStatesSkipped(),
                PickupCullingStats.experienceOrbStatesSkipped());
    }
}
