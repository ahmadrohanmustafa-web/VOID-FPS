# Minecraft 26.3 rendering investigation

Research date: 2026-11-03. This document records the actual Minecraft 26.3 bytecode inspected by the GitHub Actions workflow, rather than assumptions based only on third-party primers.

## Verified face-occlusion path

The inspection artifact contains `Block.bytecode.txt`, `ModelBlockRenderer.bytecode.txt`, `SectionCompiler.bytecode.txt`, and `signatures.txt`.

Observed in Minecraft 26.3:

- `net.minecraft.world.level.block.Block` declares a thread-local `Object2ByteLinkedOpenHashMap<Block.ShapePairKey>` named `OCCLUSION_CACHE`.
- `Block.shouldRenderFace(BlockState, BlockState, Direction)` obtains the two face-occlusion shapes, checks the thread-local cache, and computes the shape join only on a cache miss.
- The cache removes the least-recently-used entry when its size reaches 256, then inserts the result.
- `net.minecraft.client.renderer.block.ModelBlockRenderer.shouldRenderFace(...)` obtains the neighboring block state and delegates to `Block.shouldRenderFace(...)`.

**Conclusion:** a second VOID FPS cache for block-face visibility would duplicate vanilla's existing cache. It is not implemented.

## Existing renderer behavior observed

- `ModelBlockRenderer` exposes `tesselateBlock`, flat/AO tessellation helpers, a face-culling flag, and tint-cache fields/methods.
- Its face-visibility method delegates to the vanilla `Block.shouldRenderFace` cache described above.
- `SectionCompiler` is the section mesh compilation path and is present in the inspection output.

These observations alone do not prove a safe alternative optimization. No change to tessellation, model part iteration, tinting, lighting, AO, or section compilation has been justified by the current evidence.

## Decision

No rendering mixins or patches are enabled. The safe decision is to keep this build behavior-neutral rather than add an unproven optimization that could duplicate work, alter visible geometry, or crash Minecraft.

## Acceptance criteria for future work

A future optimization must:
1. Be based on the exact Minecraft 26.3 bytecode/source and method descriptor.
2. Avoid duplicating vanilla caches or existing culling.
3. Preserve all visible faces, model variants, textures, tint, lighting, and ambient occlusion.
4. Be thread-safe under parallel section compilation.
5. Pass an A/B geometry-equivalence test across full cubes, glass, leaves, slabs, stairs, fences, walls, plants, and multipart models.
6. Show a repeatable CPU-side gain on real hardware. Otherwise it must be removed.

## Remaining validation

The JAR has compiled in CI, but the game has not been launched with it. Runtime compatibility, visual equivalence, and FPS impact remain unverified.
