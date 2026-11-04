package dev.voidfps.surface;

/** Receives generated vertices, four per quad, in the source quad's vertex order. */
public interface SurfaceSink {
    void vertex(Object sprite, float x, float y, float z, float u, float v,
                float r, float g, float b, int packedLight);
}
