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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            cachedScreenHeight = client.getWindow().getHeight();

            while (exportKey.consumeClick()) {
                BenchmarkExporter.export(MONITOR);
                if (client.player != null) {
                    client.player.sendSystemMessage(
                            Component.literal("VOID FPS: CSV exported to config/voidfps/"));
                }
            }

            while (hudKey.consumeClick()) {
                CONFIG.hudEnabled = !CONFIG.hudEnabled;
                CONFIG.save();
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                            "VOID FPS monitor: " + (CONFIG.hudEnabled ? "ON" : "OFF")));
                }
            }
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "performance_monitor"),
                (graphics, delta) -> renderHud(Minecraft.getInstance(), graphics));

        CompatibilityManager.logDetectedMods();
        LOGGER.info("VOID FPS Core initialized. Graphics settings are left unchanged. F9 exports frame-time data; F10 toggles the monitor.");
    }

    public static boolean shouldCullTinyPickups() {
        return CONFIG.tinyPickupCullingEnabled;
    }

    public static int cachedScreenHeight() {
        return cachedScreenHeight;
    }

    public static double pickupProjectionScale() {
        return CONFIG.pickupProjectionScale();
    }

    private static void renderHud(Minecraft client, GuiGraphicsExtractor graphics) {
        // Keep benchmark sampling active even when the optional HUD is hidden.
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

        return String.format(
                "VOID FPS | %d FPS | %.1f ms avg / %.1f ms p99 | heap %d MB | RD %d / SD %d",
                client.getFps(),
                MONITOR.averageFrameTimeMs(),
                MONITOR.percentileFrameTimeMs(0.99),
                ram,
                client.options.renderDistance().get(),
                client.options.simulationDistance().get());
    }
}
