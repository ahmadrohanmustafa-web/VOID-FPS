# VOID FPS

Client-side Fabric project for investigating CPU-side block and terrain rendering work in Minecraft Java Edition **26.3**.

> **Current verdict: no additional optimization is enabled.** Minecraft 26.3 already performs face-occlusion caching. VOID FPS does not add a duplicate cache or replace vanilla rendering. No candidate has met the evidence and benchmark requirements below.

## Verified project status

- GitHub Actions builds the project with Java 25 and Gradle 9.6.0.
- The build produces a client JAR and a source JAR.
- The client entrypoint only logs a status message. It does not modify rendering.
- The inspection artifact includes signatures and bytecode for `Block`, `ModelBlockRenderer`, and `SectionCompiler`.
- Vanilla `Block.shouldRenderFace` uses a thread-local, 256-entry LRU-style cache keyed by pairs of face-occlusion shapes. `ModelBlockRenderer.shouldRenderFace` delegates to this method.
- The real Minecraft 26.3 client smoke test loads a test world, exercises native vertex-output parity fixtures, and disconnects through the normal save lifecycle.
- A real temporary folder resource pack is activated through Minecraft's `PackRepository` and resource reload path. The test snapshots the loaded block model, then restores the original selected packs and removes its temporary pack.
- The live resource-pack compatibility tests confirm:
  - A normal six-face full-cube override remains eligible.
  - Partial-height and enclosed/internal geometry require vanilla fallback.
  - Unculled and duplicate-boundary quads require vanilla fallback.
  - Tint-index material requires vanilla fallback.
  - Rotating UV coordinates alone remains eligible.
  - A 22.5-degree element geometry rotation fails exact cube-boundary validation and requires vanilla fallback.
- Builds #318 and #319 passed. #318 contains the runtime rotated-element marker; #319 adds and passes the mandatory CI assertion for that marker.
- The inspection confirms the real rendering flow: `SectionCompiler.compile` calls `ModelBlockRenderer.tesselateBlock`; that method selects ambient-occlusion or flat tessellation, which checks directional faces and forwards accepted baked quads to `BlockQuadOutput`. `SectionCompiler` then routes those quads to native `BufferBuilder.putBlockBakedQuad`.
- No production mixin target has been activated. The mixin configuration remains empty.

## Scope

Only block and terrain rendering. Out of scope: entities, particles, HUD/overlays, keybinds, automatic setting changes, chunk scheduling, startup/GUI optimization, gameplay, and anything that lowers visual quality.

## Why no optimization was added

The obvious proposal—caching repeated block-face visibility checks—is already implemented by Minecraft 26.3. Reimplementing it in VOID FPS would add another lookup/cache and could make performance or memory use worse.

The resource-pack tests establish a conservative eligibility boundary, not a performance gain. The next engineering requirement is to identify a genuinely redundant operation in the verified section-compilation path, then prove that a narrowly scoped candidate preserves ordered native vertex output and visual behavior before considering any production integration.

## What is still untested

1. A production mixin at a verified target, with exact injection-point and lifecycle validation.
2. A complete vanilla-versus-candidate ordered vertex comparison through the actual section compilation path (current candidate parity tests are isolated fixtures, not an active renderer replacement).
3. Screenshot/world-geometry equivalence with and without any future renderer integration.
4. Repeatable before/after performance measurements on the same world, hardware, and settings.

A successful CI build is not proof of an FPS gain. Do not install this build expecting better FPS; it currently makes no rendering changes.

## Build

The GitHub Actions workflow builds with Java 25 and Gradle 9.6.0. Download the `void-fps-build` artifact from the latest successful run in the Actions tab. It contains the JAR, sources JAR, and inspection output.

## License

MIT. See `LICENSE`.
