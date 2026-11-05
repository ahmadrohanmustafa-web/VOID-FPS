package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only entrypoint. This class is compiled from src/testmod and is never
 * included in the published VOID FPS mod JAR.
 */
public final class NativeRecorderTestEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NativeVertexRecorder recorder = new NativeVertexRecorder();

        Vector3f[] positions = {
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f(1.0f, 0.0f, 0.0f),
                new Vector3f(1.0f, 1.0f, 0.0f),
                new Vector3f(0.0f, 1.0f, 0.0f)
        };
        float[][] uvs = {
                {0.125f, 0.25f},
                {0.375f, 0.50f},
                {0.625f, 0.75f},
                {0.875f, 1.00f}
        };
        BakedQuad quad = new BakedQuad(
                positions[0], positions[1], positions[2], positions[3],
                packUv(uvs[0][0], uvs[0][1]),
                packUv(uvs[1][0], uvs[1][1]),
                packUv(uvs[2][0], uvs[2][1]),
                packUv(uvs[3][0], uvs[3][1]),
                Direction.SOUTH,
                new MaterialInfo(null, null, null, null, null, -1, null, 7));

        int[] colors = {0xA1B2C3D4, 0x10203040, 0x50607080, 0x90A0B0C0};
        int[] lights = {0x00100020, 0x00200030, 0x00300040, 0x00400050};
        int overlay = OverlayTexture.NO_OVERLAY;
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
                        "Unexpected material emission result for vertex " + i
                                + ": expected 0x00700070 but got 0x"
                                + Integer.toHexString(emittedLight));
            }
            expected.add(new NativeVertexRecord(
                    positions[i].x() + 10.0f,
                    positions[i].y() - 2.0f,
                    positions[i].z() + 5.0f,
                    colors[i], uvs[i][0], uvs[i][1], overlay,
                    expectedEmittedLight,
                    0.0f, 0.0f, 1.0f));
        }

        // Exercise Minecraft's real packed-vertex default implementation.
        recorder.putBlockBakedQuad(10.0f, -2.0f, 5.0f, quad, instance);

        var captured = recorder.snapshot();
        if (!NativeVertexParity.compare(expected, captured, 0).matches()) {
            throw new IllegalStateException(
                    "VertexConsumer.putBlockBakedQuad check failed. Expected "
                            + expected + " but captured " + captured);
        }

        recorder.clear();
        if (!recorder.snapshot().isEmpty()) {
            throw new IllegalStateException("NativeVertexRecorder clear check failed");
        }

        System.out.println("VOID FPS native putBlockBakedQuad UV and emission test passed");
    }

    private static long packUv(float u, float v) {
        return (long) Float.floatToRawIntBits(u) << 32
                | Float.floatToRawIntBits(v) & 0xFFFFFFFFL;
    }
}
