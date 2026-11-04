package dev.voidfps.surface;

/**
 * Plain-data copy of one baked-model quad, filled in by the Minecraft adapter.
 * Positions are block-local (0..1). Vertex order and winding are exactly those of the source quad
 * and are never reordered by this library.
 *
 * @param lightFace the face used for directional shading
 * @param cullFace  the face this quad is culled against, or null if it is never culled
 * @param sprite    opaque handle (e.g. the baked quad's sprite); passed through to the sink untouched
 */
public record BakedQuadData(Face lightFace, Face cullFace, int tintIndex, boolean shade,
                            float[] positions, float[] uvs, Object sprite) {
    public BakedQuadData {
        if (positions.length != 12) throw new IllegalArgumentException("positions must hold 4 xyz triples");
        if (uvs.length != 8) throw new IllegalArgumentException("uvs must hold 4 uv pairs");
        positions = positions.clone();
        uvs = uvs.clone();
    }
}
