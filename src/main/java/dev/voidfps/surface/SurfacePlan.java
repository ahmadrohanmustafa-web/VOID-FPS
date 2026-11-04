package dev.voidfps.surface;

/** Immutable, cacheable result of a successful eligibility check. One per (state, model variant). */
public final class SurfacePlan {
    private final BakedQuadData[] byFace = new BakedQuadData[6];
    /** Per face, per vertex: -1 or +1 along the face's tangent A / B axis. */
    final int[][] signA = new int[6][4];
    final int[][] signB = new int[6][4];
    final boolean aoAllowed;

    SurfacePlan(boolean aoAllowed) {
        this.aoAllowed = aoAllowed;
    }

    void set(Face face, BakedQuadData quad) {
        byFace[face.ordinal()] = quad;
        for (int v = 0; v < 4; v++) {
            signA[face.ordinal()][v] = quad.positions()[3 * v + face.tangentA()] > 0.5f ? 1 : -1;
            signB[face.ordinal()][v] = quad.positions()[3 * v + face.tangentB()] > 0.5f ? 1 : -1;
        }
    }

    public BakedQuadData quad(Face face) {
        return byFace[face.ordinal()];
    }
}
