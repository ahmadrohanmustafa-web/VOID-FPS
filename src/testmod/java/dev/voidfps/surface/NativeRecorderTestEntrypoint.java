package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test-only entrypoint. This class is compiled from src/testmod and is never
 * included in the published VOID FPS mod JAR.
 */
public final class NativeRecorderTestEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NativeVertexRecorder recorder = new NativeVertexRecorder();

        float[][] uvs = {
                {0.125f, 0.25f},
                {0.375f, 0.50f},
                {0.625f, 0.75f},
                {0.875f, 1.00f}
        };
        int[] colors = {0xA1B2C3D4, 0x10203040, 0x50607080, 0x90A0B0C0};
        int[] lights = {0x00100020, 0x00200030, 0x00300040, 0x00400050};
        int overlay = OverlayTexture.NO_OVERLAY;

        // Exercise all six native face directions with distinct per-vertex attributes.
        // This verifies that Minecraft's real packed-vertex path preserves UV/color/light
        // data and emits the expected direction normal for every face orientation.
        for (Direction direction : Direction.values()) {
            // Use a real unit face plane for each direction instead of reusing an XY quad.
            // This keeps the test geometry consistent with the BakedQuad's declared face.
            Vector3f[] positions = positionsFor(direction);
            verifyFaceGeometry(direction, positions);
            BakedQuad quad = new BakedQuad(
                    positions[0], positions[1], positions[2], positions[3],
                    packUv(uvs[0][0], uvs[0][1]),
                    packUv(uvs[1][0], uvs[1][1]),
                    packUv(uvs[2][0], uvs[2][1]),
                    packUv(uvs[3][0], uvs[3][1]),
                    direction,
                    new MaterialInfo(null, null, null, null, null, -1, null, 7));

            QuadInstance instance = new QuadInstance();
            instance.setOverlayCoords(overlay);
            List<NativeVertexRecord> expected = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                instance.setColor(i, colors[i]);
                instance.setLightCoords(i, lights[i]);
                int emittedLight = instance.getLightCoordsWithEmission(i, 7);
                // Bytecode-verified rule: emission raises both packed block and sky
                // channels to at least the material emission level.
                int expectedEmittedLight = LightCoordsUtil.pack(
                        Math.max(LightCoordsUtil.block(lights[i]), 7),
                        Math.max(LightCoordsUtil.sky(lights[i]), 7));
                if (emittedLight != expectedEmittedLight || emittedLight != 0x00700070) {
                    throw new IllegalStateException(
                            "Unexpected material emission result for " + direction + " vertex " + i
                                    + ": expected 0x00700070 but got 0x"
                                    + Integer.toHexString(emittedLight));
                }
                expected.add(new NativeVertexRecord(
                        positions[i].x() + 10.0f,
                        positions[i].y() - 2.0f,
                        positions[i].z() + 5.0f,
                        colors[i], uvs[i][0], uvs[i][1], overlay,
                        expectedEmittedLight,
                        direction.getStepX(), direction.getStepY(), direction.getStepZ()));
            }

            recorder.putBlockBakedQuad(10.0f, -2.0f, 5.0f, quad, instance);
            var captured = recorder.snapshot();
            if (!NativeVertexParity.compare(expected, captured, 0).matches()) {
                throw new IllegalStateException(
                        "VertexConsumer.putBlockBakedQuad check failed for " + direction
                                + ". Expected " + expected + " but captured " + captured);
            }
            recorder.clear();
            if (!recorder.snapshot().isEmpty()) {
                throw new IllegalStateException(
                        "NativeVertexRecorder clear check failed after " + direction);
            }
        }

        System.out.println("VOID FPS native putBlockBakedQuad all-six-directions UV, color, light, normal, and emission test passed");

        // Fabric invokes this entrypoint during Minecraft construction, before
        // the initial resource reload has populated ModelManager. Poll from a
        // delayed executor, then inspect the model only on the client thread.
        Minecraft minecraft = Minecraft.getInstance();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        scheduleStoneModelTest(minecraft, deadlineNanos);
    }

    private static void scheduleStoneModelTest(Minecraft minecraft, long deadlineNanos) {
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() ->
                minecraft.execute(() -> {
                    try {
                        // This getter throws until the block-state models have been
                        // installed by Minecraft's initial resource reload.
                        minecraft.getModelManager().getBlockStateModelSet();
                    } catch (NullPointerException notReady) {
                        if (!"Block models not yet initialized".equals(notReady.getMessage())) {
                            throw notReady;
                        }
                        if (System.nanoTime() >= deadlineNanos) {
                            throw new IllegalStateException(
                                    "Timed out waiting for Minecraft's block models to initialize",
                                    notReady);
                        }
                        scheduleStoneModelTest(minecraft, deadlineNanos);
                        return;
                    }

                    try {
                        testResourceLoadedStoneModel();
                    } catch (RuntimeException | Error failure) {
                        // Always close the automated client after a failed assertion;
                        // otherwise CI reports a misleading 180-second timeout.
                        minecraft.stop();
                        throw failure;
                    }
                }));
    }

    private static void testResourceLoadedStoneModel() {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        var state = Blocks.STONE.defaultBlockState();
        var model = modelSet.get(state);
        if (model == null) {
            throw new IllegalStateException("Resource-loaded stone BlockStateModel is missing");
        }

        long seed = 0x564F49444650534CL;
        NativeModelSnapshot.Snapshot first =
                NativeModelSnapshot.collect(model, RandomSource.create(seed));
        NativeModelSnapshot.Snapshot second =
                NativeModelSnapshot.collect(model, RandomSource.create(seed));
        if (first.isEmpty()) {
            throw new IllegalStateException("Resource-loaded stone model selected no parts");
        }
        if (!first.parts().equals(second.parts())) {
            throw new IllegalStateException("Stone model selection changed for the same seed");
        }

        int directionalQuadCount = 0;
        int unculledQuadCount = 0;
        for (NativeModelSnapshot.Part part : first.parts()) {
            unculledQuadCount += part.unculledQuads().size();
            for (NativeModelSnapshot.FaceQuads face : part.directionalQuads()) {
                directionalQuadCount += face.quads().size();
            }
        }
        if (directionalQuadCount == 0) {
            throw new IllegalStateException("Resource-loaded stone model has no directional quads");
        }
        if (!first.hasExactlyOneUnitBoundaryQuadPerFace()) {
            throw new IllegalStateException(
                    "Stone model did not satisfy the six unit-boundary-face geometry precheck");
        }
        if (!first.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException(
                    "Stone model did not satisfy the plain solid untinted material precheck");
        }

        // Exercise the actual native eligibility gate, not just its individual geometry
        // and material predicates. Stone should pass; air and block-entity states must fall
        // back to vanilla before any custom surface path can be considered.
        var stoneEligibility = SurfaceEligibility.analyzeNativeModel(state, first);
        if (!stoneEligibility.supported()) {
            throw new IllegalStateException(
                    "Native eligibility rejected ordinary stone: " + stoneEligibility.rejectReason());
        }
        var airEligibility = SurfaceEligibility.analyzeNativeModel(
                Blocks.AIR.defaultBlockState(), null);
        if (airEligibility.supported()
                || !"block state is not a solid render state".equals(airEligibility.rejectReason())) {
            throw new IllegalStateException(
                    "Native eligibility did not safely reject air: " + airEligibility);
        }
        assertNativeEligibilityRejected(Blocks.CHEST.defaultBlockState(), "chest",
                "block state declares a block entity");
        assertNativeEligibilityRejected(Blocks.GLASS.defaultBlockState(), "glass",
                "block state is not a solid render state",
                "block state cannot occlude neighbouring faces");
        assertNativeEligibilityRejected(Blocks.OAK_STAIRS.defaultBlockState(), "oak stairs",
                "block state is not a solid render state",
                "block state does not have a full-block occlusion shape");
        assertNativeEligibilityRejected(Blocks.GLOWSTONE.defaultBlockState(), "glowstone",
                "block state emits light");

        verifyNativeEligibilityMatrix(modelSet);
        verifyNativeFallbackMatrix(modelSet);
        verifyVanillaStoneFaceCulling();

        System.out.println("VOID FPS resource-loaded stone model snapshot passed: parts="
                + first.parts().size() + ", directionalQuads=" + directionalQuadCount
                + ", unculledQuads=" + unculledQuadCount);
        System.out.println("VOID FPS stone geometry/material prechecks passed");
        System.out.println("VOID FPS native eligibility gate passed: stone accepted; air, chest, glass, stairs, and glowstone rejected");
        System.out.println("VOID FPS vanilla stone face-culling checks passed for all 6 directions");

        // Exercise a real folder resource pack through Minecraft's own repository and reload.
        // Only after restoring the original selected packs do we continue the world test.
        ResourcePackOverrideTest.start(minecraft, first, () -> TestWorldBootstrapDriver.start(minecraft));
    }

    /**
     * Exercises the resource-loaded native eligibility gate across several ordinary
     * full-cube models, then confirms a tinted grass model is rejected for material
     * semantics rather than being silently treated as a plain solid cube.
     */
    private static void verifyNativeEligibilityMatrix(BlockStateModelSet modelSet) {
        net.minecraft.world.level.block.state.BlockState[] acceptedStates = {
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.SANDSTONE.defaultBlockState(),
                Blocks.OBSIDIAN.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(),
                Blocks.QUARTZ_BLOCK.defaultBlockState(),
                Blocks.MOSS_BLOCK.defaultBlockState(),
                // Full-cube models with deliberately different face textures/orientation.
                Blocks.HAY_BLOCK.defaultBlockState(),
                Blocks.PUMPKIN.defaultBlockState(),
                Blocks.BOOKSHELF.defaultBlockState(),
                Blocks.TNT.defaultBlockState()
        };

        int accepted = 0;
        for (int index = 0; index < acceptedStates.length; index++) {
            var state = acceptedStates[index];
            var model = modelSet.get(state);
            if (model == null) {
                throw new IllegalStateException("Missing resource-loaded model for eligibility fixture " + state);
            }
            NativeModelSnapshot.Snapshot snapshot = NativeModelSnapshot.collect(
                    model, RandomSource.create(0x564F494446505300L + index));
            SurfaceEligibility.NativeGeometryAnalysis result =
                    SurfaceEligibility.analyzeNativeModel(state, snapshot);
            if (!result.supported()) {
                throw new IllegalStateException(
                        "Expected ordinary full-cube model to pass native eligibility: "
                                + state + " rejected because " + result.rejectReason());
            }
            accepted++;
        }

        var grassState = Blocks.GRASS_BLOCK.defaultBlockState();
        var grassModel = modelSet.get(grassState);
        if (grassModel == null) {
            throw new IllegalStateException("Missing resource-loaded grass block model");
        }
        NativeModelSnapshot.Snapshot grassSnapshot = NativeModelSnapshot.collect(
                grassModel, RandomSource.create(0x4752415353424C4FL));
        if (grassSnapshot.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException(
                    "Tinted grass block model incorrectly passed the plain untinted material precheck");
        }
        SurfaceEligibility.NativeGeometryAnalysis grassResult =
                SurfaceEligibility.analyzeNativeModel(grassState, grassSnapshot);
        if (grassResult.supported()) {
            throw new IllegalStateException(
                    "Tinted grass block incorrectly passed native full-cube eligibility");
        }

        System.out.println("VOID FPS native eligibility matrix passed: accepted="
                + accepted + ", tintedFallback=grass_block, rejectReason=" + grassResult.rejectReason());
    }

    /**
     * Negative eligibility matrix for common non-cube and non-opaque states.
     * These models must stay on vanilla's renderer; this test only checks the
     * conservative gate and never attempts to emit candidate geometry for them.
     */
    private static void verifyNativeFallbackMatrix(BlockStateModelSet modelSet) {
        net.minecraft.world.level.block.state.BlockState[] modelStates = {
                Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.TORCH.defaultBlockState()
        };

        int modelChecks = 0;
        for (var state : modelStates) {
            var model = modelSet.get(state);
            if (model == null) {
                throw new IllegalStateException(
                        "Missing resource-loaded model for non-cube fallback fixture " + state);
            }
            NativeModelSnapshot.Snapshot snapshot = NativeModelSnapshot.collect(
                    model, RandomSource.create(0x564F494446414C4CL + modelChecks));
            boolean fullCubeGeometry = snapshot.hasExactlyOneUnitBoundaryQuadPerFace();
            boolean plainSolidMaterial = snapshot.hasOnlyPlainSolidUntintedMaterials();
            if (fullCubeGeometry && plainSolidMaterial) {
                throw new IllegalStateException(
                        "Non-cube fallback model passed both native geometry and material prechecks: "
                                + state);
            }

            SurfaceEligibility.NativeGeometryAnalysis result =
                    SurfaceEligibility.analyzeNativeModel(state, snapshot);
            if (result.supported() || result.rejectReason() == null || result.rejectReason().isBlank()) {
                throw new IllegalStateException(
                        "Unsafe fallback fixture unexpectedly passed native eligibility: " + state
                                + ", result=" + result);
            }
            modelChecks++;
            System.out.println("VOID FPS native fallback model inspection passed: block="
                    + state.getBlock() + ", fullCubeGeometry=" + fullCubeGeometry
                    + ", plainSolidMaterial=" + plainSolidMaterial
                    + ", eligibilityRejected=true");
        }

        // Water is rendered through the fluid pipeline rather than a normal solid block model.
        // Confirm that its state is rejected before a model snapshot is needed.
        var water = Blocks.WATER.defaultBlockState();
        SurfaceEligibility.NativeGeometryAnalysis waterResult =
                SurfaceEligibility.analyzeNativeModel(water, null);
        if (waterResult.supported() || waterResult.rejectReason() == null
                || waterResult.rejectReason().isBlank()) {
            throw new IllegalStateException(
                    "Water unexpectedly passed native block-model eligibility: " + waterResult);
        }

        System.out.println("VOID FPS native fallback matrix passed: modelChecks="
                + modelChecks + ", rejected=6, vanillaFallbackRequired=true");
    }

    private static void assertNativeEligibilityRejected(
            net.minecraft.world.level.block.state.BlockState state,
            String label,
            String... expectedReasonPrefixes) {
        var result = SurfaceEligibility.analyzeNativeModel(state, null);
        boolean expectedReason = result.rejectReason() != null
                && java.util.Arrays.stream(expectedReasonPrefixes)
                        .anyMatch(result.rejectReason()::startsWith);
        if (result.supported() || !expectedReason) {
            throw new IllegalStateException(
                    "Native eligibility rejected " + label + " for an unexpected reason. Expected one of "
                            + java.util.Arrays.toString(expectedReasonPrefixes) + " but got: " + result);
        }
        System.out.println("VOID FPS safely rejected " + label
                + " through native eligibility: " + result.rejectReason());
    }

    private static void verifyVanillaStoneFaceCulling() {
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();

        for (Direction direction : Direction.values()) {
            if (!net.minecraft.world.level.block.Block.shouldRenderFace(stone, air, direction)) {
                throw new IllegalStateException(
                        "Vanilla unexpectedly culled stone against air on " + direction);
            }
            if (net.minecraft.world.level.block.Block.shouldRenderFace(stone, stone, direction)) {
                throw new IllegalStateException(
                        "Vanilla unexpectedly rendered the internal stone face on " + direction);
            }
        }
    }

    private static void verifyFaceGeometry(Direction direction, Vector3f[] positions) {
        if (positions.length != 4) {
            throw new IllegalStateException("Expected four vertices for " + direction);
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Vector3f position : positions) {
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
            float plane = switch (direction.getAxis()) {
                case X -> position.x();
                case Y -> position.y();
                case Z -> position.z();
            };
            float expectedPlane = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;
            if (plane != expectedPlane) {
                throw new IllegalStateException("Face geometry is not on the " + direction + " boundary plane");
            }
        }
        if (maxX - minX != (direction.getAxis() == Direction.Axis.X ? 0.0f : 1.0f)
                || maxY - minY != (direction.getAxis() == Direction.Axis.Y ? 0.0f : 1.0f)
                || maxZ - minZ != (direction.getAxis() == Direction.Axis.Z ? 0.0f : 1.0f)) {
            throw new IllegalStateException("Face geometry does not span the expected unit square for " + direction);
        }
        Vector3f a = positions[1].sub(positions[0], new Vector3f());
        Vector3f b = positions[2].sub(positions[0], new Vector3f());
        Vector3f cross = a.cross(b, new Vector3f());
        float dot = cross.x() * direction.getStepX()
                + cross.y() * direction.getStepY()
                + cross.z() * direction.getStepZ();
        if (dot <= 0.0f) {
            throw new IllegalStateException("Face vertex winding does not point outward for " + direction);
        }
    }

    private static Vector3f[] positionsFor(Direction direction) {
        return switch (direction) {
            case DOWN -> new Vector3f[] {
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(1.0f, 0.0f, 0.0f),
                    new Vector3f(1.0f, 0.0f, 1.0f),
                    new Vector3f(0.0f, 0.0f, 1.0f)
            };
            case UP -> new Vector3f[] {
                    new Vector3f(0.0f, 1.0f, 0.0f),
                    new Vector3f(0.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 1.0f, 0.0f)
            };
            case NORTH -> new Vector3f[] {
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(0.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, 0.0f, 0.0f)
            };
            case SOUTH -> new Vector3f[] {
                    new Vector3f(0.0f, 0.0f, 1.0f),
                    new Vector3f(1.0f, 0.0f, 1.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(0.0f, 1.0f, 1.0f)
            };
            case WEST -> new Vector3f[] {
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(0.0f, 0.0f, 1.0f),
                    new Vector3f(0.0f, 1.0f, 1.0f),
                    new Vector3f(0.0f, 1.0f, 0.0f)
            };
            case EAST -> new Vector3f[] {
                    new Vector3f(1.0f, 0.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 0.0f, 1.0f)
            };
        };
    }

    private static long packUv(float u, float v) {
        return (long) Float.floatToRawIntBits(u) << 32
                | Float.floatToRawIntBits(v) & 0xFFFFFFFFL;
    }
}
