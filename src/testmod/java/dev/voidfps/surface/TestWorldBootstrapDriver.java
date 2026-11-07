package dev.voidfps.surface;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test-only integration driver. It completes Minecraft's first-run
 * accessibility onboarding through vanilla GUI hit-testing, then uses the
 * vanilla Create World screen and verifies a real FULL client chunk.
 */
final class TestWorldBootstrapDriver {
    private static final long SCREEN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(90);
    private static final long WORLD_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(180);
    private static final long DISCONNECT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(45);
    private static final long POLL_MILLIS = 200L;

    private enum Phase {
        WAITING_FOR_INITIAL_SCREEN,
        WAITING_FOR_ONBOARDING_EXIT,
        WAITING_FOR_CREATE_SCREEN,
        WAITING_FOR_WORLD,
        WAITING_FOR_FULL_CHUNK,
        WAITING_FOR_DISCONNECT
    }

    private final Minecraft minecraft;
    private Phase phase = Phase.WAITING_FOR_INITIAL_SCREEN;
    private long phaseStartedAt = System.nanoTime();
    private boolean clickDispatched;

    private TestWorldBootstrapDriver(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    static void start(Minecraft minecraft) {
        System.out.println("VOID FPS test-world bootstrap waiting for first-run UI");
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
            case WAITING_FOR_INITIAL_SCREEN -> waitForInitialScreen();
            case WAITING_FOR_ONBOARDING_EXIT -> waitForOnboardingExit();
            case WAITING_FOR_CREATE_SCREEN -> waitForCreateScreen();
            case WAITING_FOR_WORLD -> waitForWorld();
            case WAITING_FOR_FULL_CHUNK -> waitForFullChunk();
            case WAITING_FOR_DISCONNECT -> waitForDisconnect();
        }
    }

    private void waitForInitialScreen() {
        Screen current = minecraft.gui.screen();
        if (current instanceof AccessibilityOnboardingScreen onboarding) {
            Button continueButton = findButton(onboarding, CommonComponents.GUI_CONTINUE);
            if (continueButton == null) {
                throw new IllegalStateException("Accessibility onboarding Continue button was not found");
            }
            dispatchButtonClick(onboarding, continueButton, "accessibility Continue");
            phase = Phase.WAITING_FOR_ONBOARDING_EXIT;
            phaseStartedAt = System.nanoTime();
            System.out.println("VOID FPS first-run accessibility Continue click dispatched through Screen.mouseClicked");
            scheduleNext();
            return;
        }
        if (current != null) {
            beginCreateWorldFlow();
            return;
        }
        if (expired(SCREEN_TIMEOUT_NANOS)) {
            throw new IllegalStateException("Timed out waiting for initial Minecraft screen");
        }
        scheduleNext();
    }

    private void waitForOnboardingExit() {
        Screen current = minecraft.gui.screen();
        if (!(current instanceof AccessibilityOnboardingScreen)) {
            System.out.println("VOID FPS first-run accessibility onboarding closed through vanilla UI");
            beginCreateWorldFlow();
            return;
        }
        if (expired(SCREEN_TIMEOUT_NANOS)) {
            throw new IllegalStateException("Timed out waiting for accessibility onboarding fade/close");
        }
        scheduleNext();
    }

    private void beginCreateWorldFlow() {
        phase = Phase.WAITING_FOR_CREATE_SCREEN;
        phaseStartedAt = System.nanoTime();
        CreateWorldScreen.testWorld(minecraft, () ->
                System.out.println("VOID FPS test-world Create World screen closed"));
        System.out.println("VOID FPS vanilla test-world flow requested after onboarding");
        scheduleNext();
    }

    private void waitForCreateScreen() {
        Screen current = minecraft.gui.screen();
        if (current instanceof CreateWorldScreen createScreen) {
            dispatchCreateClick(createScreen);
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

    private Button findButton(Screen screen, net.minecraft.network.chat.Component message) {
        List<Button> matches = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof Button button && button.getMessage().equals(message)) {
                matches.add(button);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Expected at most one matching button, found " + matches.size());
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void dispatchCreateClick(CreateWorldScreen screen) {
        List<Button> matches = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof Button button
                    && button.getMessage().getContents() instanceof TranslatableContents translated
                    && "selectWorld.create".equals(translated.getKey())) {
                matches.add(button);
            }
        }

        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one vanilla selectWorld.create button, found " + matches.size());
        }
        dispatchButtonClick(screen, matches.get(0), "vanilla Create");
    }

    private void dispatchButtonClick(Screen screen, Button button, String label) {
        if (!button.isActive() || !button.visible) {
            throw new IllegalStateException(label + " button is not active and visible");
        }

        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        var hit = screen.getChildAt(x, y).orElse(null);
        if (hit != button) {
            throw new IllegalStateException(
                    "Vanilla screen hit-testing did not resolve " + label + " button at its center");
        }

        MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        // Keep vanilla coordinate hit-testing above, but invoke the resolved
        // vanilla AbstractButton callback directly. In the headless client,
        // Screen.mouseClicked can consume the event without reaching the
        // widget's onPress callback, which left onboarding's fade timer unset.
        button.onClick(click, false);
        clickDispatched = true;
        System.out.println("VOID FPS vanilla " + label
                + " button callback invoked after verified screen hit-testing");
    }

    private void waitForWorld() {
        Screen current = minecraft.gui.screen();
        if (current instanceof ConfirmScreen confirmation) {
            var title = confirmation.getTitle().getContents();
            if (!(title instanceof TranslatableContents translated)
                    || !("selectWorld.warning.experimental.title".equals(translated.getKey())
                    || "selectWorld.warning.deprecated.title".equals(translated.getKey()))) {
                throw new IllegalStateException(
                        "Unexpected confirmation screen during automated world creation: "
                                + confirmation.getTitle().getString());
            }

            Button yesButton = findButton(confirmation, CommonComponents.GUI_YES);
            if (yesButton == null) {
                throw new IllegalStateException(
                        "Expected vanilla Yes button on world-creation lifecycle confirmation");
            }
            dispatchButtonClick(confirmation, yesButton, "world-creation confirmation Yes");
            System.out.println("VOID FPS accepted vanilla world-creation lifecycle confirmation: "
                    + translated.getKey());
            scheduleNext();
            return;
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
