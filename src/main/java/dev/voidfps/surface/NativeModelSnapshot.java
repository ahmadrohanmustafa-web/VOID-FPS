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
        private static final float GEOMETRY_EPSILON = 1.0e-4f;

        public boolean isEmpty() {
            return parts.isEmpty();
        }

        /**
         * Conservative geometry-only precheck for a single full-cube surface.
         *
         * <p>This is deliberately NOT a complete render-eligibility decision: it says nothing
         * about opacity, render layer, tint, block entities, lighting, or material semantics.
         * Callers must perform those checks separately before considering any custom renderer.</p>
         *
         * <p>Every selected part is inspected. The combined model must contain exactly one
         * boundary quad for each of the six directions and no unculled quads. Quad order and
         * vertex order are left untouched.</p>
         */
        public boolean hasExactlyOneUnitBoundaryQuadPerFace() {
            int[] faceCounts = new int[Direction.values().length];

            for (Part part : parts) {
                if (!part.unculledQuads().isEmpty()) return false;

                for (FaceQuads faceQuads : part.directionalQuads()) {
                    Direction direction = faceQuads.direction();
                    for (BakedQuad quad : faceQuads.quads()) {
                        if (!isUnitBoundaryQuad(quad, direction)) return false;
                        if (++faceCounts[direction.ordinal()] != 1) return false;
                    }
                }
            }

            for (int count : faceCounts) {
                if (count != 1) return false;
            }
            return true;
        }

        private static boolean isUnitBoundaryQuad(BakedQuad quad, Direction direction) {
            int dx = direction.getStepX();
            int dy = direction.getStepY();
            int dz = direction.getStepZ();
            int axis = dx != 0 ? 0 : dy != 0 ? 1 : 2;
            int tangentA = axis == 2 ? 0 : axis == 1 ? 0 : 2;
            int tangentB = axis == 1 ? 2 : 1;
            float plane = (dx + dy + dz) > 0 ? 1.0f : 0.0f;
            boolean[] corners = new boolean[4];

            for (int vertex = 0; vertex < 4; vertex++) {
                var position = quad.position(vertex);
                float[] coordinates = {position.x(), position.y(), position.z()};
                float normal = coordinates[axis];
                float a = coordinates[tangentA];
                float b = coordinates[tangentB];

                if (!Float.isFinite(normal) || !Float.isFinite(a) || !Float.isFinite(b)) {
                    return false;
                }
                if (Math.abs(normal - plane) > GEOMETRY_EPSILON
                        || !nearUnitCoordinate(a) || !nearUnitCoordinate(b)) {
                    return false;
                }

                int corner = (a > 0.5f ? 1 : 0) | (b > 0.5f ? 2 : 0);
                if (corners[corner]) return false;
                corners[corner] = true;
            }

            return true;
        }

        private static boolean nearUnitCoordinate(float value) {
            return Math.abs(value) <= GEOMETRY_EPSILON
                    || Math.abs(value - 1.0f) <= GEOMETRY_EPSILON;
        }

        /**
         * Checks the native per-quad material metadata for the narrow first milestone:
         * solid chunk layer, no tint, no per-quad light emission, no animated texture,
         * and no shade-direction override. This does not prove block opacity or block
         * emission; those remain separate state/world checks.
         */
        public boolean hasOnlyPlainSolidUntintedMaterials() {
            if (parts.isEmpty()) return false;

            for (Part part : parts) {
                for (FaceQuads face : part.directionalQuads()) {
                    for (BakedQuad quad : face.quads()) {
                        if (!hasPlainSolidMaterial(quad)) return false;
                    }
                }
                for (BakedQuad quad : part.unculledQuads()) {
                    if (!hasPlainSolidMaterial(quad)) return false;
                }
            }
            return true;
        }

        private static boolean hasPlainSolidMaterial(BakedQuad quad) {
            var material = quad.materialInfo();
            return material.layer() == net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID
                    && material.tintIndex() < 0
                    && material.lightEmission() == 0
                    && material.shadeDirectionOverride() == null
                    && (material.flags() & BakedQuad.FLAG_ANIMATED) == 0;
        }
    }
}
