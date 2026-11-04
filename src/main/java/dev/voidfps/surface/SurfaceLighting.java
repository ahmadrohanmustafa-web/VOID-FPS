package dev.voidfps.surface;

/**
 * Smooth-lighting for axis-aligned cube faces, modelled on vanilla's cubic-face ambient occlusion.
 * VERIFY-AGAINST-VANILLA (26.3): corner fallback choice, zero-light substitution, and light packing.
 * Nothing here has been compared with Minecraft's own output yet.
 */
final class SurfaceLighting {
    private SurfaceLighting() {
    }

    /** 3x3 samples on the plane directly in front of the face, indexed [sa+1][sb+1]. */
    static final class Samples {
        final float[][] shade = new float[3][3];
        final int[][] light = new int[3][3];
        final boolean[][] translucent = new boolean[3][3];
    }

    static void sample(BlockView view, int x, int y, int z, Face face, Samples out) {
        int bx = x + face.dx, by = y + face.dy, bz = z + face.dz;
        int a = face.tangentA(), b = face.tangentB();
        for (int ia = -1; ia <= 1; ia++) {
            for (int ib = -1; ib <= 1; ib++) {
                int[] c = {bx, by, bz};
                c[a] += ia;
                c[b] += ib;
                out.shade[ia + 1][ib + 1] = view.shadeBrightness(c[0], c[1], c[2]);
                out.light[ia + 1][ib + 1] = view.packedLight(c[0], c[1], c[2]);
                out.translucent[ia + 1][ib + 1] = view.isLightTranslucent(c[0], c[1], c[2]);
            }
        }
    }

    /** Computes AO brightness and blended light for a vertex whose face-plane corner is (sa, sb), each -1 or +1. */
    static void vertex(Samples s, int sa, int sb, float[] aoOut, int[] lightOut) {
        int ia = sa + 1, ib = sb + 1;
        float centerShade = s.shade[1][1];
        int centerLight = s.light[1][1];

        float edgeAShade = s.shade[ia][1], edgeBShade = s.shade[1][ib];
        int edgeALight = s.light[ia][1], edgeBLight = s.light[1][ib];

        float cornerShade;
        int cornerLight;
        if (!s.translucent[ia][1] && !s.translucent[1][ib]) {
            cornerShade = edgeAShade;
            cornerLight = edgeALight;
        } else {
            cornerShade = s.shade[ia][ib];
            cornerLight = s.light[ia][ib];
        }
        aoOut[0] = (centerShade + edgeAShade + edgeBShade + cornerShade) * 0.25f;
        lightOut[0] = blend(edgeALight, edgeBLight, cornerLight, centerLight);
    }

    static int blend(int a, int b, int c, int center) {
        if (a == 0) a = center;
        if (b == 0) b = center;
        if (c == 0) c = center;
        int lo = ((a & 0xFFFF) + (b & 0xFFFF) + (c & 0xFFFF) + (center & 0xFFFF)) >> 2;
        int hi = ((a >>> 16) + (b >>> 16) + (c >>> 16) + (center >>> 16)) >> 2;
        return (hi << 16) | lo;
    }
}
