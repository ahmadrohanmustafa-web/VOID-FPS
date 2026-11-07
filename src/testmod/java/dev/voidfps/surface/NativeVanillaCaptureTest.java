package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelLighter;
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

        verifyCandidateFaceSelection(level, pos, state, model, seed, first.quads());
        verifyNativeWorldFaceCulling(level, pos, state, model, seed);
        verifyAdditionalFullCubeModels(level, pos);
    }

    /**
     * Extends native candidate parity beyond stone to several ordinary full-cube
     * states. Fixtures and their six neighbours are restored even when an assertion fails.
     */
    private static void verifyAdditionalFullCubeModels(ClientLevel level, BlockPos origin) {
        BlockState[] states = {
                Blocks.DIRT.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.BRICKS.defaultBlockState()
        };

        for (int index = 0; index < states.length; index++) {
            BlockPos fixture = origin.above(3 + index * 2);
            BlockState state = states[index];
            java.util.Map<BlockPos, BlockState> previousStates = new java.util.LinkedHashMap<>();
            previousStates.put(fixture, level.getBlockState(fixture));
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = fixture.relative(direction);
                previousStates.putIfAbsent(neighbour, level.getBlockState(neighbour));
            }

            try {
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = fixture.relative(direction);
                    level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), 3);
                    if (!level.getBlockState(neighbour).isAir()) {
                        throw new IllegalStateException(
                                "Could not clear additional-model fixture neighbour " + neighbour);
                    }
                }
                level.setBlock(fixture, state, 3);
                if (!level.getBlockState(fixture).equals(state)) {
                    throw new IllegalStateException(
                            "Could not place additional full-cube fixture " + state + " at " + fixture);
                }

                BlockStateModel model = net.minecraft.client.Minecraft.getInstance()
                        .getModelManager().getBlockStateModelSet().get(state);
                if (model == null) {
                    throw new IllegalStateException("No resource-loaded model for " + state);
                }
                long seed = state.getSeed(fixture);
                NativeModelSnapshot.Snapshot snapshot =
                        NativeModelSnapshot.collect(model, net.minecraft.util.RandomSource.create(seed));
                SurfaceEligibility.NativeGeometryAnalysis eligibility =
                        SurfaceEligibility.analyzeNativeModel(state, snapshot);
                if (!eligibility.supported()) {
                    throw new IllegalStateException(
                            "Expected ordinary full-cube fixture to pass native eligibility: "
                                    + state + " rejected because " + eligibility.rejectReason());
                }

                Capture vanilla = capture(level, fixture, state, model, seed);
                if (vanilla.quads().size() != 6 || vanilla.vertices().size() != 24) {
                    throw new IllegalStateException(
                            "Unexpected vanilla full-cube output for " + state
                                    + ": quads=" + vanilla.quads().size()
                                    + ", vertices=" + vanilla.vertices().size());
                }
                verifyCandidateFaceSelection(level, fixture, state, model, seed, vanilla.quads());
                verifyNativeWorldFaceCulling(level, fixture, state, model, seed);
                System.out.println("VOID FPS additional full-cube native parity passed: block="
                        + state.getBlock() + ", quads=6, vertices=24, eligibility=accepted");
            } finally {
                for (var entry : previousStates.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                    if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                        throw new IllegalStateException(
                                "Could not restore additional-model fixture block at " + entry.getKey());
                    }
                }
            }
        }
    }

    /**
     * Test-only VOID candidate face selector. It independently walks the selected native
     * model's directional quads, applies Minecraft's face-visibility predicate to live
     * neighbours, and compares the ordered native quad identities/materials with the
     * actual ModelBlockRenderer output.
     */
    private static void verifyCandidateFaceSelection(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            List<QuadSignature> isolatedVanilla) {
        List<QuadSignature> isolatedCandidate =
                candidateVisibleQuads(level, pos, state, model, seed);
        assertCandidateMatchesVanilla("isolated", isolatedVanilla, isolatedCandidate);
        verifyCandidateVertexOutput(level, pos, state, model, seed, "isolated");

        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState previous = level.getBlockState(neighbourPos);
            try {
                if (!level.setBlock(neighbourPos, Blocks.STONE.defaultBlockState(), 3)
                        || !level.getBlockState(neighbourPos).is(Blocks.STONE)) {
                    throw new IllegalStateException(
                            "Could not place candidate selector neighbour on " + direction);
                }

                Capture vanilla = capture(level, pos, state, model, seed);
                List<QuadSignature> candidate =
                        candidateVisibleQuads(level, pos, state, model, seed);
                assertCandidateMatchesVanilla(direction.name(), vanilla.quads(), candidate);
                verifyCandidateVertexOutput(level, pos, state, model, seed, direction.name());
            } finally {
                if (!level.setBlock(neighbourPos, previous, 3)
                        || !level.getBlockState(neighbourPos).equals(previous)) {
                    throw new IllegalStateException(
                            "Could not restore candidate selector neighbour at " + neighbourPos);
                }
            }
        }
    }

    private static List<QuadSignature> candidateVisibleQuads(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        NativeModelSnapshot.Snapshot snapshot =
                NativeModelSnapshot.collect(model, net.minecraft.util.RandomSource.create(seed));
        List<QuadSignature> candidate = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (!net.minecraft.world.level.block.Block.shouldRenderFace(state, neighbour, direction)) {
                continue;
            }
            for (NativeModelSnapshot.Part part : snapshot.parts()) {
                for (NativeModelSnapshot.FaceQuads face : part.directionalQuads()) {
                    if (face.direction() != direction) continue;
                    for (var quad : face.quads()) {
                        candidate.add(new QuadSignature(
                                quad.direction(), quad.materialInfo(), candidate.size() * 4));
                    }
                }
            }
        }
        return List.copyOf(candidate);
    }

    private static void assertCandidateMatchesVanilla(
            String scenario,
            List<QuadSignature> vanilla,
            List<QuadSignature> candidate) {
        if (!vanilla.equals(candidate)) {
            throw new IllegalStateException(
                    "VOID candidate face selection differs from vanilla for " + scenario
                            + ": vanilla=" + vanilla + ", candidate=" + candidate);
        }
        System.out.println("VOID FPS candidate face-selection parity passed: scenario="
                + scenario + ", quads=" + candidate.size());
    }

    /**
     * Exercises Minecraft's real world-neighbour culling through ModelBlockRenderer.
     * Each temporary adjacent stone must hide exactly the matching face of the fixture.
     */
    private static void verifyNativeWorldFaceCulling(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState previous = level.getBlockState(neighbourPos);
            try {
                if (!level.setBlock(neighbourPos, Blocks.STONE.defaultBlockState(), 3)
                        || !level.getBlockState(neighbourPos).is(Blocks.STONE)) {
                    throw new IllegalStateException(
                            "Could not place temporary neighbour for native culling test on " + direction);
                }

                Capture withNeighbour = capture(level, pos, state, model, seed);
                boolean hiddenFaceStillPresent = withNeighbour.quads().stream()
                        .anyMatch(quad -> quad.direction() == direction);
                if (withNeighbour.quads().size() != 5 || hiddenFaceStillPresent) {
                    throw new IllegalStateException(
                            "Native world culling mismatch on " + direction
                                    + ": quads=" + withNeighbour.quads().size()
                                    + ", hiddenFaceStillPresent=" + hiddenFaceStillPresent);
                }
                System.out.println("VOID FPS real-world native face culling passed: covered="
                        + direction + ", emittedQuads=5");
            } finally {
                if (!level.setBlock(neighbourPos, previous, 3)
                        || !level.getBlockState(neighbourPos).equals(previous)) {
                    throw new IllegalStateException(
                            "Could not restore temporary neighbour after culling test at " + neighbourPos);
                }
            }
        }
    }

    /**
     * Runs the independent candidate emitter against vanilla inside one native lighter-cache
     * scope. Both paths receive the same live ClientLevel, state, position, selected model,
     * seed, and thread-local BlockModelLighter cache. The current eligible set excludes tint,
     * offset functions, unculled quads, and non-solid/emissive materials.
     */
    private static void verifyCandidateVertexOutput(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            String scenario) {
        BlockModelLighter.enableCaching();
        try {
            Capture vanilla = capture(level, pos, state, model, seed);
            Capture candidate = captureCandidate(level, pos, state, model, seed);
            NativeVertexParity.Result parity =
                    NativeVertexParity.compare(vanilla.vertices(), candidate.vertices(), 0.0f);
            if (!parity.matches()) {
                throw new IllegalStateException(
                        "VOID candidate vertex-output parity differs from vanilla for " + scenario
                                + ": " + parity.difference());
            }
            if (!vanilla.quads().equals(candidate.quads())) {
                throw new IllegalStateException(
                        "VOID candidate quad metadata/order differs from vanilla for " + scenario
                                + ": vanilla=" + vanilla.quads() + ", candidate=" + candidate.quads());
            }
            System.out.println("VOID FPS candidate native vertex-output parity passed: scenario="
                    + scenario + ", vertices=" + parity.comparedVertices()
                    + ", exact=true, cacheScope=shared");
        } finally {
            BlockModelLighter.clearCache();
        }
    }

    /**
     * Independent test-only candidate path. It selects the same seeded native model parts,
     * performs the narrow full-cube face test, asks Minecraft's BlockModelLighter to prepare
     * AO or flat data, and forwards the original baked quads to the native packed-vertex path.
     */
    private static Capture captureCandidate(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        NativeModelSnapshot.Snapshot snapshot =
                NativeModelSnapshot.collect(model, net.minecraft.util.RandomSource.create(seed));
        SurfaceEligibility.NativeGeometryAnalysis eligibility =
                SurfaceEligibility.analyzeNativeModel(state, snapshot);
        if (!eligibility.supported()) {
            throw new IllegalStateException(
                    "Candidate emitter received an ineligible model: " + eligibility.rejectReason());
        }
        if (snapshot.parts().isEmpty()) {
            throw new IllegalStateException("Candidate emitter received no selected model parts");
        }

        boolean useAmbientOcclusion =
                state.getLightEmission() == 0 && snapshot.parts().getFirst().ambientOcclusion();
        var offset = state.getOffset(pos);
        float x = pos.getX() + (float) offset.x;
        float y = pos.getY() + (float) offset.y;
        float z = pos.getZ() + (float) offset.z;

        NativeLightingAdapter lighting = new NativeLightingAdapter();
        NativeVertexRecorder recorder = new NativeVertexRecorder();
        List<QuadSignature> quads = new ArrayList<>();
        BlockQuadOutput output = (qx, qy, qz, quad, instance) -> {
            int firstVertex = recorder.snapshot().size();
            quads.add(new QuadSignature(quad.direction(), quad.materialInfo(), firstVertex));
            recorder.putBlockBakedQuad(qx, qy, qz, quad, instance);
        };

        for (NativeModelSnapshot.Part part : snapshot.parts()) {
            for (Direction direction : Direction.values()) {
                List<net.minecraft.client.resources.model.geometry.BakedQuad> faceQuads =
                        part.directionalQuads().stream()
                                .filter(face -> face.direction() == direction)
                                .findFirst()
                                .map(NativeModelSnapshot.FaceQuads::quads)
                                .orElse(List.of());
                if (faceQuads.isEmpty()) continue;

                BlockPos neighbourPos = pos.relative(direction);
                BlockState neighbour = level.getBlockState(neighbourPos);
                if (!net.minecraft.world.level.block.Block.shouldRenderFace(state, neighbour, direction)) {
                    continue;
                }

                int packedLight = useAmbientOcclusion
                        ? 0
                        : lighting.getLightCoords(state, level, neighbourPos);
                for (var quad : faceQuads) {
                    QuadInstance instance = new QuadInstance();
                    if (useAmbientOcclusion) {
                        lighting.prepareAmbientOcclusion(level, state, pos, quad, instance);
                    } else {
                        lighting.prepareFlat(level, state, pos, packedLight, quad, instance);
                    }
                    output.put(x, y, z, quad, instance);
                }
            }
        }

        if (recorder.snapshot().size() != quads.size() * 4) {
            throw new IllegalStateException("Candidate emitter produced an incomplete quad vertex stream");
        }
        return new Capture(List.copyOf(quads), recorder.snapshot());
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
