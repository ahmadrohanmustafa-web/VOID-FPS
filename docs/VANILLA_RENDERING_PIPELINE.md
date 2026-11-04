# Minecraft 26.3 vanilla block-rendering pipeline

Inspection source: GitHub Actions artifacts from runs 41822072496, 41827531435, 41828229080, and 41828660351, built against the Minecraft 26.3 client jar. The findings below are based on bytecode inspection; they are not a runtime equivalence test.

## Verified call flow

`SectionCompiler.compile(...)` invokes `ModelBlockRenderer.tesselateBlock(...)` for block models. The renderer:

1. Seeds its `RandomSource` with the supplied model seed.
2. Calls `BlockStateModel.collectParts(random, parts)`.
3. Returns when no parts were collected.
4. Applies `BlockState.getOffset(blockPos)` to the supplied block output origin.
5. Selects AO only when the renderer AO flag is enabled, the block has zero light emission, and the first collected model part reports `useAmbientOcclusion()`.
6. Clears its reusable part list and tint cache on both normal and exceptional exits.

## Face/model handling

Both AO and flat paths iterate every collected `BlockStateModelPart`. For each of the six directions they request `getQuads(direction)`; they separately request `getQuads(null)` for unculled quads.

The renderer tracks two direction bitmasks across model parts: directions already encountered and directions whose neighbour test passed. It avoids repeating neighbour culling for later parts on a direction after the first relevant quad list. When culling is enabled, the neighbour state is read from `BlockAndTintGetter` and the decision is delegated to `Block.shouldRenderFace(state, neighbour, direction)`.

This means a safe replacement cannot assume exactly one model part, exactly one quad per face, or that every quad is a cullable cube face. Unculled quads must remain supported by vanilla fallback unless the custom path explicitly implements them.

## Lighting and ambient occlusion

The AO path delegates per-quad preparation to `BlockModelLighter.prepareQuadAmbientOcclusion(world, state, pos, quad, quadInstance)`. The inspected implementation:

- derives adjacency information from the baked quad's own `direction()`;
- uses a face-relative base position when the face is cubic, otherwise uses the block position;
- samples adjacent block states around the quad and asks the lighter cache for packed light coordinates and shade brightness;
- prepares the four per-vertex colour/light values in the reusable `QuadInstance`.

The flat path obtains packed light through `BlockModelLighter.getLightCoords` at the neighbour position for directional quads, then calls `prepareQuadFlat(...)` for each quad. Unculled quads use the flat helper's sentinel light argument.

The current project-side `SurfaceLighting` implementation is explicitly an approximation and has not been proven equivalent to 26.3. It must not be used to replace vanilla AO/flat preparation.

### Native lighter reuse and cache lifecycle confirmed by bytecode inspection

The 26.3 `BlockModelLighter` class has a public constructor and public methods for `getLightCoords`, `prepareQuadAmbientOcclusion`, and `prepareQuadFlat`. It also exposes static `enableCaching()` and `clearCache()` methods.

The `SectionCompiler.compile` bytecode calls `BlockModelLighter.enableCaching()` near the start of section compilation and `BlockModelLighter.clearCache()` on its normal completion path. The constructor obtains the cache from the same static `ThreadLocal`. Thus, a separately constructed lighter on that same worker thread shares the native cache; it does not create a separate cache. The lighter instance itself also owns mutable scratch-position and face-shape state, so an instance must be thread-confined and must not be shared between concurrent section-compilation threads.

VOID FPS now has a thin, currently unused `NativeLightingAdapter` that delegates AO, flat preparation, and packed-light lookup to these native methods. It deliberately does not call `enableCaching()` or `clearCache()`; the enclosing vanilla section-compilation lifecycle owns those calls. It is not wired into a renderer and does not activate an optimization.

This establishes a source-level integration route, not runtime parity. The adapter must only be used inside the same valid section-compilation cache scope, and output still needs comparison against vanilla in a running 26.3 client.

## Tint and output

For each quad, vanilla reads `quad.materialInfo().tintIndex()`. If the tint index is not -1, it resolves the registered tint source for the current block state/world/position and applies it with `QuadInstance.multiplyColor(color)`. Tint source results are cached during the block render and the tint cache is reset afterwards.

Finally, `BlockQuadOutput.put(x, y, z, quad, quadInstance)` receives the baked quad and prepared instance. In `SectionCompiler`, one output lambda writes to the SOLID layer; the other selects the layer from `quad.materialInfo().layer()`. Both call `BufferBuilder.putBlockBakedQuad(...)`.

The native output path therefore carries baked vertex positions, packed UVs, material information, tint, and per-vertex light/colour state. A replacement must not flatten or discard those values.

## 26.3 face-occlusion and model-offset bytecode findings

The follow-up inspection artifact from workflow run 41827531435 confirms these details:

- `ModelBlockRenderer.tesselateBlock` calls `state.getOffset(blockPos)` after collecting model parts and before emitting geometry. `BlockStateBase.getOffset` returns `Vec3.ZERO` only when its offset function is absent; otherwise it evaluates that function with the state and position. VOID FPS therefore conservatively rejects states for which `hasOffsetFunction()` is true rather than silently dropping a positional transform.
- `ModelBlockRenderer.shouldRenderFace` obtains the neighbour state and delegates directly to `Block.shouldRenderFace(state, neighbour, direction)` when culling is enabled.
- `Block.shouldRenderFace` checks the neighbour's opposite face-occlusion shape against the singleton `Shapes.block()` first, then calls the current state's `skipRendering(neighbour, direction)`. Empty-shape fast paths follow; the remaining shape-pair calculation uses Minecraft's thread-local occlusion cache.
- `BlockStateBase.getFaceOcclusionShape(direction)` returns a precomputed per-direction shape. `canOcclude()` alone is not proof that all six faces are full-block occlusion faces. The native eligibility gate now requires identity with `Shapes.block()` on all six directions as a conservative candidate filter.

The eligibility check is not a replacement for runtime culling: an eventual renderer must still call vanilla's `Block.shouldRenderFace` for each relevant neighbour test and must preserve vanilla's culling configuration. Do not build a second face-visibility cache.

## VOID FPS implementation gates

The current project has no active mixin: `void_fps_surface.mixins.json` has empty arrays and is not registered in `fabric.mod.json`. The client entrypoint intentionally logs that no optimization is active.

Before intercepting vanilla rendering, the integration must:

1. Add and verify the actual 26.3 mixin configuration/dependency and confirm the exact target at runtime.
2. Build a native-model adapter that preserves model random seeding, all collected parts, directional and unculled quads, model offsets, material layer, packed UVs, and quad material flags.
3. Reuse vanilla neighbour-face culling semantics; do not add a duplicate face-visibility cache.
4. Reuse the native lighter and native tint-source resolution where possible. The current standalone lighting approximation is not sufficient.
5. Restrict interception to a narrow, positively verified subset. Every unsupported model/state must return to vanilla without partial emission or duplicate rendering.
6. Compare generated per-vertex output with vanilla in a runtime Minecraft 26.3 test before enabling the path by default.

A successful Gradle build or bytecode-inspection workflow is not proof that the mod loads in Minecraft, preserves visuals, or improves FPS. Benchmarking is a separate final gate.
