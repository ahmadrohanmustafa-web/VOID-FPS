package dev.voidfps.surface;

import java.util.List;

/**
 * Everything the eligibility check needs about one (block state, chosen model variant) pair.
 * The adapter fills this; flags marked in BLOCKED.md must be derived from real Minecraft queries.
 *
 * @param opaqueFullCube            block is an opaque, full-cube, occluding block
 * @param hasBlockEntityRenderer    block has a block entity renderer or custom per-block rendering
 * @param solidRenderLayer          model renders in the solid (non-cutout, non-translucent) layer
 * @param ambientOcclusionAllowed   model allows AO and block emits no light
 * @param quads                     all baked quads of the chosen variant (every part, every face)
 */
public record ModelInfo(boolean opaqueFullCube, boolean hasBlockEntityRenderer, boolean solidRenderLayer,
                        boolean ambientOcclusionAllowed, List<BakedQuadData> quads) {
}
