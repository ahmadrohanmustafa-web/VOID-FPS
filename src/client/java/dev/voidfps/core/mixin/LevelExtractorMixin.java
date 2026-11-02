package dev.voidfps.core.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Avoids building render state for tiny, distant dropped pickups that would
 * occupy less than a fraction of a physical screen pixel. Entities remain
 * loaded and simulated; only their imperceptible pickup rendering is skipped.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    private static final double MIN_PROJECTED_PIXELS = 0.75;
    private static final double PROJECTION_SCALE =
            1.0 / (2.0 * MIN_PROJECTED_PIXELS * Math.tan(Math.toRadians(35.0)));

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void voidfps$cullSubpixelPickups(
            Entity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTicks,
            long chunkFadeDuration,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) return;
        if (entity.hasCustomName()) return;

        double distanceSquared = entity.distanceToSqr(cameraX, cameraY, cameraZ);
        if (distanceSquared < 1.0) return;

        int screenHeight = Minecraft.getInstance().getWindow().getHeight();
        if (screenHeight <= 0) return;

        double worldSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        double maxVisibleDistance = worldSize * screenHeight * PROJECTION_SCALE;

        // Squared comparison avoids a square-root operation for every pickup.
        if (distanceSquared > maxVisibleDistance * maxVisibleDistance) {
            cir.setReturnValue(false);
        }
    }
}
