package dev.voidfps.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ProfileController {
    public enum Profile {
        VANILLA("Vanilla settings"), GAMING("Gaming"), EXTREME("Extreme");
        private final String displayName;
        Profile(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    private Profile current = Profile.VANILLA;
    private Integer originalRenderDistance;
    private Integer originalSimulationDistance;

    public Profile currentProfile() { return current; }

    public void cycle(Minecraft client) {
        if (client.options == null) return;
        if (originalRenderDistance == null) {
            originalRenderDistance = client.options.renderDistance().get();
            originalSimulationDistance = client.options.simulationDistance().get();
        }
        current = switch (current) {
            case VANILLA -> Profile.GAMING;
            case GAMING -> Profile.EXTREME;
            case EXTREME -> Profile.VANILLA;
        };
        switch (current) {
            case GAMING -> {
                client.options.renderDistance().set(Math.min(originalRenderDistance, 10));
                client.options.simulationDistance().set(Math.min(originalSimulationDistance, 6));
            }
            case EXTREME -> {
                client.options.renderDistance().set(Math.min(originalRenderDistance, 6));
                client.options.simulationDistance().set(Math.min(originalSimulationDistance, 4));
            }
            case VANILLA -> {
                client.options.renderDistance().set(originalRenderDistance);
                client.options.simulationDistance().set(originalSimulationDistance);
            }
        }
        client.options.save();
        if (client.player != null) client.player.displayClientMessage(Component.literal(
                "VOID FPS profile: " + current.displayName() + " (render "
                        + client.options.renderDistance().get() + ", simulation "
                        + client.options.simulationDistance().get() + ")"), true);
        VoidFpsClient.LOGGER.info("Applied {} profile.", current.displayName());
    }
}
