package dev.voidfps.core;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

public final class ProfileController {
    public enum Profile {
        VANILLA("Vanilla settings"), GAMING("Gaming"), EXTREME("Extreme");
        private final String label;
        Profile(String label) { this.label = label; }
        public String displayName() { return label; }
    }

    private Profile current = Profile.VANILLA;
    private Integer baseRender, baseSimulation, baseBiomeBlend;
    private Double baseEntityDistance;
    private CloudStatus baseCloudStatus;
    private ParticleStatus baseParticles;
    private Boolean baseEntityShadows, baseAmbientOcclusion;

    public Profile currentProfile() { return current; }

    public void cycle(Minecraft client) {
        if (client.options == null) return;
        captureOriginalSettings(client);
        current = switch (current) {
            case VANILLA -> Profile.GAMING;
            case GAMING -> Profile.EXTREME;
            case EXTREME -> Profile.VANILLA;
        };
        switch (current) {
            case GAMING -> applyGaming(client);
            case EXTREME -> applyExtreme(client);
            case VANILLA -> restoreOriginal(client);
        }
        client.options.save();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "VOID FPS: " + current.displayName()
                    + " | render " + client.options.renderDistance().get()
                    + " | simulation " + client.options.simulationDistance().get()
                    + " | particles " + client.options.particles().get()
                    + " | clouds " + client.options.cloudStatus().get()));
        }
        VoidFpsClient.LOGGER.info("Applied {} profile.", current.displayName());
    }

    private void captureOriginalSettings(Minecraft client) {
        if (baseRender != null) return;
        baseRender = client.options.renderDistance().get();
        baseSimulation = client.options.simulationDistance().get();
        baseBiomeBlend = client.options.biomeBlendRadius().get();
        baseEntityDistance = client.options.entityDistanceScaling().get();
        baseCloudStatus = client.options.cloudStatus().get();
        baseParticles = client.options.particles().get();
        baseEntityShadows = client.options.entityShadows().get();
        baseAmbientOcclusion = client.options.ambientOcclusion().get();
    }

    private void applyGaming(Minecraft client) {
        client.options.renderDistance().set(Math.min(baseRender, 10));
        client.options.simulationDistance().set(Math.min(baseSimulation, 6));
        client.options.biomeBlendRadius().set(Math.min(baseBiomeBlend, 1));
        client.options.entityDistanceScaling().set(Math.min(baseEntityDistance, 0.85));
        client.options.cloudStatus().set(baseCloudStatus == CloudStatus.OFF ? CloudStatus.OFF : CloudStatus.FAST);
        client.options.particles().set(baseParticles == ParticleStatus.ALL ? ParticleStatus.DECREASED : baseParticles);
        client.options.entityShadows().set(baseEntityShadows);
        client.options.ambientOcclusion().set(baseAmbientOcclusion);
    }

    private void applyExtreme(Minecraft client) {
        client.options.renderDistance().set(Math.min(baseRender, 6));
        client.options.simulationDistance().set(Math.min(baseSimulation, 4));
        client.options.biomeBlendRadius().set(0);
        client.options.entityDistanceScaling().set(Math.min(baseEntityDistance, 0.65));
        client.options.cloudStatus().set(CloudStatus.OFF);
        client.options.particles().set(ParticleStatus.MINIMAL);
        client.options.entityShadows().set(false);
        client.options.ambientOcclusion().set(false);
    }

    private void restoreOriginal(Minecraft client) {
        client.options.renderDistance().set(baseRender);
        client.options.simulationDistance().set(baseSimulation);
        client.options.biomeBlendRadius().set(baseBiomeBlend);
        client.options.entityDistanceScaling().set(baseEntityDistance);
        client.options.cloudStatus().set(baseCloudStatus);
        client.options.particles().set(baseParticles);
        client.options.entityShadows().set(baseEntityShadows);
        client.options.ambientOcclusion().set(baseAmbientOcclusion);
    }
}