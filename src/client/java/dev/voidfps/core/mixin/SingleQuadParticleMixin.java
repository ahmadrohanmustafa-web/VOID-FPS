package dev.voidfps.core.mixin;

import dev.voidfps.core.ParticleCullingStats;
import dev.voidfps.core.VoidFpsClient;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleRenderState;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.VibrationSignalParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void voidfps$cullSubpixelParticle(
            QuadParticleRenderState renderState,
            Camera camera,
            float partialTickTime,
            CallbackInfo ci
    ) {
        if (!VoidFpsClient.shouldCullTinyParticles()) return;

        SingleQuadParticle particle = (SingleQuadParticle) (Object) this;
        // Keep the vibration locator visible even when its rendered quad is tiny.
        if (particle instanceof VibrationSignalParticle) return;

        int screenHeight = VoidFpsClient.cachedScreenHeight();
        if (screenHeight <= 0) return;

        ParticleAccessor position = (ParticleAccessor) (Object) this;
        double x = position.voidfps$getOldX()
                + (position.voidfps$getX() - position.voidfps$getOldX()) * partialTickTime;
        double y = position.voidfps$getOldY()
                + (position.voidfps$getY() - position.voidfps$getOldY()) * partialTickTime;
        double z = position.voidfps$getOldZ()
                + (position.voidfps$getZ() - position.voidfps$getOldZ()) * partialTickTime;

        Vec3 cameraPosition = camera.position();
        double dx = x - cameraPosition.x();
        double dy = y - cameraPosition.y();
        double dz = z - cameraPosition.z();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < 1.0) return;

        double particleSize = Math.abs(particle.getQuadSize(partialTickTime));
        if (particleSize <= 0.0) {
            ParticleCullingStats.recordSkipped();
            ci.cancel();
            return;
        }

        double maximumVisibleDistance = particleSize
                * screenHeight * VoidFpsClient.particleProjectionScale();
        if (distanceSquared > maximumVisibleDistance * maximumVisibleDistance) {
            ParticleCullingStats.recordSkipped();
            ci.cancel();
        }
    }
}
