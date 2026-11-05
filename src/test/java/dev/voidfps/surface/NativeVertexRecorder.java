package dev.voidfps.surface;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only recorder for Minecraft 26.3's native VertexConsumer block-quad output.
 *
 * <p>Pass an instance to {@code VertexConsumer.putBlockBakedQuad(...)} to capture the exact
 * ordered arguments delivered through the interface's packed addVertex default method.
 * This class records calls; it does not itself create a world or invoke vanilla rendering.</p>
 */
final class NativeVertexRecorder implements VertexConsumer {
    private final List<NativeVertexRecord> vertices = new ArrayList<>();

    private float x;
    private float y;
    private float z;
    private float u;
    private float v;
    private float normalX;
    private float normalY;
    private float normalZ;
    private int color;
    private int overlay;
    private int light;
    private boolean vertexPending;

    List<NativeVertexRecord> snapshot() {
        if (vertexPending) {
            throw new IllegalStateException("incomplete vertex: normal was not submitted");
        }
        return List.copyOf(vertices);
    }

    void clear() {
        if (vertexPending) {
            throw new IllegalStateException("cannot clear while a vertex is incomplete");
        }
        vertices.clear();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (vertexPending) {
            throw new IllegalStateException("previous vertex did not receive a normal");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        vertexPending = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return setColor((alpha & 0xFF) << 24 | (red & 0xFF) << 16
                | (green & 0xFF) << 8 | (blue & 0xFF));
    }

    @Override
    public VertexConsumer setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.overlay = packPair(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.light = packPair(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (!vertexPending) {
            throw new IllegalStateException("normal submitted without a vertex");
        }
        normalX = x;
        normalY = y;
        normalZ = z;
        vertices.add(new NativeVertexRecord(
                this.x, this.y, this.z, color, u, v, overlay, light,
                normalX, normalY, normalZ));
        vertexPending = false;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    private static int packPair(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }
}
