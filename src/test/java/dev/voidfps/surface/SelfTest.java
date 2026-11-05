package dev.voidfps.surface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-free tests for the engine-independent core. These specify THIS library's behaviour;
 * they do not prove equality with Minecraft's renderer (see BLOCKED.md).
 */
public final class SelfTest {
    private static int failures = 0, checks = 0;

    static void check(String name, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + name);
        }
    }

    static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }

    // ---- fixtures -------------------------------------------------------------------------

    static final class TestView implements BlockView {
        final Map<Long, Boolean> opaque = new HashMap<>();
        int ambientLight = (15 << 16) | 15;

        static long key(int x, int y, int z) {
            return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }

        void solid(int x, int y, int z) {
            opaque.put(key(x, y, z), true);
        }

        boolean isSolid(int x, int y, int z) {
            return opaque.getOrDefault(key(x, y, z), false);
        }

        public boolean coversFace(int x, int y, int z, Face s) {
            return isSolid(x, y, z);
        }

        public int packedLight(int x, int y, int z) {
            return isSolid(x, y, z) ? 0 : ambientLight;
        }

        public float shadeBrightness(int x, int y, int z) {
            return isSolid(x, y, z) ? 0.2f : 1.0f;
        }

        public boolean isLightTranslucent(int x, int y, int z) {
            return !isSolid(x, y, z);
        }

        public float directionalShade(Face f) {
            return switch (f) {
                case DOWN -> 0.5f;
                case UP -> 1.0f;
                case NORTH, SOUTH -> 0.8f;
                case WEST, EAST -> 0.6f;
            };
        }
    }

    static final class RecordingSink implements SurfaceSink {
        final List<float[]> verts = new ArrayList<>(); // x y z u v r g b light
        final List<Object> sprites = new ArrayList<>();

        public void vertex(Object sprite, float x, float y, float z, float u, float v,
                           float r, float g, float b, int light) {
            verts.add(new float[]{x, y, z, u, v, r, g, b, light});
            sprites.add(sprite);
        }
    }

    /** Unit-square quad on face f, vertices in a fixed non-trivial order, distinctive UVs. */
    static BakedQuadData faceQuad(Face f, Object sprite) {
        int a = f.tangentA(), b = f.tangentB(), n = f.axis();
        float plane = f.sign() > 0 ? 1 : 0;
        float[][] ab = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        float[] pos = new float[12];
        float[] uv = new float[8];
        for (int v = 0; v < 4; v++) {
            pos[3 * v + n] = plane;
            pos[3 * v + a] = ab[v][0];
            pos[3 * v + b] = ab[v][1];
            uv[2 * v] = 0.25f * (f.ordinal() + 1) + 0.01f * v;
            uv[2 * v + 1] = 0.5f + 0.02f * v;
        }
        return new BakedQuadData(f, f, -1, true, pos, uv, sprite);
    }

    static List<BakedQuadData> cubeQuads(Object sprite) {
        List<BakedQuadData> l = new ArrayList<>();
        for (Face f : Face.VALUES) l.add(faceQuad(f, sprite));
        return l;
    }

    static ModelInfo cube(List<BakedQuadData> q) {
        return new ModelInfo(true, false, true, true, q);
    }

    static SurfacePlan planOf(ModelInfo m) {
        SurfaceEligibility.Analysis a = SurfaceEligibility.analyze(m);
        if (!a.supported()) throw new IllegalStateException(a.rejectReason());
        return a.plan();
    }

    static BakedQuadData with(BakedQuadData q, Face light, Face cull, int tint, float[] pos) {
        return new BakedQuadData(light, cull, tint, q.shade(), pos != null ? pos : q.positions(), q.uvs(), q.sprite());
    }

    // ---- tests ----------------------------------------------------------------------------

    static void eligibility() {
        check("plain cube accepted", SurfaceEligibility.analyze(cube(cubeQuads("stone"))).supported());

        check("non-opaque rejected",
                !SurfaceEligibility.analyze(new ModelInfo(false, false, true, true, cubeQuads("s"))).supported());
        check("block entity rejected",
                !SurfaceEligibility.analyze(new ModelInfo(true, true, true, true, cubeQuads("s"))).supported());
        check("non-solid layer rejected",
                !SurfaceEligibility.analyze(new ModelInfo(true, false, false, true, cubeQuads("s"))).supported());
        check("empty rejected", !SurfaceEligibility.analyze(cube(List.of())).supported());

        List<BakedQuadData> tinted = cubeQuads("g");
        tinted.set(1, with(tinted.get(1), Face.UP, Face.UP, 0, null));
        check("tinted rejected", !SurfaceEligibility.analyze(cube(tinted)).supported());

        List<BakedQuadData> noCull = cubeQuads("g");
        noCull.set(2, with(noCull.get(2), Face.NORTH, null, -1, null));
        check("no cullface rejected", !SurfaceEligibility.analyze(cube(noCull)).supported());

        List<BakedQuadData> dup = cubeQuads("g");
        dup.add(faceQuad(Face.UP, "overlay"));
        check("overlay (2 quads on a face) rejected", !SurfaceEligibility.analyze(cube(dup)).supported());

        List<BakedQuadData> shrunk = cubeQuads("g");
        float[] p = shrunk.get(1).positions();
        p[0] = 0.0625f;
        shrunk.set(1, with(shrunk.get(1), Face.UP, Face.UP, -1, p));
        check("inset element rejected", !SurfaceEligibility.analyze(cube(shrunk)).supported());

        List<BakedQuadData> inner = cubeQuads("g");
        float[] q2 = inner.get(1).positions();
        for (int v = 0; v < 4; v++) q2[3 * v + 1] = 0.9f;
        inner.set(1, with(inner.get(1), Face.UP, Face.UP, -1, q2));
        check("off-boundary plane rejected", !SurfaceEligibility.analyze(cube(inner)).supported());

        List<BakedQuadData> degenerate = cubeQuads("g");
        float[] d = degenerate.get(0).positions();
        d[3] = d[0];
        d[4] = d[1];
        d[5] = d[2];
        degenerate.set(0, with(degenerate.get(0), Face.DOWN, Face.DOWN, -1, d));
        check("degenerate quad rejected", !SurfaceEligibility.analyze(cube(degenerate)).supported());

        List<BakedQuadData> mismatched = cubeQuads("g");
        mismatched.set(1, with(mismatched.get(1), Face.NORTH, Face.UP, -1, null));
        check("lightFace != cullFace rejected", !SurfaceEligibility.analyze(cube(mismatched)).supported());

        List<BakedQuadData> partial = cubeQuads("g");
        partial.remove(0);
        check("cube missing a face still accepted", SurfaceEligibility.analyze(cube(partial)).supported());
    }

    static void culling() {
        SurfacePlan plan = planOf(cube(cubeQuads("stone")));

        TestView air = new TestView();
        RecordingSink s1 = new RecordingSink();
        check("isolated block emits 6 quads", SurfaceCompiler.emit(plan, air, 0, 0, 0, 0, 0, 0, true, s1) == 6);
        check("isolated block emits 24 vertices", s1.verts.size() == 24);

        TestView buried = new TestView();
        for (Face f : Face.VALUES) buried.solid(f.dx, f.dy, f.dz);
        RecordingSink s2 = new RecordingSink();
        check("fully buried block emits 0 quads", SurfaceCompiler.emit(plan, buried, 0, 0, 0, 0, 0, 0, true, s2) == 0);

        TestView capped = new TestView();
        capped.solid(0, 1, 0);
        RecordingSink s3 = new RecordingSink();
        check("block under a neighbour emits 5", SurfaceCompiler.emit(plan, capped, 0, 0, 0, 0, 0, 0, true, s3) == 5);
        boolean anyTop = false;
        for (int i = 0; i < s3.verts.size(); i += 4) {
            boolean allTop = true;
            for (int k = 0; k < 4; k++) allTop &= near(s3.verts.get(i + k)[1], 1.0f);
            anyTop |= allTop;
        }
        check("no top quad when covered", !anyTop);
    }

    static void geometryAndUvs() {
        SurfacePlan plan = planOf(cube(cubeQuads("stone")));
        TestView air = new TestView();
        RecordingSink s = new RecordingSink();
        SurfaceCompiler.emit(plan, air, 5, 6, 7, 100f, 200f, 300f, false, s);

        boolean offsetOk = true, uvOk = true, spriteOk = true;
        int quad = 0;
        for (Face f : Face.VALUES) {
            BakedQuadData src = plan.quad(f);
            for (int v = 0; v < 4; v++) {
                float[] out = s.verts.get(quad * 4 + v);
                offsetOk &= near(out[0], 100f + src.positions()[3 * v]) && near(out[1], 200f + src.positions()[3 * v + 1])
                        && near(out[2], 300f + src.positions()[3 * v + 2]);
                uvOk &= near(out[3], src.uvs()[2 * v]) && near(out[4], src.uvs()[2 * v + 1]);
                spriteOk &= s.sprites.get(quad * 4 + v) == src.sprite();
            }
            quad++;
        }
        check("positions = origin + source vertex, source order kept", offsetOk);
        check("UVs pass through unchanged and in order", uvOk);
        check("sprite handle passed through", spriteOk);
    }

    static void flatLighting() {
        SurfacePlan plan = planOf(cube(cubeQuads("stone")));
        TestView v = new TestView();
        v.ambientLight = (7 << 16) | 3;
        RecordingSink s = new RecordingSink();
        SurfaceCompiler.emit(plan, v, 0, 0, 0, 0, 0, 0, false, s);
        boolean ok = true;
        int i = 0;
        for (Face f : Face.VALUES) {
            for (int k = 0; k < 4; k++, i++) {
                float[] o = s.verts.get(i);
                ok &= near(o[5], v.directionalShade(f)) && (int) o[8] == v.ambientLight;
            }
        }
        check("flat lighting = directional shade, neighbour light", ok);

        // AO disabled globally OR by model must give the same flat result.
        SurfacePlan noAoModel = planOf(new ModelInfo(true, false, true, false, cubeQuads("stone")));
        RecordingSink s2 = new RecordingSink();
        SurfaceCompiler.emit(noAoModel, v, 0, 0, 0, 0, 0, 0, true, s2);
        boolean same = s.verts.size() == s2.verts.size();
        for (int k = 0; same && k < s.verts.size(); k++) same &= java.util.Arrays.equals(s.verts.get(k), s2.verts.get(k));
        check("model with AO disallowed renders flat", same);
    }

    /** Collect per-vertex colour of the UP face keyed by (x>0.5, z>0.5). */
    static float[][] topColours(TestView view) {
        SurfacePlan plan = planOf(cube(cubeQuads("stone")));
        RecordingSink s = new RecordingSink();
        SurfaceCompiler.emit(plan, view, 0, 0, 0, 0, 0, 0, true, s);
        float[][] out = new float[2][2];
        for (int i = 0; i < s.verts.size(); i += 4) {
            boolean top = true;
            for (int k = 0; k < 4; k++) top &= near(s.verts.get(i + k)[1], 1.0f);
            if (top) {
                for (int k = 0; k < 4; k++) {
                    float[] v = s.verts.get(i + k);
                    out[v[0] > 0.5f ? 1 : 0][v[2] > 0.5f ? 1 : 0] = v[5];
                }
            }
        }
        return out;
    }

    static void ambientOcclusion() {
        TestView open = new TestView();
        float[][] c0 = topColours(open);
        check("open top face: all corners = 1.0 * top shade",
                near(c0[0][0], 1f) && near(c0[0][1], 1f) && near(c0[1][0], 1f) && near(c0[1][1], 1f));

        // Occluder in front of the +X edge of the top face (at block (1,1,0)).
        TestView edge = new TestView();
        edge.solid(1, 1, 0);
        float[][] c1 = topColours(edge);
        // Vertices on the +X side: edge=0.2, other edge=1, corner sampled=1, centre=1 -> 0.8
        check("edge occluder darkens +X vertices to 0.8", near(c1[1][0], 0.8f) && near(c1[1][1], 0.8f));
        check("edge occluder leaves -X vertices at 1.0", near(c1[0][0], 1f) && near(c1[0][1], 1f));

        // Occluders at +X and +Z edges: the (+X,+Z) corner falls back to an edge value.
        TestView two = new TestView();
        two.solid(1, 1, 0);
        two.solid(0, 1, 1);
        float[][] c2 = topColours(two);
        check("double occluder: shared corner = (1+.2+.2+.2)/4 = 0.4", near(c2[1][1], 0.4f));

        // Light blend: zero-light samples are replaced by the centre light.
        check("blend replaces zero samples with centre",
                SurfaceLighting.blend(0, 0, 0, (8 << 16) | 8) == ((8 << 16) | 8));
        check("blend averages the four samples per field",
                SurfaceLighting.blend((4 << 16) | 0, (8 << 16) | 4, (12 << 16) | 8, (0 << 16) | 12) == ((6 << 16) | 6));
    }


    static void nativeVertexComparison() {
        NativeVertexRecord a = new NativeVertexRecord(
                1, 2, 3, 0xFFFFFFFF, 0.25f, 0.75f, 4, 5, 0, 1, 0);
        NativeVertexRecord b = new NativeVertexRecord(
                1, 2, 3, 0xFFFFFFFF, 0.25f, 0.75f, 4, 5, 0, 1, 0);
        NativeVertexRecord close = new NativeVertexRecord(
                1.00001f, 2, 3, 0xFFFFFFFF, 0.25f, 0.75f, 4, 5, 0, 1, 0);
        NativeVertexRecord wrongColor = new NativeVertexRecord(
                1, 2, 3, 0xFFFEFFFF, 0.25f, 0.75f, 4, 5, 0, 1, 0);

        check("native vertex exact records match",
                NativeVertexParity.compare(List.of(a), List.of(b), 0).matches());
        check("native vertex float tolerance applies only to floats",
                NativeVertexParity.compare(List.of(a), List.of(close), 0.00002f).matches());
        check("native packed colour mismatch is detected",
                !NativeVertexParity.compare(List.of(a), List.of(wrongColor), 0.00002f).matches());
        check("native vertex count mismatch is detected",
                !NativeVertexParity.compare(List.of(a, b), List.of(b), 0).matches());
        check("native vertex order mismatch is detected at equal count",
                !NativeVertexParity.compare(List.of(a, wrongColor), List.of(wrongColor, a), 0).matches());
        check("native vertex null capture is rejected",
                !NativeVertexParity.compare(null, List.of(a), 0).matches());
        check("native vertex invalid tolerance is rejected",
                !NativeVertexParity.compare(List.of(a), List.of(a), Float.NaN).matches());
        check("native vertex NaN is rejected",
                !NativeVertexParity.compare(
                        List.of(new NativeVertexRecord(Float.NaN, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                        List.of(new NativeVertexRecord(Float.NaN, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                        0).matches());
    }

    static void nativeVertexRecorder() {
        NativeVertexRecorder recorder = new NativeVertexRecorder();
        NativeVertexRecord expected = new NativeVertexRecord(
                1.25f, 2.5f, 3.75f, 0xA1B2C3D4, 0.125f, 0.875f,
                0x12345678, 0x23456789, 0.0f, 1.0f, 0.0f);

        // Exercise the same packed 11-argument default method used by putBlockBakedQuad.
        recorder.addVertex(expected.x(), expected.y(), expected.z(),
                expected.color(), expected.u(), expected.v(), expected.overlay(), expected.light(),
                expected.normalX(), expected.normalY(), expected.normalZ());

        List<NativeVertexRecord> captured = recorder.snapshot();
        check("native VertexConsumer recorder captures one packed vertex",
                captured.size() == 1);
        check("native VertexConsumer recorder preserves all 11 values",
                NativeVertexParity.compare(List.of(expected), captured, 0).matches());

        recorder.clear();
        check("native VertexConsumer recorder clear resets capture",
                recorder.snapshot().isEmpty());
    }

    public static void main(String[] args) {
        eligibility();
        culling();
        geometryAndUvs();
        flatLighting();
        ambientOcclusion();
        nativeVertexComparison();
        nativeVertexRecorder();
        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
