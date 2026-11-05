package dev.voidfps.surface;

import net.fabricmc.api.ClientModInitializer;

/**
 * Test-only entrypoint. This class is compiled from src/testmod and is never
 * included in the published VOID FPS mod JAR.
 */
public final class NativeRecorderTestEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NativeVertexRecorder recorder = new NativeVertexRecorder();
        NativeVertexRecord expected = new NativeVertexRecord(
                1.25f, 2.5f, 3.75f, 0xA1B2C3D4, 0.125f, 0.875f,
                0x12345678, 0x23456789, 0.0f, 1.0f, 0.0f);

        recorder.addVertex(expected.x(), expected.y(), expected.z(),
                expected.color(), expected.u(), expected.v(), expected.overlay(), expected.light(),
                expected.normalX(), expected.normalY(), expected.normalZ());

        var captured = recorder.snapshot();
        if (captured.size() != 1
                || !NativeVertexParity.compare(java.util.List.of(expected), captured, 0).matches()) {
            throw new IllegalStateException("NativeVertexRecorder packed-call check failed: " + captured);
        }

        recorder.clear();
        if (!recorder.snapshot().isEmpty()) {
            throw new IllegalStateException("NativeVertexRecorder clear check failed");
        }

        System.out.println("VOID FPS native recorder test passed");
    }
}
