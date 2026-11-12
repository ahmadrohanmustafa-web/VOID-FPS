package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.core.SectionPos;
import net.minecraft.client.Minecraft;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
        verifyRendererFlagParity(level, pos, state, model, seed);
        verifyNativeWorldFaceCulling(level, pos, state, model, seed);
        verifyAdditionalFullCubeModels(level, pos);
        verifySectionRegionVertexParity(level, pos, state, model, seed);
    }

    /**
     * Extends native candidate parity beyond stone to several ordinary full-cube
     * states. Fixtures and their six neighbours are restored even when an assertion fails.
     */
    private static void verifyAdditionalFullCubeModels(ClientLevel level, BlockPos origin) {
        BlockState[] states = {
                Blocks.DIRT.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.BRICKS.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.SANDSTONE.defaultBlockState(),
                Blocks.OBSIDIAN.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(),
                Blocks.QUARTZ_BLOCK.defaultBlockState(),
                Blocks.MOSS_BLOCK.defaultBlockState(),
                // Exercise directional cube textures and multi-material face layouts.
                Blocks.HAY_BLOCK.defaultBlockState(),
                Blocks.PUMPKIN.defaultBlockState(),
                Blocks.BOOKSHELF.defaultBlockState(),
                Blocks.TNT.defaultBlockState(),
                // Property-driven model variants: rotated hay textures and horizontal carved-pumpkin facing.
                Blocks.HAY_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                Blocks.HAY_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                Blocks.HAY_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z),
                Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH),
                Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH),
                Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST),
                Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
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
                long vanillaSeed = state.getSeed(fixture);
                // Exercise native model-part selection under several deterministic seeds.
                // The first is Minecraft's real position-derived seed; the others probe
                // alternate selected-part combinations without changing world state.
                long[] modelSeeds = {
                        vanillaSeed,
                        0x564F494446505300L + index,
                        0x9E3779B97F4A7C15L ^ (index * 0x100000001B3L),
                        0x6A09E667F3BCC909L + (index * 31L)
                };
                for (int seedIndex = 0; seedIndex < modelSeeds.length; seedIndex++) {
                    long seed = modelSeeds[seedIndex];
                    NativeModelSnapshot.Snapshot snapshot =
                            NativeModelSnapshot.collect(model, net.minecraft.util.RandomSource.create(seed));
                    SurfaceEligibility.NativeGeometryAnalysis eligibility =
                            SurfaceEligibility.analyzeNativeModel(state, snapshot);
                    if (!eligibility.supported()) {
                        throw new IllegalStateException(
                                "Expected ordinary full-cube fixture to pass native eligibility: "
                                        + state + " seedIndex=" + seedIndex
                                        + " rejected because " + eligibility.rejectReason());
                    }

                    Capture vanilla = capture(level, fixture, state, model, seed);
                    if (vanilla.quads().size() != 6 || vanilla.vertices().size() != 24) {
                        throw new IllegalStateException(
                                "Unexpected vanilla full-cube output for " + state
                                        + " seedIndex=" + seedIndex
                                        + ": quads=" + vanilla.quads().size()
                                        + ", vertices=" + vanilla.vertices().size());
                    }
                    verifyCandidateFaceSelection(level, fixture, state, model, seed, vanilla.quads());
                }
                verifyNativeWorldFaceCulling(level, fixture, state, model, vanillaSeed);
                System.out.println("VOID FPS additional full-cube native parity passed: state="
                        + state + ", quads=6, vertices=24, eligibility=accepted"
                        + ", deterministicSeeds=" + modelSeeds.length);
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
     * Verifies the candidate against every ModelBlockRenderer constructor flag combination.
     * The production caller may disable AO or face culling; candidate parity must respect both
     * switches rather than assuming the test fixture's usual (true, true) configuration.
     */
    private static void verifyRendererFlagParity(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        for (boolean ambientOcclusion : new boolean[]{false, true}) {
            for (boolean cull : new boolean[]{false, true}) {
                String scenario = "renderer-flags-ao-" + ambientOcclusion + "-cull-" + cull;
                verifyCandidateVertexOutput(
                        level, pos, state, model, seed, scenario, ambientOcclusion, cull);

                for (Direction covered : Direction.values()) {
                    BlockPos neighbourPos = pos.relative(covered);
                    BlockState previous = level.getBlockState(neighbourPos);
                    try {
                        level.setBlock(neighbourPos, Blocks.STONE.defaultBlockState(), 3);
                        if (!level.getBlockState(neighbourPos).is(Blocks.STONE)) {
                            throw new IllegalStateException(
                                    "Could not place renderer-flag parity neighbour on " + covered);
                        }
                        verifyCandidateVertexOutput(
                                level, pos, state, model, seed, scenario + "-covered-" + covered,
                                ambientOcclusion, cull);
                    } finally {
                        level.setBlock(neighbourPos, previous, 3);
                        if (!level.getBlockState(neighbourPos).equals(previous)) {
                            throw new IllegalStateException(
                                    "Could not restore renderer-flag parity neighbour at " + neighbourPos);
                        }
                    }
                }
            }
        }
        System.out.println("VOID FPS ModelBlockRenderer flag matrix passed: AO={false,true}, cull={false,true}, coveredDirections=6");
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
                level.setBlock(neighbourPos, Blocks.STONE.defaultBlockState(), 3);
                if (!level.getBlockState(neighbourPos).is(Blocks.STONE)) {
                    throw new IllegalStateException(
                            "Could not place candidate selector neighbour on " + direction);
                }

                Capture vanilla = capture(level, pos, state, model, seed);
                List<QuadSignature> candidate =
                        candidateVisibleQuads(level, pos, state, model, seed);
                assertCandidateMatchesVanilla(direction.name(), vanilla.quads(), candidate);
                verifyCandidateVertexOutput(level, pos, state, model, seed, direction.name());
            } finally {
                level.setBlock(neighbourPos, previous, 3);
                if (!level.getBlockState(neighbourPos).equals(previous)) {
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
     * The candidate must match vanilla for both occluding and non-occluding neighbour
     * materials, in every direction. This prevents a stone-only test from hiding
     * incorrect assumptions about transparent or foliage neighbours.
     */
    private static void verifyNativeWorldFaceCulling(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        BlockState[] neighbourStates = {
                Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState()
        };
        String[] neighbourLabels = {"air", "stone", "glass", "oak_leaves"};

        for (int neighbourIndex = 0; neighbourIndex < neighbourStates.length; neighbourIndex++) {
            BlockState neighbourState = neighbourStates[neighbourIndex];
            String neighbourLabel = neighbourLabels[neighbourIndex];
            for (Direction direction : Direction.values()) {
                BlockPos neighbourPos = pos.relative(direction);
                BlockState previous = level.getBlockState(neighbourPos);
                String scenario = "neighbour-" + neighbourLabel + "-" + direction.name();
                try {
                    // setBlock returns false when the requested state is already present
                    // (notably AIR in the initially empty test fixture). Verify the world state.
                    level.setBlock(neighbourPos, neighbourState, 3);
                    if (!level.getBlockState(neighbourPos).equals(neighbourState)) {
                        throw new IllegalStateException(
                                "Could not place temporary " + neighbourLabel
                                        + " neighbour for native culling test on " + direction);
                    }

                    Capture vanilla = capture(level, pos, state, model, seed);
                    List<QuadSignature> candidate =
                            candidateVisibleQuads(level, pos, state, model, seed);
                    assertCandidateMatchesVanilla(scenario, vanilla.quads(), candidate);
                    verifyCandidateVertexOutput(level, pos, state, model, seed, scenario);

                    if (neighbourState.is(Blocks.STONE)) {
                        boolean hiddenFaceStillPresent = vanilla.quads().stream()
                                .anyMatch(quad -> quad.direction() == direction);
                        if (vanilla.quads().size() != 5 || hiddenFaceStillPresent) {
                            throw new IllegalStateException(
                                    "Native opaque-neighbour culling mismatch on " + direction
                                            + ": quads=" + vanilla.quads().size()
                                            + ", hiddenFaceStillPresent=" + hiddenFaceStillPresent);
                        }
                    }
                } finally {
                    // setBlock may return false when the requested state is already present.
                    // Restoration is successful when the world actually contains the saved state.
                    level.setBlock(neighbourPos, previous, 3);
                    if (!level.getBlockState(neighbourPos).equals(previous)) {
                        throw new IllegalStateException(
                                "Could not restore temporary neighbour after culling test at " + neighbourPos);
                    }
                }
            }
        }
        System.out.println("VOID FPS native neighbour-material parity passed: materials=4, directions=6, "
                + "combinations=24, exactVertexParity=true");
    }

    /**
     * Builds the real 3x3x3 RenderSectionRegion from copied sections of loaded FULL chunks.
     * The region's block-state reads must match the live level for the fixture and its six
     * neighbours; native lighting and tint remain intentionally live-level dependencies.
     */
    private static void verifySectionRegionVertexParity(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        RenderSectionRegion region = createSectionRegion(level, pos, state);
        verifyCandidateVertexOutput(region, pos, state, model, seed, "section-region");
        Map<ChunkSectionLayer, MeshSnapshot> baselineMeshes =
                verifyNativeSectionCompilerMeshCapture(level, pos, region);
        verifyCandidateSectionShadowTraversal(region, SectionPos.of(pos), false, java.util.Map.of());
        verifyMixedEligibilitySectionTraversal(level, pos, state, baselineMeshes);
        System.out.println("VOID FPS real RenderSectionRegion fixture passed: sections=27"
                + ", centerAndSixNeighboursExact=true, lightingSource=liveClientLevel");

        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState previous = level.getBlockState(neighbourPos);
            try {
                level.setBlock(neighbourPos, Blocks.STONE.defaultBlockState(), 3);
                if (!level.getBlockState(neighbourPos).is(Blocks.STONE)) {
                    throw new IllegalStateException(
                            "Could not place section-region parity neighbour on " + direction);
                }

                RenderSectionRegion neighbourRegion = createSectionRegion(level, pos, state);
                BlockState copiedNeighbour = neighbourRegion.getBlockState(neighbourPos);
                if (!copiedNeighbour.is(Blocks.STONE)) {
                    throw new IllegalStateException(
                            "Section-region snapshot missed temporary neighbour on " + direction
                                    + ": " + copiedNeighbour);
                }
                Capture vanilla = capture(neighbourRegion, pos, state, model, seed);
                if (vanilla.quads().size() != 5 || vanilla.vertices().size() != 20) {
                    throw new IllegalStateException(
                            "Unexpected vanilla section-region output with neighbour " + direction
                                    + ": quads=" + vanilla.quads().size()
                                    + ", vertices=" + vanilla.vertices().size());
                }
                verifyCandidateVertexOutput(
                        neighbourRegion, pos, state, model, seed, "section-region-" + direction.name());
                System.out.println("VOID FPS section-region neighbour snapshot passed: covered="
                        + direction + ", copiedStateExact=true, vanillaVertices=20");
            } finally {
                level.setBlock(neighbourPos, previous, 3);
                if (!level.getBlockState(neighbourPos).equals(previous)) {
                    throw new IllegalStateException(
                            "Could not restore section-region neighbour at " + neighbourPos);
                }
            }
        }
    }

    /**
     * Captures the actual vanilla SectionCompiler output for the fixture's 16x16x16
     * section. This is a repeatability/baseline test only: SectionCompiler still uses
     * Minecraft's native ModelBlockRenderer and does not exercise a VOID replacement.
     */
    private static Map<ChunkSectionLayer, MeshSnapshot> verifyNativeSectionCompilerMeshCapture(
            ClientLevel level, BlockPos fixturePos, RenderSectionRegion region) {
        Minecraft minecraft = Minecraft.getInstance();
        var modelManager = minecraft.getModelManager();
        SectionCompiler compiler = new SectionCompiler(
                true,
                true,
                modelManager.getBlockStateModelSet(),
                modelManager.getFluidStateModelSet(),
                minecraft.getBlockColors());
        SectionBufferBuilderPack buffers = new SectionBufferBuilderPack();
        try {
            SectionPos sectionPos = SectionPos.of(fixturePos);
            Map<ChunkSectionLayer, MeshSnapshot> first = captureVanillaSectionMeshes(
                    compiler, sectionPos, region, buffers);
            Map<ChunkSectionLayer, MeshSnapshot> second = captureVanillaSectionMeshes(
                    compiler, sectionPos, region, buffers);
            if (first.isEmpty()) {
                throw new IllegalStateException(
                        "Vanilla SectionCompiler emitted no meshes for fixture section " + sectionPos);
            }
            if (!first.keySet().equals(second.keySet())) {
                throw new IllegalStateException(
                        "Repeated vanilla section compiles produced different render layers: first="
                                + first.keySet() + ", second=" + second.keySet());
            }
            for (ChunkSectionLayer layer : first.keySet()) {
                MeshSnapshot a = first.get(layer);
                MeshSnapshot b = second.get(layer);
                if (!a.metadata().equals(b.metadata()) || !Arrays.equals(a.bytes(), b.bytes())) {
                    throw new IllegalStateException(
                            "Repeated vanilla section mesh metadata/bytes differ for layer " + layer
                                    + ": first=" + a.metadata() + ", second=" + b.metadata()
                                    + ", firstBytes=" + a.bytes().length
                                    + ", secondBytes=" + b.bytes().length);
                }
            }
            int totalBytes = first.values().stream().mapToInt(mesh -> mesh.bytes().length).sum();
            int totalVertices = first.values().stream().mapToInt(mesh -> mesh.metadata().vertexCount()).sum();
            System.out.println("VOID FPS native vanilla SectionCompiler mesh capture passed: section="
                    + sectionPos + ", layers=" + first.size()
                    + ", vertices=" + totalVertices + ", vertexBytes=" + totalBytes
                    + ", formatAndTopologyValidated=true"
                    + ", vertexElements=" + first.values().stream()
                            .map(mesh -> mesh.metadata().elementDescriptors())
                            .toList()
                    + ", repeatedMeshBytesAndMetadataExact=true, candidateRendererExercised=false");
            return Map.copyOf(first);
        } finally {
            buffers.close();
        }
    }


    /**
     * Shadow-only section traversal: visit every block position in the real 16x16x16
     * section snapshot, run the independent candidate emitter for every geometry that
     * passes the conservative eligibility gate, and compare its ordered native vertex
     * records against vanilla ModelBlockRenderer. Ineligible states remain vanilla
     * fallback; this does not replace SectionCompiler or create production MeshData.
     */
    private static int verifyCandidateSectionShadowTraversal(
            RenderSectionRegion region,
            SectionPos sectionPos,
            boolean requireFallback,
            Map<BlockPos, BlockState> expectedFallbackFixtures) {
        Minecraft minecraft = Minecraft.getInstance();
        int minX = sectionPos.x() << 4;
        int minY = sectionPos.y() << 4;
        int minZ = sectionPos.z() << 4;
        int visitedNonAir = 0;
        int eligible = 0;
        int fallback = 0;
        java.util.Set<BlockPos> observedExpectedFallbackPositions = new java.util.LinkedHashSet<>();
        int comparedVertexRecords = 0;

        for (int y = minY; y < minY + 16; y++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int x = minX; x < minX + 16; x++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState state = region.getBlockState(blockPos);
                    if (state.isAir()) {
                        continue;
                    }
                    visitedNonAir++;

                    BlockStateModel model = minecraft.getModelManager()
                            .getBlockStateModelSet().get(state);
                    if (model == null) {
                        throw new IllegalStateException(
                                "Section shadow traversal found no model for " + state
                                        + " at " + blockPos);
                    }
                    long seed = state.getSeed(blockPos);
                    NativeModelSnapshot.Snapshot snapshot = NativeModelSnapshot.collect(
                            model, net.minecraft.util.RandomSource.create(seed));
                    SurfaceEligibility.NativeGeometryAnalysis analysis =
                            SurfaceEligibility.analyzeNativeModel(state, snapshot);
                    if (!analysis.supported()) {
                        fallback++;
                        BlockState expectedFixtureState = expectedFallbackFixtures.get(blockPos);
                        if (expectedFixtureState != null) {
                            if (!state.equals(expectedFixtureState)) {
                                throw new IllegalStateException(
                                        "Section snapshot changed a deliberate fallback fixture state at "
                                                + blockPos + ": expected=" + expectedFixtureState
                                                + ", actual=" + state);
                            }
                            observedExpectedFallbackPositions.add(blockPos);
                        }
                        continue;
                    }

                    comparedVertexRecords += verifyCandidateVertexOutput(
                            region, blockPos, state, model, seed,
                            "section-shadow-" + x + "-" + y + "-" + z);
                    eligible++;
                }
            }
        }

        if (visitedNonAir == 0 || eligible == 0) {
            throw new IllegalStateException(
                    "Section shadow traversal did not exercise eligible geometry: nonAir="
                            + visitedNonAir + ", eligible=" + eligible
                            + ", fallback=" + fallback + ", section=" + sectionPos);
        }
        if (requireFallback && fallback == 0) {
            throw new IllegalStateException(
                    "Mixed-eligibility section fixture did not exercise any vanilla fallback blocks");
        }
        if (!observedExpectedFallbackPositions.containsAll(expectedFallbackFixtures.keySet())) {
            java.util.Set<BlockPos> missingFallbackPositions = new java.util.LinkedHashSet<>(expectedFallbackFixtures.keySet());
            missingFallbackPositions.removeAll(observedExpectedFallbackPositions);
            throw new IllegalStateException(
                    "Section shadow traversal did not classify every deliberate fallback fixture as vanilla: "
                            + "missingPositions=" + missingFallbackPositions
                            + ", observed=" + observedExpectedFallbackPositions);
        }
        System.out.println("VOID FPS candidate section shadow traversal passed: section="
                + sectionPos + ", nonAirBlocks=" + visitedNonAir
                + ", eligibleBlocks=" + eligible + ", vanillaFallbackBlocks=" + fallback
                + ", expectedFallbackFixtures=" + expectedFallbackFixtures.size()
                + ", matchedExpectedFallbackFixtures=" + observedExpectedFallbackPositions.size()
                + ", candidateVertexRecordsCompared=" + comparedVertexRecords
                + ", perBlockNativeParityExact=true"
                + ", fullSectionMeshReplacement=false");
        return fallback;
    }

    /**
     * Places representative partial/non-cubic models inside the same real section,
     * snapshots the updated region, and proves the conservative gate leaves them
     * on vanilla fallback while still testing eligible full cubes in that section.
     * Every changed block is restored even if traversal fails.
     */
    private static void verifyMixedEligibilitySectionTraversal(
            ClientLevel level,
            BlockPos origin,
            BlockState expectedOriginState,
            Map<ChunkSectionLayer, MeshSnapshot> baselineMeshes) {
        SectionPos sectionPos = SectionPos.of(origin);
        int minX = sectionPos.x() << 4;
        int minY = sectionPos.y() << 4;
        int minZ = sectionPos.z() << 4;
        BlockState[] fallbackStates = {
                Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.TORCH.defaultBlockState(),
                Blocks.OAK_STAIRS.defaultBlockState()
        };
        Map<BlockPos, BlockState> previousStates = new LinkedHashMap<>();
        Map<BlockPos, BlockState> expectedFallbackFixtures = new LinkedHashMap<>();
        previousStates.put(origin, level.getBlockState(origin));
        if (!previousStates.get(origin).equals(expectedOriginState)) {
            throw new IllegalStateException("Mixed section fixture origin changed before setup");
        }

        try {
            for (int index = 0; index < fallbackStates.length; index++) {
                BlockPos fixture = new BlockPos(
                        minX + 2 + index * 2, minY + 1, minZ + 2);
                expectedFallbackFixtures.put(fixture, fallbackStates[index]);
                previousStates.put(fixture, level.getBlockState(fixture));
                // Give every fallback model an unobstructed six-neighbour test space.
                // Some models (notably leaves) can legitimately emit no visible faces
                // when nearby opaque blocks cover all of their cull directions.
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = fixture.relative(direction);
                    previousStates.putIfAbsent(neighbour, level.getBlockState(neighbour));
                    level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), 3);
                    if (!level.getBlockState(neighbour).isAir()) {
                        throw new IllegalStateException(
                                "Could not clear fallback fixture neighbour " + neighbour
                                        + " for " + fallbackStates[index]);
                    }
                }
                level.setBlock(fixture, fallbackStates[index], 3);
                if (!level.getBlockState(fixture).equals(fallbackStates[index])) {
                    throw new IllegalStateException(
                            "Could not place mixed-eligibility fixture " + fallbackStates[index]
                                    + " at " + fixture);
                }

                BlockState fixtureState = level.getBlockState(fixture);
                BlockStateModel fixtureModel = Minecraft.getInstance()
                        .getModelManager().getBlockStateModelSet().get(fixtureState);
                if (fixtureModel == null) {
                    throw new IllegalStateException(
                            "Mixed-eligibility fixture has no native block model: " + fixtureState);
                }
                NativeModelSnapshot.Snapshot fixtureSnapshot = NativeModelSnapshot.collect(
                        fixtureModel, net.minecraft.util.RandomSource.create(fixtureState.getSeed(fixture)));
                SurfaceEligibility.NativeGeometryAnalysis fixtureEligibility =
                        SurfaceEligibility.analyzeNativeModel(fixtureState, fixtureSnapshot);
                if (fixtureEligibility.supported()) {
                    throw new IllegalStateException(
                            "Mixed-eligibility fixture unexpectedly passed candidate geometry gate: "
                                    + fixtureState);
                }

                // Confirm the fallback is real geometry, not an empty or missing model.
                // Capture through Minecraft's native ModelBlockRenderer with the fixture's
                // actual world neighbours and position-derived model seed.
                Capture vanillaFixture = capture(
                        level, fixture, fixtureState, fixtureModel, fixtureState.getSeed(fixture));
                if (vanillaFixture.quads().isEmpty() || vanillaFixture.vertices().isEmpty()
                        || vanillaFixture.vertices().size() != vanillaFixture.quads().size() * 4) {
                    throw new IllegalStateException(
                            "Ineligible fallback fixture did not produce complete native model geometry: "
                                    + "state=" + fixtureState
                                    + ", quads=" + vanillaFixture.quads().size()
                                    + ", vertices=" + vanillaFixture.vertices().size());
                }

                // Capture each deliberately unsupported model twice in the same world
                // state. Fallback geometry must be stable, not merely non-empty.
                Capture repeatedVanillaFixture = capture(
                        level, fixture, fixtureState, fixtureModel, fixtureState.getSeed(fixture));
                if (!vanillaFixture.quads().equals(repeatedVanillaFixture.quads())
                        || !vanillaFixture.vertices().equals(repeatedVanillaFixture.vertices())) {
                    throw new IllegalStateException(
                            "Repeated native fallback fixture captures were not exact: state="
                                    + fixtureState
                                    + ", firstQuads=" + vanillaFixture.quads().size()
                                    + ", secondQuads=" + repeatedVanillaFixture.quads().size()
                                    + ", firstVertices=" + vanillaFixture.vertices().size()
                                    + ", secondVertices=" + repeatedVanillaFixture.vertices().size());
                }
                System.out.println("VOID FPS expected vanilla fallback classification passed: state="
                        + fixtureState + ", reason=" + fixtureEligibility.rejectReason()
                        + ", nativeQuads=" + vanillaFixture.quads().size()
                        + ", nativeVertices=" + vanillaFixture.vertices().size()
                        + ", completeNativeGeometry=true, repeatedCaptureExact=true");
            }

            RenderSectionRegion mixedRegion = createSectionRegion(level, origin, expectedOriginState);
            Map<ChunkSectionLayer, MeshSnapshot> mixedMeshes =
                    verifyNativeSectionCompilerMeshCapture(level, origin, mixedRegion);
            int baselineVertices = baselineMeshes.values().stream()
                    .mapToInt(mesh -> mesh.metadata().vertexCount()).sum();
            int mixedVertices = mixedMeshes.values().stream()
                    .mapToInt(mesh -> mesh.metadata().vertexCount()).sum();
            if (mixedMeshes.size() <= baselineMeshes.size() || mixedVertices <= baselineVertices) {
                throw new IllegalStateException(
                        "Mixed fallback geometry did not expand vanilla section mesh output: "
                                + "baselineLayers=" + baselineMeshes.keySet()
                                + ", mixedLayers=" + mixedMeshes.keySet()
                                + ", baselineVertices=" + baselineVertices
                                + ", mixedVertices=" + mixedVertices);
            }
            for (Map.Entry<ChunkSectionLayer, MeshSnapshot> baselineEntry : baselineMeshes.entrySet()) {
                MeshSnapshot mixedMesh = mixedMeshes.get(baselineEntry.getKey());
                if (mixedMesh == null) {
                    throw new IllegalStateException(
                            "Mixed fallback section lost an existing vanilla render layer: "
                                    + baselineEntry.getKey());
                }
                MeshMetadata baselineMetadata = baselineEntry.getValue().metadata();
                MeshMetadata mixedMetadata = mixedMesh.metadata();
                if (baselineMetadata.vertexStride() != mixedMetadata.vertexStride()
                        || !baselineMetadata.format().equals(mixedMetadata.format())
                        || !baselineMetadata.elementDescriptors().equals(mixedMetadata.elementDescriptors())
                        || !baselineMetadata.primitiveTopology().equals(mixedMetadata.primitiveTopology())) {
                    throw new IllegalStateException(
                            "Mixed fallback section changed the native vertex contract for "
                                    + baselineEntry.getKey()
                                    + ": baseline=" + baselineMetadata
                                    + ", mixed=" + mixedMetadata);
                }
            }
            MeshSnapshot translucentMesh = mixedMeshes.get(ChunkSectionLayer.TRANSLUCENT);
            if (translucentMesh == null || translucentMesh.metadata().vertexCount() <= 0) {
                throw new IllegalStateException(
                        "Glass-pane fallback fixture did not produce a non-empty vanilla translucent mesh: "
                                + mixedMeshes.keySet());
            }
            System.out.println("VOID FPS mixed vanilla section mesh delta passed: "
                    + "baselineLayers=" + baselineMeshes.size()
                    + ", mixedLayers=" + mixedMeshes.size()
                    + ", baselineVertices=" + baselineVertices
                    + ", mixedVertices=" + mixedVertices
                    + ", translucentVertices=" + translucentMesh.metadata().vertexCount()
                    + ", baselineLayerContractsPreserved=true"
                    + ", addedLayers=" + mixedMeshes.keySet().stream()
                            .filter(layer -> !baselineMeshes.containsKey(layer)).toList());
            int sectionFallbackCount = verifyCandidateSectionShadowTraversal(
                    mixedRegion, sectionPos, true, expectedFallbackFixtures);
            if (sectionFallbackCount < fallbackStates.length) {
                throw new IllegalStateException(
                        "Section traversal did not account for every deliberately placed fallback fixture: "
                                + "expectedAtLeast=" + fallbackStates.length
                                + ", actual=" + sectionFallbackCount);
            }
            System.out.println("VOID FPS mixed-eligibility section fixture passed: "
                    + "deliberateFallbackFixtures=" + fallbackStates.length
                    + ", sectionFallbackBlocks=" + sectionFallbackCount
                    + ", individuallyVerifiedFixtures=" + expectedFallbackFixtures.size()
                    + ", vanillaFallbackRequired=true");
        } finally {
            int restoredStates = 0;
            for (var entry : previousStates.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
                if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                    throw new IllegalStateException(
                            "Could not restore mixed-eligibility fixture block at " + entry.getKey());
                }
                restoredStates++;
            }
            if (restoredStates != previousStates.size()) {
                throw new IllegalStateException(
                        "Mixed-eligibility fixture restoration count mismatch: restored="
                                + restoredStates + ", expected=" + previousStates.size());
            }
            System.out.println("VOID FPS mixed-eligibility fixture world restoration passed: "
                    + "restoredStates=" + restoredStates + ", exactStateChecks=true");
        }
    }

    private static Map<ChunkSectionLayer, MeshSnapshot> captureVanillaSectionMeshes(
            SectionCompiler compiler,
            SectionPos sectionPos,
            RenderSectionRegion region,
            SectionBufferBuilderPack buffers) {
        SectionCompiler.Results results = compiler.compile(
                sectionPos, region, VertexSorting.DISTANCE_TO_ORIGIN, buffers);
        try {
            Map<ChunkSectionLayer, MeshSnapshot> snapshot = new LinkedHashMap<>();
            for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
                MeshData mesh = entry.getValue();
                if (mesh == null) {
                    throw new IllegalStateException(
                            "Vanilla SectionCompiler returned a null mesh for " + entry.getKey());
                }
                MeshData.DrawState drawState = mesh.drawState();
                int vertexCount = drawState.vertexCount();
                int vertexStride = drawState.format().getVertexSize();
                if (vertexStride <= 0 || drawState.format().getElements().isEmpty()) {
                    throw new IllegalStateException(
                            "Vanilla section mesh has an empty vertex format for " + entry.getKey()
                                    + ": stride=" + vertexStride + ", format=" + drawState.format());
                }
                java.util.Set<String> elementNames = new java.util.HashSet<>();
                java.util.Set<Integer> elementOffsets = new java.util.HashSet<>();
                List<String> elementDescriptors = new ArrayList<>();
                int previousElementEnd = 0;
                for (var element : drawState.format().getElements()) {
                    String elementName = element.name();
                    int elementOffset = element.offset();
                    int elementSize = element.format().blockSize();
                    int elementAlignment = element.format().byteAlignment();
                    var componentType = element.format().componentType();
                    int componentCount = element.format().componentCount();
                    int componentBytes = componentType == null ? 0 : componentType.byteSize();
                    long expectedComponentBytes = (long) componentBytes * componentCount;
                    long elementEnd = (long) elementOffset + elementSize;
                    if (elementName == null || elementName.isBlank() || !elementNames.add(elementName)) {
                        throw new IllegalStateException(
                                "Vanilla section mesh has a missing or duplicate vertex element name for "
                                        + entry.getKey() + ": " + elementName
                                        + ", format=" + drawState.format());
                    }
                    if (elementOffset < 0 || !elementOffsets.add(elementOffset)
                            || elementSize <= 0 || elementAlignment <= 0
                            || componentType == null || componentCount <= 0 || componentBytes <= 0
                            || expectedComponentBytes != elementSize
                            || elementOffset % elementAlignment != 0
                            || elementEnd > vertexStride) {
                        throw new IllegalStateException(
                                "Vanilla section mesh has an invalid vertex element layout for "
                                        + entry.getKey() + ": element=" + elementName
                                        + ", offset=" + elementOffset + ", size=" + elementSize
                                        + ", alignment=" + elementAlignment
                                        + ", componentType=" + componentType
                                        + ", componentCount=" + componentCount
                                        + ", componentBytes=" + componentBytes
                                        + ", expectedComponentBytes=" + expectedComponentBytes
                                        + ", stride=" + vertexStride
                                        + ", format=" + drawState.format());
                    }
                    if (elementOffset < previousElementEnd) {
                        throw new IllegalStateException(
                                "Vanilla section mesh vertex elements overlap or are out of byte-offset order for "
                                        + entry.getKey() + ": element=" + elementName
                                        + ", offset=" + elementOffset + ", previousEnd=" + previousElementEnd
                                        + ", format=" + drawState.format());
                    }
                    elementDescriptors.add(elementName + "@"
                            + elementOffset + ":" + element.format()
                            + "[bytes=" + elementSize
                            + ",alignment=" + elementAlignment
                            + ",componentType=" + element.format().componentType()
                            + ",componentCount=" + element.format().componentCount()
                            + ",componentBytes=" + element.format().componentType().byteSize()
                            + "]");
                    previousElementEnd = (int) elementEnd;
                }
                if (vertexCount <= 0 || vertexStride <= 0) {
                    throw new IllegalStateException(
                            "Vanilla SectionCompiler returned invalid vertex metadata for "
                                    + entry.getKey() + ": vertices=" + vertexCount
                                    + ", stride=" + vertexStride);
                }
                ByteBuffer source = mesh.vertexBuffer().duplicate();
                byte[] bytes = new byte[source.remaining()];
                source.get(bytes);
                long expectedBytes = (long) vertexCount * vertexStride;
                if (bytes.length == 0 || bytes.length != expectedBytes) {
                    throw new IllegalStateException(
                            "Vanilla section vertex-buffer size does not match draw metadata for "
                                    + entry.getKey() + ": bytes=" + bytes.length
                                    + ", vertices=" + vertexCount + ", stride=" + vertexStride
                                    + ", expectedBytes=" + expectedBytes);
                }
                if (drawState.indexCount() < 0 || drawState.primitiveTopology() == null) {
                    throw new IllegalStateException(
                            "Vanilla SectionCompiler returned invalid draw topology/index metadata for "
                                    + entry.getKey() + ": " + drawState);
                }
                MeshMetadata metadata = new MeshMetadata(
                        vertexCount, vertexStride, drawState.format().toString(),
                        String.join(" | ", elementDescriptors),
                        drawState.indexCount(), drawState.primitiveTopology().toString());
                snapshot.put(entry.getKey(), new MeshSnapshot(metadata, bytes));
            }
            return snapshot;
        } finally {
            results.release();
        }
    }

    private record MeshMetadata(
            int vertexCount, int vertexStride, String format, String elementDescriptors,
            int indexCount, String primitiveTopology) {
    }

    private record MeshSnapshot(MeshMetadata metadata, byte[] bytes) {
    }

    private static RenderSectionRegion createSectionRegion(
            ClientLevel level, BlockPos pos, BlockState expectedState) {
        int centerSectionX = pos.getX() >> 4;
        int centerSectionY = pos.getY() >> 4;
        int centerSectionZ = pos.getZ() >> 4;
        int minSectionX = centerSectionX - 1;
        int minSectionY = centerSectionY - 1;
        int minSectionZ = centerSectionZ - 1;
        int minWorldSectionY = level.getMinY() >> 4;
        SectionCopy[] sections = new SectionCopy[27];

        for (int dz = 0; dz < 3; dz++) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dx = 0; dx < 3; dx++) {
                    int sectionX = minSectionX + dx;
                    int sectionY = minSectionY + dy;
                    int sectionZ = minSectionZ + dz;
                    LevelChunk chunk = level.getChunkSource().getChunk(
                            sectionX, sectionZ, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        throw new IllegalStateException(
                                "Missing loaded FULL chunk for section-region fixture at "
                                        + sectionX + "," + sectionZ);
                    }
                    int sectionIndex = sectionY - minWorldSectionY;
                    sections[dx + dy * 3 + dz * 9] = new SectionCopy(chunk, sectionIndex);
                }
            }
        }

        RenderSectionRegion region = new RenderSectionRegion(
                level, minSectionX, minSectionY, minSectionZ, sections);
        if (!region.getBlockState(pos).equals(expectedState)) {
            throw new IllegalStateException(
                    "Section-region center state differs from live ClientLevel at " + pos
                            + ": region=" + region.getBlockState(pos)
                            + ", live=" + expectedState);
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState liveNeighbour = level.getBlockState(neighbourPos);
            BlockState regionNeighbour = region.getBlockState(neighbourPos);
            if (!regionNeighbour.equals(liveNeighbour)) {
                throw new IllegalStateException(
                        "Section-region neighbour differs from live ClientLevel on " + direction
                                + ": region=" + regionNeighbour + ", live=" + liveNeighbour);
            }
        }
        return region;
    }

    /**
     * Runs the independent candidate emitter against vanilla inside one native lighter-cache
     * scope. Both paths receive the same live ClientLevel, state, position, selected model,
     * seed, and thread-local BlockModelLighter cache. The current eligible set excludes tint,
     * offset functions, unculled quads, and non-solid/emissive materials.
     */
    private static int verifyCandidateVertexOutput(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            String scenario) {
        return verifyCandidateVertexOutput(level, pos, state, model, seed, scenario, true, true);
    }

    private static int verifyCandidateVertexOutput(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            String scenario,
            boolean ambientOcclusion,
            boolean cull) {
        BlockModelLighter.enableCaching();
        try {
            Capture vanilla = capture(level, pos, state, model, seed, ambientOcclusion, cull);
            Capture candidate = captureCandidate(level, pos, state, model, seed, ambientOcclusion, cull);
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
            return parity.comparedVertices();
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
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            boolean ambientOcclusion,
            boolean cull) {
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
                ambientOcclusion && state.getLightEmission() == 0 && snapshot.parts().getFirst().ambientOcclusion();
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
                if (cull && !net.minecraft.world.level.block.Block.shouldRenderFace(state, neighbour, direction)) {
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
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed) {
        return capture(level, pos, state, model, seed, true, true);
    }

    private static Capture capture(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            boolean ambientOcclusion,
            boolean cull) {
        ModelBlockRenderer renderer = new ModelBlockRenderer(
                ambientOcclusion, cull, BlockColors.createDefault());
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
