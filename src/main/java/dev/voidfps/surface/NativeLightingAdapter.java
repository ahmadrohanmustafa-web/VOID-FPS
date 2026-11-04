package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thin adapter over Minecraft 26.3's native block-quad lighting implementation.
 *
 * <p>This class deliberately delegates all lighting work to {@link BlockModelLighter};
 * it does not reproduce ambient occlusion, packed-light sampling, or directional shading.</p>
 *
 * <p>Instances are thread-confined because the native lighter owns mutable scratch state.
 * Use this adapter only during a vanilla section-compilation cache scope: SectionCompiler
 * calls BlockModelLighter.enableCaching() before compiling and clearCache() afterwards.
 * The lighter's constructor obtains that same thread-local cache. Do not enable or clear
 * the shared cache from this adapter.</p>
 *
 * <p>This adapter is not wired into a renderer and does not activate an optimization.</p>
 */
public final class NativeLightingAdapter {
    private final BlockModelLighter lighter = new BlockModelLighter();

    /**
     * Prepares native per-vertex AO and packed-light values for one baked quad.
     */
    public void prepareAmbientOcclusion(
            BlockAndTintGetter world,
            BlockState state,
            BlockPos pos,
            BakedQuad quad,
            QuadInstance instance) {
        lighter.prepareQuadAmbientOcclusion(world, state, pos, quad, instance);
    }

    /**
     * Prepares native flat-shaded colour and packed-light values for one baked quad.
     *
     * @param packedLight packed light supplied by vanilla; pass -1 to request the
     *                    native helper's neighbour-position sampling behavior
     */
    public void prepareFlat(
            BlockAndTintGetter world,
            BlockState state,
            BlockPos pos,
            int packedLight,
            BakedQuad quad,
            QuadInstance instance) {
        lighter.prepareQuadFlat(world, state, pos, packedLight, quad, instance);
    }

    /**
     * Resolves packed light through Minecraft's native lighter cache.
     */
    public int getLightCoords(BlockState state, BlockAndTintGetter world, BlockPos pos) {
        return lighter.getLightCoords(state, world, pos);
    }
}
