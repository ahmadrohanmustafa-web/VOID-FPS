package dev.voidfps.surface;

import java.util.List;

/**
 * Ordered comparison for captured native block-vertex records.
 *
 * <p>A successful result only means the supplied records match. The caller is responsible for
 * obtaining both lists from the real Minecraft 26.3 vanilla and candidate output paths.</p>
 */
public final class NativeVertexParity {
    private NativeVertexParity() {
    }

    public record Result(boolean matches, int comparedVertices, String difference) {
    }

    public static Result compare(
            List<NativeVertexRecord> vanilla,
            List<NativeVertexRecord> candidate,
            float floatTolerance) {
        if (vanilla == null || candidate == null) {
            return new Result(false, 0, "vertex capture is missing");
        }
        if (floatTolerance < 0.0f || !Float.isFinite(floatTolerance)) {
            return new Result(false, 0, "invalid floating-point tolerance");
        }
        if (vanilla.size() != candidate.size()) {
            return new Result(false, Math.min(vanilla.size(), candidate.size()),
                    "vertex count differs: vanilla=" + vanilla.size()
                            + ", candidate=" + candidate.size());
        }

        for (int i = 0; i < vanilla.size(); i++) {
            NativeVertexRecord expected = vanilla.get(i);
            NativeVertexRecord actual = candidate.get(i);
            if (expected == null || actual == null) {
                return new Result(false, i, "vertex " + i + " is missing: vanilla="
                        + (expected == null ? "null" : "present") + ", candidate="
                        + (actual == null ? "null" : "present"));
            }
            if (!expected.matches(actual, floatTolerance)) {
                return new Result(false, i,
                        "vertex " + i + " differs: vanilla=" + expected + ", candidate=" + actual);
            }
        }
        return new Result(true, vanilla.size(), null);
    }
}
