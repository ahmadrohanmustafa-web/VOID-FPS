# Runtime vertex-parity test plan

## Purpose

Prove that a narrowly eligible VOID FPS candidate block produces the same four packed vertex records as vanilla Minecraft 26.3 before any renderer interception is enabled.

The existing engine-independent `SelfTest` suite tests VOID FPS's own simplified data model. It is not a vanilla parity test and must not be presented as one.

## Comparison boundary

The comparison must happen at the native `VertexConsumer.addVertex(float, float, float, int, float, float, int, int, float, float, float)` boundary reached by `VertexConsumer.putBlockBakedQuad(...)`.

Capture, in order, all 12 arguments for each of the four vertices:

1. translated X, Y, Z;
2. packed vertex colour;
3. unpacked U and V;
4. overlay coordinates;
5. packed light after `getLightCoordsWithEmission(vertex, materialLightEmission)`;
6. normal X, Y, Z from the baked quad direction.

Compare all 48 scalar values (with exact equality for integer fields and a documented small tolerance only for floating-point fields). Also compare vertex count and order. Do not compare screenshots alone: they can hide subtle lighting, UV, or ordering differences.

## Test execution design

This must run inside a real Minecraft 26.3 client runtime, after client bootstrap and resource/model loading. It must not run as a plain unit test that constructs fake Minecraft world or rendering objects.

For each test fixture:

1. Obtain a real block state, loaded world/region, block position, selected native model, and deterministic model seed.
2. Invoke the vanilla model collection and lighting/tint preparation flow with the same state, world, position, seed, culling setting, and native section-compilation cache scope used by vanilla.
3. Capture the vanilla output by supplying a test-only recording `VertexConsumer` to the native `putBlockBakedQuad` output boundary, or by a narrowly scoped test hook at the existing `BlockQuadOutput` callback.
4. Run the candidate adapter against the same original `BakedQuad` objects and prepared `QuadInstance` data, then capture through the same recorder.
5. Compare the ordered vertex records and fail the test on any mismatch.
6. Verify that a rejected candidate emits no candidate vertices and is left entirely to vanilla. Never allow partial candidate output followed by vanilla fallback, which would duplicate geometry.

The test hook must be test-only or explicitly disabled in normal builds. It must not register a production mixin or activate an optimization.

## Required fixture coverage

- Isolated full opaque stone-like cube.
- Same cube with each of the six neighbour faces independently covered and with all faces covered, checking native culling.
- Different block positions and non-zero section-local/world origins.
- Ambient occlusion enabled and disabled by the native renderer/model conditions.
- Flat-lighting path.
- Distinctive packed UVs and vertex order to detect accidental reordering.
- Multiple deterministic model seeds; if model selection changes, compare each vanilla-selected snapshot with its corresponding candidate pass.
- Unsupported controls: tinted quad, animated material, emissive quad/state, non-solid layer, block entity, offset state, partial occlusion shape, unculled quad, and non-cube model. Each must reject before candidate emission and render through vanilla only.

## Important isolation rules

- Use the original native baked quad and the same prepared `QuadInstance`; do not synthesize a cube or reconstruct per-vertex data.
- Reuse `BlockModelLighter` only inside the active vanilla section-compilation cache lifecycle. Keep each lighter instance thread-confined.
- Preserve native tint preparation when it is present; the first candidate subset currently rejects tinted quads.
- Delegate face visibility to `Block.shouldRenderFace`; do not introduce a second occlusion cache.
- Keep layer routing and fallback ownership at the section compiler's native output boundary.
- Run on the render/section worker context expected by the native APIs; do not move mutable renderer objects across threads.

## Pass criteria and release gate

The parity harness is considered valid only when it executes inside an actual 26.3 client and records both sides of the same native output boundary. A passing build, bytecode inspection, source-level adapter test, or screenshot is not sufficient.

The renderer remains disabled until:
- every supported fixture has identical ordered vertex records;
- every unsupported fixture is rejected before any candidate output;
- the vanilla fallback path is observed to emit exactly once;
- the test can be repeated deterministically.

Only after parity passes should a separate integration milestone investigate a disabled-by-default interception point. FPS benchmarking is later still and must compare the same scene, hardware, settings, and workload.
