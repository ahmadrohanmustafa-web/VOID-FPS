package dev.voidfps.surface;

/**
 * Emits the exposed faces of one supported block. Usage contract for the chunk-compile hook:
 * <pre>
 *   plan = cache.get(state/variant)           // SurfaceEligibility.analyze, cached
 *   if (plan == null) -> let vanilla render   // unsupported
 *   else SurfaceCompiler.emit(...)            // handled: vanilla MUST NOT render this block,
 *                                             // even when emit() returns 0 (fully hidden block)
 * </pre>
 * emit() cannot fail part-way: all validation happens in SurfaceEligibility, so a block is never
 * half-emitted and then rendered again by vanilla.
 */
public final class SurfaceCompiler {
    private SurfaceCompiler() {
    }

    /**
     * @param x,y,z     absolute block position (for world queries)
     * @param ox,oy,oz  output-space origin of the block (e.g. section-local coordinates)
     * @return number of quads emitted
     */
    public static int emit(SurfacePlan plan, BlockView view, int x, int y, int z,
                           float ox, float oy, float oz, boolean ambientOcclusion, SurfaceSink sink) {
        boolean smooth = ambientOcclusion && plan.aoAllowed;
        SurfaceLighting.Samples samples = smooth ? new SurfaceLighting.Samples() : null;
        float[] ao = new float[1];
        int[] lt = new int[1];
        int emitted = 0;

        for (Face f : Face.VALUES) {
            BakedQuadData q = plan.quad(f);
            if (q == null) continue;
            if (view.coversFace(x + f.dx, y + f.dy, z + f.dz, f.opposite())) continue;

            float dirShade = q.shade() ? view.directionalShade(q.lightFace()) : 1.0f;
            float[] p = q.positions(), uv = q.uvs();
            int flatLight = 0;
            if (smooth) {
                SurfaceLighting.sample(view, x, y, z, f, samples);
            } else {
                flatLight = view.packedLight(x + f.dx, y + f.dy, z + f.dz);
            }

            for (int v = 0; v < 4; v++) {
                float c;
                int light;
                if (smooth) {
                    SurfaceLighting.vertex(samples, plan.signA[f.ordinal()][v], plan.signB[f.ordinal()][v], ao, lt);
                    c = ao[0] * dirShade;
                    light = lt[0];
                } else {
                    c = dirShade;
                    light = flatLight;
                }
                sink.vertex(q.sprite(), ox + p[3 * v], oy + p[3 * v + 1], oz + p[3 * v + 2],
                        uv[2 * v], uv[2 * v + 1], c, c, c, light);
            }
            emitted++;
        }
        return emitted;
    }
}
