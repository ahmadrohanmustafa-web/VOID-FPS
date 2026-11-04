package dev.voidfps.surface;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a model can be rendered by the surface path. The decision is made from the
 * quad geometry itself, not only from block flags, so a mislabelled block cannot slip through.
 * Milestone 1: opaque, untinted, solid-layer full cubes with at most one quad per face.
 */
public final class SurfaceEligibility {
    private static final float EPS = 1.0e-4f;

    private SurfaceEligibility() {
    }

    /** Either a plan (supported) or a rejection reason (vanilla must render the block). */
    public record Analysis(SurfacePlan plan, String rejectReason) {
        public boolean supported() {
            return plan != null;
        }
    }

    /**
     * Native-model gate for the narrow opaque full-cube candidate set. It checks native
     * block-state solidity, occlusion capability, block light emission, selected native
     * geometry, and native quad material metadata. It is still only an eligibility precheck:
     * block-entity exclusion and exact vanilla lighting/culling/output parity
     * must be established before any custom renderer is activated.
     */
    public record NativeGeometryAnalysis(boolean supported, String rejectReason) {
    }

    public static NativeGeometryAnalysis analyzeNativeModel(
            BlockState state, NativeModelSnapshot.Snapshot snapshot) {
        if (state == null) {
            return new NativeGeometryAnalysis(false, "block state is missing");
        }
        if (state.hasBlockEntity()) {
            return new NativeGeometryAnalysis(false, "block state declares a block entity");
        }
        if (!state.isSolidRender()) {
            return new NativeGeometryAnalysis(false, "block state is not a solid render state");
        }
        if (!state.canOcclude()) {
            return new NativeGeometryAnalysis(false, "block state cannot occlude neighbouring faces");
        }
        if (state.hasOffsetFunction()) {
            return new NativeGeometryAnalysis(false, "block state has a position-dependent model offset");
        }
        if (state.getLightEmission() != 0) {
            return new NativeGeometryAnalysis(false, "block state emits light");
        }
        if (snapshot == null || snapshot.isEmpty()) {
            return new NativeGeometryAnalysis(false, "native model has no selected parts");
        }
        if (!snapshot.hasExactlyOneUnitBoundaryQuadPerFace()) {
            return new NativeGeometryAnalysis(false,
                    "native model is not exactly one unit boundary quad per face");
        }
        if (!snapshot.hasOnlyPlainSolidUntintedMaterials()) {
            return new NativeGeometryAnalysis(false,
                    "native quad material is not plain solid, untinted, static, and non-emissive");
        }
        return new NativeGeometryAnalysis(true, null);
    }

    public static Analysis analyze(ModelInfo info) {
        if (!info.opaqueFullCube()) return reject("not an opaque full cube");
        if (info.hasBlockEntityRenderer()) return reject("block entity / custom renderer");
        if (!info.solidRenderLayer()) return reject("not in the solid render layer");
        if (info.quads().isEmpty()) return reject("no quads");

        SurfacePlan plan = new SurfacePlan(info.ambientOcclusionAllowed());
        boolean[] seen = new boolean[6];
        for (BakedQuadData q : info.quads()) {
            if (q.tintIndex() >= 0) return reject("tinted quad (milestone 1 excludes tint)");
            if (q.cullFace() == null) return reject("quad without cull face");
            if (q.lightFace() != q.cullFace()) return reject("light face differs from cull face");
            Face f = q.cullFace();
            if (seen[f.ordinal()]) return reject("more than one quad on face " + f);
            String geometry = checkUnitFaceSquare(q, f);
            if (geometry != null) return reject(geometry);
            seen[f.ordinal()] = true;
            plan.set(f, q);
        }
        return new Analysis(plan, null);
    }

    private static Analysis reject(String reason) {
        return new Analysis(null, reason);
    }

    /** Returns null if the quad is exactly the unit square lying on the cube's boundary plane of face f. */
    private static String checkUnitFaceSquare(BakedQuadData q, Face f) {
        float[] p = q.positions();
        float plane = f.sign() > 0 ? 1.0f : 0.0f;
        int a = f.tangentA(), b = f.tangentB();
        boolean[] corner = new boolean[4]; // index = (a>0.5 ? 1 : 0) | (b>0.5 ? 2 : 0)
        for (int v = 0; v < 4; v++) {
            float n = p[3 * v + f.axis()], ca = p[3 * v + a], cb = p[3 * v + b];
            if (!Float.isFinite(n) || !Float.isFinite(ca) || !Float.isFinite(cb)) return "non-finite vertex";
            if (Math.abs(n - plane) > EPS) return "vertex not on the cube boundary plane";
            if (!nearZeroOrOne(ca) || !nearZeroOrOne(cb)) return "quad does not span the full face";
            int idx = (ca > 0.5f ? 1 : 0) | (cb > 0.5f ? 2 : 0);
            if (corner[idx]) return "degenerate quad (repeated corner)";
            corner[idx] = true;
        }
        return null;
    }

    private static boolean nearZeroOrOne(float c) {
        return Math.abs(c) <= EPS || Math.abs(c - 1.0f) <= EPS;
    }
}
