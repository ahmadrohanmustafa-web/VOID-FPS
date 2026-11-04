package dev.voidfps.surface;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * Lossless, read-only snapshot of the parts selected by a native Minecraft block-state model.
 *
 * <p>The caller must supply the same seeded RandomSource that vanilla would use for this
 * block render. This adapter deliberately does not flatten quads into the older engine-neutral
 * data format: doing so would discard material information and potentially UV/light semantics.
 * It also does not render anything or replace vanilla.</p>
 */
public final class NativeModelSnapshot {
    private NativeModelSnapshot() {
    }

    /**
     * Collects the model parts using Minecraft's own variant-selection logic and copies every
     * directional and unculled quad list without changing its order or quad objects.
     */
    public static Snapshot collect(BlockStateModel model, RandomSource random) {
        List<BlockStateModelPart> nativeParts = new ArrayList<>();
        model.collectParts(random, nativeParts);

        List<Part> parts = new ArrayList<>(nativeParts.size());
        for (BlockStateModelPart part : nativeParts) {
            List<FaceQuads> faces = new ArrayList<>(Direction.values().length);
            for (Direction direction : Direction.values()) {
                faces.add(new FaceQuads(direction, List.copyOf(part.getQuads(direction))));
            }

            List<BakedQuad> unculled = List.copyOf(part.getQuads(null));
            parts.add(new Part(part.useAmbientOcclusion(), part.materialFlags(),
                    List.copyOf(faces), unculled));
        }

        return new Snapshot(List.copyOf(parts));
    }

    /** One selected native model part. */
    public record Part(
            boolean ambientOcclusion,
            int materialFlags,
            List<FaceQuads> directionalQuads,
            List<BakedQuad> unculledQuads) {
    }

    /** Quads whose native cull direction is the associated direction. */
    public record FaceQuads(Direction direction, List<BakedQuad> quads) {
    }

    /** Immutable list of all parts selected for one block-state model invocation. */
    public record Snapshot(List<Part> parts) {
        public boolean isEmpty() {
            return parts.isEmpty();
        }
    }
}
