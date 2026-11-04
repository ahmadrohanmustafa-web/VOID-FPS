package dev.voidfps.surface;

/**
 * World queries needed for culling and lighting. All coordinates are absolute block coordinates
 * (or any consistent integer space). Implemented by the Minecraft adapter.
 */
public interface BlockView {
    /**
     * True if the block at (x,y,z) fully covers the side of that block named by {@code sideTouchingUs},
     * i.e. a face lying against it is invisible. Must treat unloaded neighbours the way vanilla does.
     */
    boolean coversFace(int x, int y, int z, Face sideTouchingUs);

    /** Packed light coordinates for the block at (x,y,z): sky light in the high 16 bits, block light in the low 16. */
    int packedLight(int x, int y, int z);

    /** Ambient-occlusion brightness of the block at (x,y,z): 1.0 for open blocks, lower for occluding ones. */
    float shadeBrightness(int x, int y, int z);

    /** True if light passes through the block at (x,y,z) (vanilla: light block value of 0). */
    boolean isLightTranslucent(int x, int y, int z);

    /** Directional face brightness for the current dimension (e.g. top brighter than sides). */
    float directionalShade(Face face);
}
