package dev.voidfps.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Optional frame-time based render-distance controller. */
public final class AutoRenderDistanceController {
    private static final int MIN_DISTANCE = 4;
    private static final double SLOW_FRAME_MS = 33.3;
    private static final double FAST_FRAME_MS = 23.5;
    private static final int CHANGE_COOLDOWN_TICKS = 200;

    private boolean enabled;
    private int originalDistance;
    private int ticksUntilChange;
    private int slowSamples;
    private int fastSamples;

    public void toggle(Minecraft client) {
        if (enabled) {
            enabled = false;
            slowSamples = 0;
            fastSamples = 0;
            ticksUntilChange = 0;
            if (client.options.renderDistance().get() != originalDistance) {
                client.options.renderDistance().set(originalDistance);
                client.options.save();
            }
            notifyPlayer(client, "VOID FPS Auto Distance: OFF (restored " + originalDistance + ")");
            return;
        }
        originalDistance = client.options.renderDistance().get();
        enabled = true;
        ticksUntilChange = 0;
        slowSamples = 0;
        fastSamples = 0;
        notifyPlayer(client, "VOID FPS Auto Distance: ON (range " +
                Math.max(MIN_DISTANCE, originalDistance - 4) + "-" + originalDistance + ")");
    }

    public void update(Minecraft client, double averageFrameTimeMs) {
        if (!enabled || client.level == null || client.player == null) return;
        if (ticksUntilChange > 0) {
            ticksUntilChange -= 40;
            return;
        }

        int current = client.options.renderDistance().get();
        int minimum = Math.min(originalDistance, MIN_DISTANCE);
        if (averageFrameTimeMs >= SLOW_FRAME_MS) {
            slowSamples++;
            fastSamples = 0;
            if (slowSamples >= 3 && current > minimum) {
                applyDistance(client, current - 1);
                slowSamples = 0;
            }
        } else if (averageFrameTimeMs > 0.0 && averageFrameTimeMs <= FAST_FRAME_MS) {
            fastSamples++;
            slowSamples = 0;
            if (fastSamples >= 5 && current < originalDistance) {
                applyDistance(client, current + 1);
                fastSamples = 0;
            }
        } else {
            slowSamples = 0;
            fastSamples = 0;
        }
    }

    private void applyDistance(Minecraft client, int distance) {
        client.options.renderDistance().set(distance);
        client.options.save();
        ticksUntilChange = CHANGE_COOLDOWN_TICKS;
        notifyPlayer(client, "VOID FPS Auto Distance: " + distance + " chunks");
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
        else VoidFpsClient.LOGGER.info(message);
    }
}