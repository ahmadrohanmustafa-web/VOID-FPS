package dev.voidfps.core.mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelRenderer;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Memoizes repeated face-visibility decisions while one block model is being
 * tessellated. A section compiler reuses ModelBlockRenderer for many blocks,
 * and multipart models can ask the same directional occlusion question for
 * several quads. The cache is thread-local because section compilation runs
 * on worker threads as well as the render thread.
 *
 * This does not invent a new occlusion rule: the first query for each face
 * always executes Minecraft's own shouldRenderFace implementation. Only
 * repeated queries for the same level, state, position, direction and
 * neighbour position reuse that exact vanilla result.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererMixin {
    @Unique
    private static final ThreadLocal<VoidFpsFaceCache> VOIDFPS$FACE_CACHE =
            ThreadLocal.withInitial(VoidFpsFaceCache::new);

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
            at = @At("HEAD")
    )
    private void voidfps$beginBlock(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        VOIDFPS$FACE_CACHE.get().begin(level, pos, state);
    }

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
            at = @At("RETURN")
    )
    private void voidfps$endBlock(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        VOIDFPS$FACE_CACHE.get().end();
    }

    @Inject(
            method = "shouldRenderFace(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void voidfps$reuseFaceDecision(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        VoidFpsFaceCache cache = VOIDFPS$FACE_CACHE.get();
        if (!cache.matches(level, pos, state)) {
            return;
        }

        int face = direction.ordinal();
        int bit = 1 << face;
        if ((cache.knownFaces & bit) != 0
                && cache.neighborPositions[face] == neighborPos.asLong()) {
            cir.setReturnValue((cache.visibleFaces & bit) != 0);
        }
    }

    @Inject(
            method = "shouldRenderFace(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN")
    )
    private void voidfps$rememberFaceDecision(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        VoidFpsFaceCache cache = VOIDFPS$FACE_CACHE.get();
        if (!cache.matches(level, pos, state)) {
            return;
        }

        int face = direction.ordinal();
        int bit = 1 << face;
        cache.knownFaces |= bit;
        cache.neighborPositions[face] = neighborPos.asLong();
        if (cir.getReturnValue()) {
            cache.visibleFaces |= bit;
        } else {
            cache.visibleFaces &= ~bit;
        }
    }

    @Unique
    private static final class VoidFpsFaceCache {
        private final long[] neighborPositions = new long[6];
        private BlockAndTintGetter level;
        private BlockState state;
        private long position;
        private int knownFaces;
        private int visibleFaces;
        private boolean active;

        private void begin(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            this.level = level;
            this.state = state;
            this.position = pos.asLong();
            this.knownFaces = 0;
            this.visibleFaces = 0;
            this.active = true;
        }

        private boolean matches(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            return this.active
                    && this.level == level
                    && this.state == state
                    && this.position == pos.asLong();
        }

        private void end() {
            this.active = false;
            this.level = null;
            this.state = null;
            this.knownFaces = 0;
            this.visibleFaces = 0;
        }
    }
}
