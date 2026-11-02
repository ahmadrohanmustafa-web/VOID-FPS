package dev.voidfps.core.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps expensive chunk-section mesh compilation off the render thread.
 *
 * Vanilla's synchronous path is selected by the "Prioritize Chunk Updates"
 * option for nearby/player-modified sections. The async path uses the same
 * immutable section region snapshot and the dispatcher's existing worker
 * queue, so mesh contents and graphics quality are unchanged; only the
 * scheduling thread changes. This avoids doing full section meshing inside
 * a frame and reduces the chance of chunk-update frame stalls.
 */
@Mixin(LevelRenderer.class)
public abstract class ChunkCompileSchedulingMixin {
    @Redirect(
            method = "compileSections(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V"
            ),
            require = 1
    )
    private void voidfps$compileChunkSectionAsynchronously(
            SectionRenderDispatcher.RenderSection section,
            RenderSectionRegion region
    ) {
        section.compileAsync(region);
    }
}
