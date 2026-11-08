package dev.voidfps.surface;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Test-client-only integration test. Creates a real folder resource pack in
 * Minecraft's resourcepacks directory, discovers/selects it through the
 * existing PackRepository, reloads Minecraft's resource pipeline, verifies
 * the changed stone model, and restores the user's original selected packs.
 */
final class ResourcePackOverrideTest {
    private static final String PACK_FOLDER = "voidfps-resource-pack-test";
    private static final String PACK_TITLE = "VOID FPS Resource Pack Integration Test";
    private static final long MODEL_SEED = 0x564F49444650534CL;

    private ResourcePackOverrideTest() {
    }

    static void start(
            Minecraft minecraft,
            NativeModelSnapshot.Snapshot vanillaStone,
            Runnable onSuccess) {
        List<String> originalSelectedIds =
                new ArrayList<>(minecraft.getResourcePackRepository().getSelectedIds());
        Path packDirectory = minecraft.getResourcePackDirectory().resolve(PACK_FOLDER);

        try {
            if (Files.exists(packDirectory)) {
                throw new IllegalStateException(
                        "Refusing to overwrite an existing resource-pack test directory: " + packDirectory);
            }
            writeTemporaryPack(minecraft, packDirectory);
            PackRepository repository = minecraft.getResourcePackRepository();
            repository.reload();

            Pack discovered = repository.getAvailablePacks().stream()
                    .filter(pack -> pack.location().id().contains(PACK_FOLDER))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Minecraft PackRepository did not discover temporary folder pack "
                                    + PACK_FOLDER + "; available IDs=" + repository.getAvailableIds()));

            String packId = discovered.location().id();
            if (!repository.addPack(packId)) {
                throw new IllegalStateException(
                        "Minecraft PackRepository refused to select discovered test pack " + packId);
            }
            if (!repository.getSelectedIds().contains(packId)) {
                throw new IllegalStateException(
                        "Temporary resource pack was not present in selected pack IDs after addPack");
            }

            System.out.println("VOID FPS real resource-pack folder discovered and selected through PackRepository: "
                    + packId);
            minecraft.reloadResourcePacks().whenComplete((ignored, reloadFailure) ->
                    minecraft.execute(() -> {
                        Throwable failure = reloadFailure;
                        if (failure == null) {
                            try {
                                verifyOverride(minecraft, vanillaStone);
                                writePartialGeometryOverride(packDirectory);
                            } catch (IOException | RuntimeException | Error verificationFailure) {
                                failure = verificationFailure;
                            }
                        }
                        if (failure == null) {
                            minecraft.reloadResourcePacks().whenComplete((partialIgnored, partialReloadFailure) ->
                                    minecraft.execute(() -> {
                                        Throwable partialFailure = partialReloadFailure;
                                        if (partialFailure == null) {
                                            try {
                                                verifyPartialGeometryOverride(minecraft);
                                                writeInternalGeometryOverride(packDirectory);
                                            } catch (IOException | RuntimeException | Error verificationFailure) {
                                                partialFailure = verificationFailure;
                                            }
                                        }
                                        if (partialFailure != null) {
                                            restore(minecraft, repository, originalSelectedIds, packDirectory,
                                                    partialFailure, onSuccess);
                                            return;
                                        }
                                        minecraft.reloadResourcePacks().whenComplete((internalIgnored, internalReloadFailure) ->
                                                minecraft.execute(() -> {
                                                    Throwable internalFailure = internalReloadFailure;
                                                    if (internalFailure == null) {
                                                        try {
                                                            verifyInternalGeometryOverride(minecraft);
                                                            writeUnculledGeometryOverride(packDirectory);
                                                        } catch (IOException | RuntimeException | Error verificationFailure) {
                                                            internalFailure = verificationFailure;
                                                        }
                                                    }
                                                    if (internalFailure != null) {
                                                        restore(minecraft, repository, originalSelectedIds, packDirectory,
                                                                internalFailure, onSuccess);
                                                        return;
                                                    }
                                                    minecraft.reloadResourcePacks().whenComplete((unculledIgnored, unculledReloadFailure) ->
                                                            minecraft.execute(() -> {
                                                                Throwable unculledFailure = unculledReloadFailure;
                                                                if (unculledFailure == null) {
                                                                    try {
                                                                        verifyUnculledGeometryOverride(minecraft);
                                                                        writeDuplicateBoundaryGeometryOverride(packDirectory);
                                                                    } catch (IOException | RuntimeException | Error verificationFailure) {
                                                                        unculledFailure = verificationFailure;
                                                                    }
                                                                }
                                                                if (unculledFailure != null) {
                                                                    restore(minecraft, repository, originalSelectedIds, packDirectory,
                                                                            unculledFailure, onSuccess);
                                                                    return;
                                                                }
                                                                minecraft.reloadResourcePacks().whenComplete((duplicateIgnored, duplicateReloadFailure) ->
                                                                        minecraft.execute(() -> {
                                                                            Throwable duplicateFailure = duplicateReloadFailure;
                                                                            if (duplicateFailure == null) {
                                                                                try {
                                                                                    verifyDuplicateBoundaryGeometryOverride(minecraft);
                                                                                } catch (RuntimeException | Error verificationFailure) {
                                                                                    duplicateFailure = verificationFailure;
                                                                                }
                                                                            }
                                                                            if (duplicateFailure != null) {
                                                                     restore(minecraft, repository, originalSelectedIds, packDirectory,
                                                                             duplicateFailure, onSuccess);
                                                                     return;
                                                                 }
                                                                 try {
                                                                     writeTintedMaterialOverride(packDirectory);
                                                                 } catch (IOException | RuntimeException | Error fixtureFailure) {
                                                                     restore(minecraft, repository, originalSelectedIds, packDirectory,
                                                                             fixtureFailure, onSuccess);
                                                                     return;
                                                                 }
                                                                 minecraft.reloadResourcePacks().whenComplete((tintedIgnored, tintedReloadFailure) ->
                                                                         minecraft.execute(() -> {
                                                                             Throwable tintedFailure = tintedReloadFailure;
                                                                             if (tintedFailure == null) {
                                                                                 try {
                                                                                     verifyTintedMaterialOverride(minecraft);
                                                                                 } catch (RuntimeException | Error verificationFailure) {
                                                                                     tintedFailure = verificationFailure;
                                                                                 }
                                                                             }
                                                                             restore(minecraft, repository, originalSelectedIds, packDirectory,
                                                                                     tintedFailure, onSuccess);
                                                                         }));
                                                                        }));
                                                            }));
                                                }));
                                    }));
                            return;
                        }
                        restore(minecraft, repository, originalSelectedIds, packDirectory,
                                failure, onSuccess);
                    }));
        } catch (IOException | RuntimeException | Error setupFailure) {
            restore(minecraft, minecraft.getResourcePackRepository(), originalSelectedIds,
                    packDirectory, setupFailure, onSuccess);
        }
    }

    private static void writeTemporaryPack(Minecraft minecraft, Path packDirectory)
            throws IOException {
        PackFormat format = net.minecraft.SharedConstants.getCurrentVersion()
                .packVersion(PackType.CLIENT_RESOURCES);
        Files.createDirectories(packDirectory.resolve("assets/minecraft/models/block"));
        String metadata = """
                {
                  "pack": {
                    "description": "%s",
                    "min_format": [%d, %d],
                    "max_format": [%d, %d]
                  }
                }
                """.formatted(PACK_TITLE, format.major(), format.minor(),
                format.major(), format.minor());
        Files.writeString(packDirectory.resolve("pack.mcmeta"), metadata);

        // The parent supplies vanilla cube geometry; only the texture is changed.
        // This makes the override easy to identify without introducing custom geometry.
        String model = """
                {
                  "parent": "minecraft:block/cube_all",
                  "textures": {
                    "all": "minecraft:block/dirt"
                  }
                }
                """;
        Files.writeString(
                packDirectory.resolve("assets/minecraft/models/block/stone.json"), model);

        if (!Files.isRegularFile(packDirectory.resolve("pack.mcmeta"))
                || !Files.isRegularFile(
                        packDirectory.resolve("assets/minecraft/models/block/stone.json"))) {
            throw new IllegalStateException("Temporary resource pack files were not created");
        }
        System.out.println("VOID FPS wrote temporary pack.mcmeta using live Minecraft 26.3 resource format "
                + format + " and stone model override");
    }

    private static void verifyOverride(
            Minecraft minecraft,
            NativeModelSnapshot.Snapshot vanillaStone) {
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        var state = Blocks.STONE.defaultBlockState();
        var overriddenModel = modelSet.get(state);
        if (overriddenModel == null) {
            throw new IllegalStateException(
                    "Stone model missing after real resource-pack reload");
        }

        NativeModelSnapshot.Snapshot overridden = NativeModelSnapshot.collect(
                overriddenModel, RandomSource.create(MODEL_SEED));
        if (overridden.isEmpty()) {
            throw new IllegalStateException(
                    "Activated resource-pack stone override produced an empty model snapshot");
        }
        if (overridden.parts().equals(vanillaStone.parts())) {
            throw new IllegalStateException(
                    "Stone model snapshot did not change after activating dirt-textured folder-pack override");
        }

        var eligibility = SurfaceEligibility.analyzeNativeModel(state, overridden);
        if (!eligibility.supported()) {
            throw new IllegalStateException(
                    "Full-cube resource-pack override should remain eligible; got "
                            + eligibility.rejectReason());
        }
        if (!overridden.hasExactlyOneUnitBoundaryQuadPerFace()
                || !overridden.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException(
                    "Resource-pack override changed geometry/material semantics unexpectedly");
        }

        System.out.println("VOID FPS real resource-pack override passed: selected through PackRepository, "
                + "Minecraft reload completed, stone model snapshot differs from vanilla, "
                + "six-face full-cube geometry remains eligible");
    }

    /**
     * Replaces the same live folder-pack model with a deliberately partial cube.
     * The real reload must expose that geometry and the native eligibility gate
     * must reject it, proving resource-pack custom geometry stays on vanilla.
     */
    private static void writePartialGeometryOverride(Path packDirectory) throws IOException {
        String model = """
                {
                  "textures": {
                    "all": "minecraft:block/dirt"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 8, 16],
                      "faces": {
                        "down": {"texture": "#all"},
                        "up": {"texture": "#all"},
                        "north": {"texture": "#all"},
                        "south": {"texture": "#all"},
                        "west": {"texture": "#all"},
                        "east": {"texture": "#all"}
                      }
                    }
                  ]
                }
                """;
        Files.writeString(
                packDirectory.resolve("assets/minecraft/models/block/stone.json"), model);
        System.out.println("VOID FPS rewrote active folder-pack stone model with deliberate half-height geometry");
    }

    private static void verifyPartialGeometryOverride(Minecraft minecraft) {
        var state = Blocks.STONE.defaultBlockState();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            throw new IllegalStateException("Stone model missing after partial-geometry resource-pack reload");
        }
        NativeModelSnapshot.Snapshot partial = NativeModelSnapshot.collect(
                model, RandomSource.create(MODEL_SEED));
        if (partial.isEmpty()) {
            throw new IllegalStateException("Partial-geometry resource-pack override produced an empty snapshot");
        }
        if (partial.hasExactlyOneUnitBoundaryQuadPerFace()) {
            throw new IllegalStateException("Half-height resource-pack geometry incorrectly passed full-cube geometry gate");
        }
        if (!partial.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException("Partial-geometry fixture unexpectedly changed plain solid material semantics");
        }
        var eligibility = SurfaceEligibility.analyzeNativeModel(state, partial);
        if (eligibility.supported()
                || !"native model is not exactly one unit boundary quad per face".equals(eligibility.rejectReason())) {
            throw new IllegalStateException(
                    "Partial-geometry resource-pack model was not rejected by the native geometry gate: "
                            + eligibility);
        }
        System.out.println("VOID FPS real partial-geometry resource-pack override passed: live reload exposed half-height model and native eligibility required vanilla fallback");
    }

    /**
     * Adds a second, fully enclosed element inside a full-sized cube. The
     * model still has all six outer boundary faces, but also contains internal
     * geometry that a surface-only renderer must never silently discard.
     */
    private static void writeInternalGeometryOverride(Path packDirectory) throws IOException {
        String model = """
                {
                  "textures": {
                    "all": "minecraft:block/dirt"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "down": {"texture": "#all"},
                        "up": {"texture": "#all"},
                        "north": {"texture": "#all"},
                        "south": {"texture": "#all"},
                        "west": {"texture": "#all"},
                        "east": {"texture": "#all"}
                      }
                    },
                    {
                      "from": [4, 4, 4],
                      "to": [12, 12, 12],
                      "faces": {
                        "down": {"texture": "#all"},
                        "up": {"texture": "#all"},
                        "north": {"texture": "#all"},
                        "south": {"texture": "#all"},
                        "west": {"texture": "#all"},
                        "east": {"texture": "#all"}
                      }
                    }
                  ]
                }
                """;
        Files.writeString(
                packDirectory.resolve("assets/minecraft/models/block/stone.json"), model);
        System.out.println("VOID FPS rewrote active folder-pack stone model with an enclosed internal element");
    }

    private static void verifyInternalGeometryOverride(Minecraft minecraft) {
        var state = Blocks.STONE.defaultBlockState();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            throw new IllegalStateException("Stone model missing after internal-geometry resource-pack reload");
        }
        NativeModelSnapshot.Snapshot internal = NativeModelSnapshot.collect(
                model, RandomSource.create(MODEL_SEED));
        if (internal.isEmpty()) {
            throw new IllegalStateException("Internal-geometry resource-pack override produced an empty snapshot");
        }
        if (internal.hasExactlyOneUnitBoundaryQuadPerFace()) {
            throw new IllegalStateException("Model with an enclosed internal element incorrectly passed full-cube geometry gate");
        }
        if (!internal.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException("Internal-geometry fixture unexpectedly changed plain solid material semantics");
        }
        var eligibility = SurfaceEligibility.analyzeNativeModel(state, internal);
        if (eligibility.supported()
                || !"native model is not exactly one unit boundary quad per face".equals(eligibility.rejectReason())) {
            throw new IllegalStateException(
                    "Internal-geometry resource-pack model was not rejected by the native geometry gate: "
                            + eligibility);
        }
        System.out.println("VOID FPS real internal-geometry resource-pack override passed: live reload exposed enclosed extra element and native eligibility required vanilla fallback");
    }

    /**
     * Adds an additional face with no cull direction. Such quads are kept in
     * the native unculled list and must force vanilla fallback.
     */
    private static void writeUnculledGeometryOverride(Path packDirectory) throws IOException {
        String model = """
                {
                  "textures": {
                    "all": "minecraft:block/dirt"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "down": {"texture": "#all"},
                        "up": {"texture": "#all"},
                        "north": {"texture": "#all"},
                        "south": {"texture": "#all"},
                        "west": {"texture": "#all"},
                        "east": {"texture": "#all"}
                      }
                    },
                    {
                      "from": [2, 2, 2],
                      "to": [14, 14, 3],
                      "faces": {
                        "north": {"texture": "#all"}
                      }
                    }
                  ]
                }
                """;
        Files.writeString(
                packDirectory.resolve("assets/minecraft/models/block/stone.json"), model);
        System.out.println("VOID FPS rewrote active folder-pack stone model with an additional unculled face");
    }

    private static void verifyUnculledGeometryOverride(Minecraft minecraft) {
        var state = Blocks.STONE.defaultBlockState();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            throw new IllegalStateException("Stone model missing after unculled-geometry resource-pack reload");
        }
        NativeModelSnapshot.Snapshot snapshot = NativeModelSnapshot.collect(
                model, RandomSource.create(MODEL_SEED));
        if (snapshot.isEmpty()) {
            throw new IllegalStateException("Unculled-geometry resource-pack override produced an empty snapshot");
        }
        int unculledCount = snapshot.parts().stream()
                .mapToInt(part -> part.unculledQuads().size())
                .sum();
        if (unculledCount < 1) {
            throw new IllegalStateException(
                    "Resource-pack model's face without cullface was not captured as an unculled quad");
        }
        if (snapshot.hasExactlyOneUnitBoundaryQuadPerFace()) {
            throw new IllegalStateException("Model containing unculled geometry incorrectly passed full-cube geometry gate");
        }
        if (!snapshot.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException("Unculled-geometry fixture unexpectedly changed plain solid material semantics");
        }
        var eligibility = SurfaceEligibility.analyzeNativeModel(state, snapshot);
        if (eligibility.supported()
                || !"native model is not exactly one unit boundary quad per face".equals(eligibility.rejectReason())) {
            throw new IllegalStateException(
                    "Unculled resource-pack model was not rejected by the native geometry gate: "
                            + eligibility);
        }
        System.out.println("VOID FPS real unculled-geometry resource-pack override passed: live reload captured unculled quad and native eligibility required vanilla fallback, unculledQuads="
                + unculledCount);
    }

    /**
     * Duplicates a full-cube element. Every face remains on a unit boundary,
     * but each direction now has two competing quads; the surface gate must
     * reject this rather than silently discard one copy.
     */
    private static void writeDuplicateBoundaryGeometryOverride(Path packDirectory) throws IOException {
        String model = """
                {
                  "textures": {
                    "all": "minecraft:block/dirt"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "down": {"texture": "#all", "cullface": "down"},
                        "up": {"texture": "#all", "cullface": "up"},
                        "north": {"texture": "#all", "cullface": "north"},
                        "south": {"texture": "#all", "cullface": "south"},
                        "west": {"texture": "#all", "cullface": "west"},
                        "east": {"texture": "#all", "cullface": "east"}
                      }
                    },
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "down": {"texture": "#all", "cullface": "down"},
                        "up": {"texture": "#all", "cullface": "up"},
                        "north": {"texture": "#all", "cullface": "north"},
                        "south": {"texture": "#all", "cullface": "south"},
                        "west": {"texture": "#all", "cullface": "west"},
                        "east": {"texture": "#all", "cullface": "east"}
                      }
                    }
                  ]
                }
                """;
        Files.writeString(
                packDirectory.resolve("assets/minecraft/models/block/stone.json"), model);
        System.out.println("VOID FPS rewrote active folder-pack stone model with duplicate boundary faces");
    }

    private static void verifyDuplicateBoundaryGeometryOverride(Minecraft minecraft) {
        var state = Blocks.STONE.defaultBlockState();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            throw new IllegalStateException("Stone model missing after duplicate-boundary resource-pack reload");
        }
        NativeModelSnapshot.Snapshot snapshot = NativeModelSnapshot.collect(
                model, RandomSource.create(MODEL_SEED));
        if (snapshot.isEmpty()) {
            throw new IllegalStateException("Duplicate-boundary resource-pack override produced an empty snapshot");
        }
        int directionalCount = snapshot.parts().stream()
                .flatMap(part -> part.directionalQuads().stream())
                .mapToInt(face -> face.quads().size())
                .sum();
        if (directionalCount != 12) {
            throw new IllegalStateException(
                    "Duplicate-boundary fixture should expose exactly 12 directional quads; got " + directionalCount);
        }
        if (!snapshot.parts().stream().allMatch(part -> part.unculledQuads().isEmpty())) {
            throw new IllegalStateException("Duplicate-boundary fixture unexpectedly introduced unculled quads");
        }
        if (snapshot.hasExactlyOneUnitBoundaryQuadPerFace()) {
            throw new IllegalStateException("Duplicate boundary faces incorrectly passed the one-quad-per-face gate");
        }
        if (!snapshot.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException("Duplicate-boundary fixture unexpectedly changed plain solid material semantics");
        }
        var eligibility = SurfaceEligibility.analyzeNativeModel(state, snapshot);
        if (eligibility.supported()
                || !"native model is not exactly one unit boundary quad per face".equals(eligibility.rejectReason())) {
            throw new IllegalStateException(
                    "Duplicate-boundary resource-pack model was not rejected by the native geometry gate: "
                            + eligibility);
        }
        System.out.println("VOID FPS real duplicate-boundary resource-pack override passed: live reload exposed 12 boundary quads and native eligibility required vanilla fallback");
    }

    /**
     * Applies tint metadata to an otherwise valid full cube through the live
     * resource-pack pipeline. Geometry remains eligible, but material metadata
     * must force the native path to fall back to vanilla.
     */
    private static void writeTintedMaterialOverride(Path packDirectory) throws IOException {
        String model = """
                {
                  "textures": {
                    "all": "minecraft:block/dirt"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "faces": {
                        "down": {"texture": "#all", "cullface": "down", "tintindex": 0},
                        "up": {"texture": "#all", "cullface": "up", "tintindex": 0},
                        "north": {"texture": "#all", "cullface": "north", "tintindex": 0},
                        "south": {"texture": "#all", "cullface": "south", "tintindex": 0},
                        "west": {"texture": "#all", "cullface": "west", "tintindex": 0},
                        "east": {"texture": "#all", "cullface": "east", "tintindex": 0}
                      }
                    }
                  ]
                }
                """;
        Files.writeString(
                packDirectory.resolve("assets/minecraft/models/block/stone.json"), model);
        System.out.println("VOID FPS rewrote active folder-pack stone model with tint metadata on all six faces");
    }

    private static void verifyTintedMaterialOverride(Minecraft minecraft) {
        var state = Blocks.STONE.defaultBlockState();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            throw new IllegalStateException("Stone model missing after tinted-material resource-pack reload");
        }
        NativeModelSnapshot.Snapshot tinted = NativeModelSnapshot.collect(
                model, RandomSource.create(MODEL_SEED));
        if (tinted.isEmpty()) {
            throw new IllegalStateException("Tinted resource-pack override produced an empty model snapshot");
        }
        if (!tinted.hasExactlyOneUnitBoundaryQuadPerFace()) {
            throw new IllegalStateException("Tinted-material fixture unexpectedly changed full-cube geometry");
        }
        if (tinted.hasOnlyPlainSolidUntintedMaterials()) {
            throw new IllegalStateException("Tinted resource-pack material incorrectly passed the plain-material gate");
        }
        var eligibility = SurfaceEligibility.analyzeNativeModel(state, tinted);
        if (eligibility.supported()
                || !"native quad material is not plain solid, untinted, static, and non-emissive"
                        .equals(eligibility.rejectReason())) {
            throw new IllegalStateException(
                    "Tinted resource-pack model was not rejected by the native material gate: " + eligibility);
        }
        System.out.println("VOID FPS real tinted-material resource-pack override passed: six-face geometry remained valid but tint metadata required vanilla fallback");
    }

    private static void restore(
            Minecraft minecraft,
            PackRepository repository,
            Collection<String> originalSelectedIds,
            Path packDirectory,
            Throwable priorFailure,
            Runnable onSuccess) {
        try {
            repository.setSelected(originalSelectedIds);
            minecraft.reloadResourcePacks().whenComplete((ignored, restoreFailure) ->
                    minecraft.execute(() -> {
                        Throwable failure = priorFailure;
                        if (restoreFailure != null) {
                            if (failure == null) {
                                failure = restoreFailure;
                            } else {
                                failure.addSuppressed(restoreFailure);
                            }
                        }

                        try {
                            repository.reload();
                            deleteTemporaryPack(packDirectory);
                        } catch (IOException | RuntimeException cleanupFailure) {
                            if (failure == null) {
                                failure = cleanupFailure;
                            } else {
                                failure.addSuppressed(cleanupFailure);
                            }
                        }

                        if (failure != null) {
                            System.err.println("VOID FPS real resource-pack integration test failed: " + failure);
                            failure.printStackTrace();
                            minecraft.stop();
                            return;
                        }

                        System.out.println("VOID FPS original resource-pack selection restored and temporary folder removed");
                        onSuccess.run();
                    }));
        } catch (RuntimeException | Error restoreSetupFailure) {
            if (priorFailure != null) {
                restoreSetupFailure.addSuppressed(priorFailure);
            }
            try {
                deleteTemporaryPack(packDirectory);
            } catch (IOException cleanupFailure) {
                restoreSetupFailure.addSuppressed(cleanupFailure);
            }
            System.err.println("VOID FPS could not restore resource-pack selection: " + restoreSetupFailure);
            restoreSetupFailure.printStackTrace();
            minecraft.stop();
        }
    }

    private static void deleteTemporaryPack(Path packDirectory) throws IOException {
        if (!Files.exists(packDirectory)) {
            return;
        }
        try (var paths = Files.walk(packDirectory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
