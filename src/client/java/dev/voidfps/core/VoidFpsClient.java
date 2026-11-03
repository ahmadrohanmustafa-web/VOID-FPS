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
    private static final ClientTickMonitor TICK_MONITOR = new ClientTickMonitor();
    private static final GarbageCollectionMonitor GC_MONITOR = new GarbageCollectionMonitor();
    private static final AdaptiveCullingController ADAPTIVE_CULLING = new AdaptiveCullingController();
    private static final VoidFpsConfig CONFIG = new VoidFpsConfig();
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping exportKey, hudKey, pickupCullingKey, resetCountersKey, particleCullingKey, adaptiveKey;
    private static int refreshCounter;
    private static int adaptiveTickCounter;
    private static String cachedText = "";
    private static volatile int cachedScreenHeight;
    private static volatile double cachedPickupScreenScale;
    private static volatile double cachedParticleScreenScale;

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        exportKey = register("key.voidfps.export_benchmark", InputConstants.KEY_F9);
        hudKey = register("key.voidfps.toggle_hud", InputConstants.KEY_F10);
        pickupCullingKey = register("key.voidfps.toggle_pickup_culling", InputConstants.KEY_F11);
        resetCountersKey = register("key.voidfps.reset_counters", InputConstants.KEY_F12);
        particleCullingKey = register("key.voidfps.toggle_particle_culling", InputConstants.KEY_F8);
        adaptiveKey = register("key.voidfps.toggle_adaptive_culling", InputConstants.KEY_F7);

        ClientTickEvents.START_CLIENT_TICK.register(client -> TICK_MONITOR.beginTick(System.nanoTime()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            cachedScreenHeight = client.getWindow().getHeight();
            if (++adaptiveTickCounter >= 10) {
                adaptiveTickCounter = 0;
                if (CONFIG.adaptiveCullingEnabled) ADAPTIVE_CULLING.update(MONITOR.lastFrameTimeMs());
                else ADAPTIVE_CULLING.reset();
            }
            double adaptiveScale = CONFIG.adaptiveCullingEnabled ? ADAPTIVE_CULLING.multiplier() : 1.0;
            cachedPickupScreenScale = cachedScreenHeight * CONFIG.pickupProjectionScale() * adaptiveScale;
            cachedParticleScreenScale = cachedScreenHeight * CONFIG.particleProjectionScale() * adaptiveScale;
            GC_MONITOR.poll(System.nanoTime());
            while (exportKey.consumeClick()) {
                BenchmarkExporter.export(MONITOR, TICK_MONITOR);
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
                notifyPlayer(client, "VOID FPS pickup culling: " + (CONFIG.tinyPickupCullingEnabled ? "ON" : "OFF"));
            }
            while (resetCountersKey.consumeClick()) {
                PickupCullingStats.reset();
                ParticleCullingStats.reset();
                notifyPlayer(client, "VOID FPS render counters reset.");
            }
            while (particleCullingKey.consumeClick()) {
                CONFIG.tinyParticleCullingEnabled = !CONFIG.tinyParticleCullingEnabled;
                CONFIG.save();
                notifyPlayer(client, "VOID FPS tiny-particle culling: " + (CONFIG.tinyParticleCullingEnabled ? "ON" : "OFF"));
            }
            while (adaptiveKey.consumeClick()) {
                CONFIG.adaptiveCullingEnabled = !CONFIG.adaptiveCullingEnabled;
                if (!CONFIG.adaptiveCullingEnabled) ADAPTIVE_CULLING.reset();
                CONFIG.save();
                notifyPlayer(client, "VOID FPS adaptive culling: " + (CONFIG.adaptiveCullingEnabled ? "ON" : "OFF"));
            }
            TICK_MONITOR.endTick(System.nanoTime());
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "performance_monitor"),
                (graphics, delta) -> renderHud(Minecraft.getInstance(), graphics));
        CompatibilityManager.logDetectedMods();
        LOGGER.info("VOID FPS Core initialized. F7 adaptive culling; F8 tiny-particle culling; F9 benchmark export; F10 monitor; F11 pickup culling; F12 reset counters.");
    }

    private static KeyMapping register(String key, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(key, code, CATEGORY));
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
        else LOGGER.info(message);
    }

    public static boolean shouldCullItemEntities() { return CONFIG.tinyPickupCullingEnabled && CONFIG.itemEntityCullingEnabled; }
    public static boolean shouldCullExperienceOrbs() { return CONFIG.tinyPickupCullingEnabled && CONFIG.experienceOrbCullingEnabled; }
    public static boolean shouldCullTinyParticles() { return CONFIG.tinyParticleCullingEnabled; }
    public static int cachedScreenHeight() { return cachedScreenHeight; }
    public static double pickupProjectionScale() { return CONFIG.pickupProjectionScale(); }
    public static double particleProjectionScale() { return CONFIG.particleProjectionScale(); }
    public static double pickupScreenScale() { return cachedPickupScreenScale; }
    public static double particleScreenScale() { return cachedParticleScreenScale; }
    public static GarbageCollectionMonitor.Stats garbageCollectionStats() { return GC_MONITOR.stats(); }

    private static void renderHud(Minecraft client, GuiGraphicsExtractor graphics) {
        MONITOR.recordFrame(System.nanoTime());
        if (!CONFIG.hudEnabled || client.level == null || client.player == null) return;
        if (++refreshCounter >= 8) {
            refreshCounter = 0;
            cachedText = buildHudText(client);
        }
        graphics.text(client.font, cachedText, 8, 8, 0xFFE8F2FF);
    }

    private static String buildHudText(Minecraft client) {
        long ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L);
        FrameTimeMonitor.Stats stats = MONITOR.stats();
        ClientTickMonitor.Stats tickStats = TICK_MONITOR.stats();
        GarbageCollectionMonitor.Stats gcStats = GC_MONITOR.stats();
        return String.format("VOID FPS | %d FPS | frame %.1f/%.1f ms | tick %.2f/%.2f ms p95 | 1%% low %.0f | spikes F:%d/%d T:%d/%d | GC %d / %d ms | heap %d MB | pickups %d | particles %d | adaptive %.2fx",
                client.getFps(), stats.averageFrameTimeMs(), stats.p99FrameTimeMs(),
                tickStats.averageMs(), tickStats.p95Ms(), stats.onePercentLowFps(),
                stats.framesOver50Ms(), stats.framesOver100Ms(), tickStats.ticksOver50Ms(), tickStats.ticksOver100Ms(),
                gcStats.collectionsSinceStart(), gcStats.collectionTimeMs(), ram, PickupCullingStats.totalStatesSkipped(),
                ParticleCullingStats.totalStatesSkipped(), CONFIG.adaptiveCullingEnabled ? ADAPTIVE_CULLING.multiplier() : 1.0);
    }
}
