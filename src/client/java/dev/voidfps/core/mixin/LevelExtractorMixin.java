package dev.voidfps.core.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Avoids extracting render state for tiny dropped items and XP orbs once
 * their projected size falls below a pixel. This does not remove entities,
 * change simulation, or alter any video setting.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    private static final double MIN_PROJECTED_PIXELS = 0.75;
    private static final double HALF_FOV_TANGENT = Math.tan(Math.toRadians(35.0));

    @Inject(
            method = "isEntityVisible",
            at = @At("HEAD"),
            cancellable = true
    )
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
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            return;
        }

        if (entity.hasCustomName()) {
            return;
        }

        double distanceSquared = entity.distanceToSqr(cameraX, cameraY, cameraZ);
        if (distanceSquared < 1.0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int screenHeight = client.getWindow().getHeight();
        if (screenHeight <= 0) {
            return;
        }

        double worldSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        double projectedPixels = worldSize * screenHeight
                / (2.0 * Math.sqrt(distanceSquared) * HALF_FOV_TANGENT);

        if (projectedPixels < MIN_PROJECTED_PIXELS) {
            cir.setReturnValue(false);
        }
    }
}
