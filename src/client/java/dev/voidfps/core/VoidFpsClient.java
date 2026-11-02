package dev.voidfps.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoidFpsClient implements ClientModInitializer {
    public static final String MOD_ID = "voidfps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final FrameTimeMonitor FRAME_MONITOR = new FrameTimeMonitor();
    private static final VoidFpsConfig CONFIG = new VoidFpsConfig();
    private static final ProfileController PROFILES = new ProfileController();
    private static KeyMapping cycleProfileKey;
    private static KeyMapping exportKey;
    private static KeyMapping toggleHudKey;
    private static int hudRefreshCounter;
    private static String cachedHudText = "";

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        cycleProfileKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voidfps.cycle_profile", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC));
        exportKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voidfps.export_benchmark", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, KeyMapping.Category.MISC));
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voidfps.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cycleProfileKey.consumeClick()) PROFILES.cycle(client);
            while (exportKey.consumeClick()) {
                BenchmarkExporter.export(FRAME_MONITOR);
                if (client.player != null) client.player.displayClientMessage(
                        Component.literal("VOID FPS: benchmark CSV exported to config/voidfps/"), true);
            }
            while (toggleHudKey.consumeClick()) {
                CONFIG.hudEnabled = !CONFIG.hudEnabled;
                CONFIG.save();
                if (client.player != null) client.player.displayClientMessage(
                        Component.literal("VOID FPS monitor: " + (CONFIG.hudEnabled ? "ON" : "OFF")), true);
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "performance_monitor"),
                (graphics, deltaTracker) -> renderHud(Minecraft.getInstance(), graphics));

        CompatibilityManager.logDetectedMods();
        LOGGER.info("VOID FPS Core initialized. F8 profiles, F9 CSV export, F10 monitor.");
    }

    private static void renderHud(Minecraft client, GuiGraphics graphics) {
        if (!CONFIG.hudEnabled || client.level == null || client.player == null) return;
        FRAME_MONITOR.recordFrame(System.nanoTime());
        if (++hudRefreshCounter >= 8) {
            hudRefreshCounter = 0;
            cachedHudText = buildHudText(client);
        }
        graphics.drawString(client.font, cachedHudText, 8, 8, 0xFFE8F2FF, true);
    }

    private static String buildHudText(Minecraft client) {
        long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L);
        return String.format("VOID FPS | %s | %d FPS | %.1f ms avg / %.1f ms p99 | RAM %d MB | RD %d / SD %d",
                PROFILES.currentProfile().displayName(), client.getFps(),
                FRAME_MONITOR.averageFrameTimeMs(), FRAME_MONITOR.percentileFrameTimeMs(0.99),
                usedMb, client.options.renderDistance().get(), client.options.simulationDistance().get());
    }
}
