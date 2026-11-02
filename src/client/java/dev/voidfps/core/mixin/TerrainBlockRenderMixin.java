package dev.voidfps.core.mixin;

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VOID terrain-geometry suppression.
 *
 * Stops the final client terrain draw submission for each chunk-section layer.
 * It does not remove or modify blocks in ClientLevel, chunk palettes, block
 * states, collision shapes, interaction/raycast data, light data, block entity
 * data, packets, or server state. Those systems continue to run normally.
 *
 * This is deliberately placed at the final terrain submission boundary rather
 * than in world storage or block-state lookup. It also avoids creating the
 * per-layer RenderPass and issuing its GPU draw calls.
 *
 * NOTE: section meshes are still compiled and uploaded by vanilla in this
 * first safe rendering-path implementation. A separate compiler-stage
 * optimization must preserve visibility data and block-entity discovery
 * before it can safely skip meshing.
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class TerrainBlockRenderMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true, require = 1)
    private void voidfps$skipClientTerrainGeometry(CallbackInfo ci) {
        ci.cancel();
    }
}
