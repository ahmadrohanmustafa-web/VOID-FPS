package dev.voidfps.surface;

/**
 * Immutable snapshot of one call to the packed VertexConsumer.addVertex overload used by
 * Minecraft 26.3's block-quad output path.
 *
 * <p>This is a comparison data type only. It does not capture Minecraft rendering calls and
 * must not be described as a runtime parity test by itself.</p>
 */
public record NativeVertexRecord(
        float x, float y, float z,
        int color,
        float u, float v,
        int overlay,
        int light,
        float normalX, float normalY, float normalZ) {

    public boolean matches(NativeVertexRecord other, float floatTolerance) {
        if (other == null || floatTolerance < 0.0f || !Float.isFinite(floatTolerance)) {
            return false;
        }
        return near(x, other.x, floatTolerance)
                && near(y, other.y, floatTolerance)
                && near(z, other.z, floatTolerance)
                && color == other.color
                && near(u, other.u, floatTolerance)
                && near(v, other.v, floatTolerance)
                && overlay == other.overlay
                && light == other.light
                && near(normalX, other.normalX, floatTolerance)
                && near(normalY, other.normalY, floatTolerance)
                && near(normalZ, other.normalZ, floatTolerance);
    }

    private static boolean near(float a, float b, float tolerance) {
        return Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) <= tolerance;
    }
}
