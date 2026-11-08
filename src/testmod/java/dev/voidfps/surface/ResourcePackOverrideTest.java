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
                            } catch (RuntimeException | Error verificationFailure) {
                                failure = verificationFailure;
                            }
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
