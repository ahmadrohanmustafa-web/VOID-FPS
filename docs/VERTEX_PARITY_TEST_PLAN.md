# Runtime vertex-parity test plan

## Purpose

Prove that a narrowly eligible VOID FPS candidate block produces the same four packed vertex records as vanilla Minecraft 26.3 before any renderer interception is enabled.

The existing engine-independent `SelfTest` suite tests VOID FPS's own simplified data model. It is not a vanilla parity test and must not be presented as one.

## Comparison boundary

The comparison must happen at the native `VertexConsumer.addVertex(float, float, float, int, float, float, int, int, float, float, float)` boundary reached by `VertexConsumer.putBlockBakedQuad(...)`.

Capture, in order, all 11 arguments for each of the four vertices:

1. translated X, Y, Z;
2. packed vertex colour;
3. unpacked U and V;
4. overlay coordinates;
5. packed light after `getLightCoordsWithEmission(vertex, materialLightEmission)`;
6. normal X, Y, Z from the baked quad direction.

Compare all 44 scalar values (with exact equality for integer fields and a documented small tolerance only for floating-point fields). Also compare vertex count and order. Do not compare screenshots alone: they can hide subtle lighting, UV, or ordering differences.

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

## Verified client-test launch options (2026-11-05)

The current build uses Fabric Loom's non-remapping plugin for Minecraft 26.3. The official Loom documentation confirms that Loom supplies a `runClient` JavaExec task and supports additional run configurations whose source set can be `sourceSets.test`. This gives us a path to put test-only bootstrap code on a development-client classpath without adding test classes to the shipped JAR.

Fabric's automated-testing documentation also describes client game tests launched from a production run task under Xvfb on Linux. That route is built around Fabric's game-test facilities and documents a Fabric API runtime dependency. VOID FPS currently has no Fabric API dependency, so we must not silently add that dependency or assume its game-test annotations and lifecycle are available.

### Decision for the next implementation stage

Use a dedicated Loom development-client run configuration with the test source set, then verify that the Minecraft 26.3 client can bootstrap in CI under a virtual display. Keep all test bootstrap classes in `src/test`; do not add a production mixin, do not register the currently empty mixin config, and do not alter the shipped client entrypoint's behavior.

A client-start smoke test is only infrastructure validation. It is **not** vertex parity. The subsequent harness still needs a verified way to create/load a real test world and capture both vanilla and candidate output at the native `VertexConsumer.addVertex(...)` boundary. If that requires Fabric's game-test framework, isolate any API dependency to the test runtime and verify the exact 26.3 artifact before adding it.

### Current state

- GitHub Actions run `42020289401` on commit `e049dce051c5f9d35ce50e6850460deb50e0b491` passes `gradle build --no-daemon`, including the dependency-free engine-independent self-test suite.
- Loom's development-client and test-source-set launch mechanisms are documented.
- GitHub Actions run `42014818487` successfully launched the Minecraft 26.3 test client under Xvfb and observed the VOID FPS client entrypoint log.
- The latest verification run `42020289401` also passes the Minecraft 26.3 startup smoke test after the self-test classpath was returned to a dependency-free configuration.
- The CI startup smoke log is now included in the `void-fps-build` artifact for future diagnosis.
- The `NativeVertexRecorder` compiles as test code, but its packed-call exercise is not currently run by the standalone self-test because loading `VertexConsumer` there requires Loom's Minecraft runtime. No native vanilla/candidate vertex capture has yet run; startup success does not establish rendering parity.
- Renderer interception remains disabled.


## Latest CI verification (2026-11-05, run 42024280099)

- Commit `2657e08d5ef3cf9ad790e0946f574b75d4a6ef61` passed the Gradle build and Minecraft 26.3 client startup smoke test.
- The test-only client entrypoint ran and logged `VOID FPS native recorder test passed`.
- The test-only runtime exercise now constructs a deterministic native `BakedQuad` and `QuadInstance`, calls `VertexConsumer.putBlockBakedQuad(...)`, and compares the four captured records against expected positions, colors, UVs, overlay, light values, and face normal. This exercises Minecraft's actual packed-vertex default path. It still does **not** invoke vanilla model collection/lighting preparation or compare vanilla output against a candidate adapter.
- The headless Xvfb log includes OpenGL/Vulkan backend creation warnings after the required checks. Treat this as a CI graphics-backend limitation, not proof of successful rendered gameplay.
- The uploaded release JAR was inspected: it contains neither `NativeRecorderTestEntrypoint` nor the `voidfps-test` mod metadata. Test-only classes remain excluded from the shipped artifact.

### Immediate next implementation target

Run the new native `putBlockBakedQuad` fixture through CI and fix any constructor, runtime, or expected-value mismatch. Once it passes, expand the fixture with non-zero packed UVs and material light emission, then investigate a real loaded block model and native model/lighting preparation. This remains a narrow API-path test; it must not be described as real block-model or vanilla/candidate parity. Keep production rendering disabled.
