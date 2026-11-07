package dev.voidfps.surface;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test-only integration driver. It uses the vanilla Create World screen and
 * vanilla GUI hit-testing, then verifies a real integrated server and FULL
 * client chunk before disconnecting through Minecraft's normal save path.
 */
final class TestWorldBootstrapDriver {
    private static final long SCREEN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(90);
    private static final long WORLD_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(180);
    private static final long DISCONNECT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(45);
    private static final long POLL_MILLIS = 200L;

    private enum Phase {
        WAITING_FOR_CREATE_SCREEN,
        WAITING_FOR_WORLD,
        WAITING_FOR_FULL_CHUNK,
        WAITING_FOR_DISCONNECT
    }

    private final Minecraft minecraft;
    private final long startedAt = System.nanoTime();
    private Phase phase = Phase.WAITING_FOR_CREATE_SCREEN;
    private long phaseStartedAt = startedAt;
    private boolean clickDispatched;

    private TestWorldBootstrapDriver(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    static void start(Minecraft minecraft) {
        System.out.println("VOID FPS test-world bootstrap starting through vanilla Create World UI");
        CreateWorldScreen.testWorld(minecraft, () ->
                System.out.println("VOID FPS test-world Create World screen closed"));
        new TestWorldBootstrapDriver(minecraft).scheduleNext();
    }

    private void scheduleNext() {
        CompletableFuture.delayedExecutor(POLL_MILLIS, TimeUnit.MILLISECONDS).execute(() ->
                minecraft.execute(() -> {
                    try {
                        tick();
                    } catch (RuntimeException | Error failure) {
                        fail(failure);
                    }
                }));
    }

    private void tick() {
        switch (phase) {
            case WAITING_FOR_CREATE_SCREEN -> waitForCreateScreen();
            case WAITING_FOR_WORLD -> waitForWorld();
            case WAITING_FOR_FULL_CHUNK -> waitForFullChunk();
            case WAITING_FOR_DISCONNECT -> waitForDisconnect();
        }
    }

    private void waitForCreateScreen() {
        Screen current = minecraft.gui.screen();
        if (current instanceof CreateWorldScreen createScreen) {
            dispatchVanillaCreateClick(createScreen);
            phase = Phase.WAITING_FOR_WORLD;
            phaseStartedAt = System.nanoTime();
            scheduleNext();
            return;
        }
        if (expired(SCREEN_TIMEOUT_NANOS)) {
            throw new IllegalStateException(
                    "Timed out waiting for CreateWorldScreen; current screen="
                            + (current == null ? "null" : current.getClass().getName()));
        }
        scheduleNext();
    }

    private void dispatchVanillaCreateClick(CreateWorldScreen screen) {
        List<Button> matches = new ArrayList<>();
        for (var child : screen.children()) {
            if (!(child instanceof Button button)) {
                continue;
            }
            if (button.getMessage().getContents() instanceof TranslatableContents translated
                    && "selectWorld.create".equals(translated.getKey())) {
                matches.add(button);
            }
        }

        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one vanilla selectWorld.create button, found " + matches.size());
        }

        Button createButton = matches.get(0);
        if (!createButton.isActive()) {
            throw new IllegalStateException("Vanilla Create button is not active");
        }

        double x = createButton.getX() + createButton.getWidth() / 2.0;
        double y = createButton.getY() + createButton.getHeight() / 2.0;
        var hit = screen.getChildAt(x, y).orElse(null);
        if (hit != createButton) {
            throw new IllegalStateException(
                    "Vanilla screen hit-testing did not resolve the Create button at its center");
        }

        MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        if (!screen.mouseClicked(click, false)) {
            throw new IllegalStateException("Vanilla Screen.mouseClicked did not consume the Create click");
        }

        clickDispatched = true;
        System.out.println("VOID FPS vanilla Create button click dispatched through Screen.mouseClicked");
    }

    private void waitForWorld() {
        Screen current = minecraft.gui.screen();
        if (current instanceof ConfirmScreen) {
            throw new IllegalStateException(
                    "Unexpected confirmation screen appeared during automated world creation");
        }
        if (minecraft.hasSingleplayerServer() && minecraft.level != null) {
            phase = Phase.WAITING_FOR_FULL_CHUNK;
            phaseStartedAt = System.nanoTime();
            System.out.println("VOID FPS integrated server and ClientLevel detected");
            scheduleNext();
            return;
        }
        if (expired(WORLD_TIMEOUT_NANOS)) {
            throw new IllegalStateException(
                    "Timed out waiting for integrated server and ClientLevel; clickDispatched="
                            + clickDispatched + ", screen="
                            + (current == null ? "null" : current.getClass().getName()));
        }
        scheduleNext();
    }

    private void waitForFullChunk() {
        if (!minecraft.hasSingleplayerServer() || minecraft.level == null) {
            if (expired(WORLD_TIMEOUT_NANOS)) {
                throw new IllegalStateException("Client world disappeared before a FULL chunk was verified");
            }
            scheduleNext();
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player != null) {
            int chunkX = player.blockPosition().getX() >> 4;
            int chunkZ = player.blockPosition().getZ() >> 4;
            ClientLevel level = minecraft.level;
            ClientChunkCache chunks = level.getChunkSource();
            LevelChunk chunk = chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);

            if (chunk != null && chunk.getPos().equals(new ChunkPos(chunkX, chunkZ))) {
                boolean hasBlockData = false;
                for (var section : chunk.getSections()) {
                    if (section != null && !section.hasOnlyAir()) {
                        hasBlockData = true;
                        break;
                    }
                }
                if (hasBlockData) {
                    System.out.println("VOID FPS real-world bootstrap passed: integratedServer=true"
                            + ", clientLevel=true, loadedChunks=" + chunks.getLoadedChunksCount()
                            + ", playerChunk=" + chunk.getPos()
                            + ", fullChunk=true, nonAirSection=true");
                    beginDisconnect();
                    return;
                }
            }
        }

        if (expired(WORLD_TIMEOUT_NANOS)) {
            throw new IllegalStateException(
                    "Timed out waiting for the player's actual FULL chunk with non-air section data");
        }
        scheduleNext();
    }

    private void beginDisconnect() {
        phase = Phase.WAITING_FOR_DISCONNECT;
        phaseStartedAt = System.nanoTime();
        minecraft.disconnectWithSavingScreen();
        scheduleNext();
    }

    private void waitForDisconnect() {
        if (minecraft.level == null && !minecraft.hasSingleplayerServer()) {
            System.out.println("VOID FPS test world disconnected through normal save lifecycle");
            minecraft.stop();
            return;
        }
        if (expired(DISCONNECT_TIMEOUT_NANOS)) {
            throw new IllegalStateException(
                    "Timed out waiting for normal client/server disconnect; levelPresent="
                            + (minecraft.level != null)
                            + ", integratedServer=" + minecraft.hasSingleplayerServer());
        }
        scheduleNext();
    }

    private boolean expired(long timeoutNanos) {
        return System.nanoTime() - phaseStartedAt >= timeoutNanos;
    }

    private void fail(Throwable failure) {
        System.err.println("VOID FPS test-world bootstrap failed during " + phase + ": " + failure);
        failure.printStackTrace();
        minecraft.stop();
    }
}
