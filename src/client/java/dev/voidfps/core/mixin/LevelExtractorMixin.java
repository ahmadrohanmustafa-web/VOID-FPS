package dev.voidfps.core.mixin;

import dev.voidfps.core.PickupCullingStats;
import dev.voidfps.core.VoidFpsClient;
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
 * Avoids extracting render state for ordinary dropped pickups that project
 * below the configured subpixel threshold. The entities remain loaded and
 * simulated; named pickups are always preserved.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
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

        if (entity instanceof ItemEntity && !VoidFpsClient.shouldCullItemEntities()) return;
        if (entity instanceof ExperienceOrb && !VoidFpsClient.shouldCullExperienceOrbs()) return;

        int screenHeight = VoidFpsClient.cachedScreenHeight();
        if (screenHeight <= 0) return;

        double distanceSquared = entity.distanceToSqr(cameraX, cameraY, cameraZ);
        if (distanceSquared < 1.0) return;

        double worldSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        double maxVisibleDistance = worldSize * screenHeight * VoidFpsClient.pickupProjectionScale();

        // Squared comparison avoids a square-root operation for each candidate.
        if (distanceSquared > maxVisibleDistance * maxVisibleDistance) {
            if (entity instanceof ItemEntity) {
                PickupCullingStats.recordItemEntitySkipped();
            } else {
                PickupCullingStats.recordExperienceOrbSkipped();
            }
            cir.setReturnValue(false);
        }
    }
}
