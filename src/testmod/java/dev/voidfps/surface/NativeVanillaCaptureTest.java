package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only capture of Minecraft 26.3's real prepared BlockQuadOutput boundary.
 * This invokes vanilla ModelBlockRenderer with a live ClientLevel and records
 * the exact ordered packed vertices emitted by its native tessellation path.
 */
final class NativeVanillaCaptureTest {
    private NativeVanillaCaptureTest() {
    }

    static void verifyStone(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.STONE)) {
            throw new IllegalArgumentException("Capture fixture is not stone at " + pos + ": " + state);
        }

        BlockStateModel model = net.minecraft.client.Minecraft.getInstance()
                .getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            throw new IllegalStateException("No resource-loaded model for world stone fixture");
        }

        long seed = state.getSeed(pos);
        Capture first = capture(level, pos, state, model, seed);
        Capture second = capture(level, pos, state, model, seed);

        if (first.vertices().isEmpty() || first.quads().isEmpty()) {
            throw new IllegalStateException("Vanilla ModelBlockRenderer emitted no stone quads");
        }
        NativeVertexParity.Result parity =
                NativeVertexParity.compare(first.vertices(), second.vertices(), 0.0f);
        if (!parity.matches()) {
            throw new IllegalStateException("Repeated vanilla stone captures differ: " + parity.difference());
        }
        if (!first.quads().equals(second.quads())) {
            throw new IllegalStateException("Repeated vanilla stone quad metadata/order differs");
        }
        if (first.vertices().size() != first.quads().size() * 4) {
            throw new IllegalStateException("Vanilla capture did not emit exactly four vertices per quad");
        }

        System.out.println("VOID FPS real vanilla ModelBlockRenderer capture passed: position=" + pos
                + ", quads=" + first.quads().size()
                + ", vertices=" + first.vertices().size()
                + ", repeatedCaptureExact=true");
    }

    private static Capture capture(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        ModelBlockRenderer renderer = new ModelBlockRenderer(true, true, BlockColors.createDefault());
        NativeVertexRecorder recorder = new NativeVertexRecorder();
        List<QuadSignature> quads = new ArrayList<>();

        BlockQuadOutput output = (x, y, z, quad, instance) -> {
            int firstVertex = recorder.snapshot().size();
            quads.add(new QuadSignature(quad.direction(), quad.materialInfo(), firstVertex));
            recorder.putBlockBakedQuad(x, y, z, quad, instance);
        };

        renderer.tesselateBlock(
                output,
                pos.getX(), pos.getY(), pos.getZ(),
                level, pos, state, model, seed);
        return new Capture(List.copyOf(quads), recorder.snapshot());
    }

    private record QuadSignature(Direction direction, Object material, int firstVertex) {
    }

    private record Capture(List<QuadSignature> quads, List<NativeVertexRecord> vertices) {
    }
}
