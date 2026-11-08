# VOID FPS

Client-side Fabric project for investigating CPU-side block and terrain rendering work in Minecraft Java Edition **26.3**.

> **Current verdict: no additional optimization is enabled.** Minecraft 26.3 already reuses face-occlusion results, directional face checks, tint colors, packed light, and shade brightness during block/section compilation. VOID FPS does not add duplicate caches or replace vanilla rendering.

## Verified project status

- GitHub Actions builds with Java 25 and Gradle 9.6.0.
- The client entrypoint only logs a status message; it does not modify rendering.
- Real Minecraft 26.3 client tests exercise native vertex-output fixtures, load a test world, and complete the normal save/disconnect lifecycle.
- Real temporary folder resource packs are selected through `PackRepository` and activated by Minecraft's resource reload path. Tests verify full-cube eligibility and conservative fallback for partial, internal, unculled, duplicate, tinted, and rotated geometry. UV-only rotation remains eligible. The original pack selection is restored and the temporary pack is removed.
- Builds #318 and #319 passed the rotated-element geometry test and its mandatory CI assertion.
- The verified render path is `SectionCompiler.compile` → `ModelBlockRenderer.tesselateBlock` → directional face checks / `BlockQuadOutput` → native `BufferBuilder.putBlockBakedQuad`.

## Important vanilla optimizations already present

Minecraft 26.3 bytecode confirms multiple caches and reuse guards in the exact path VOID FPS was considering:

1. **Face occlusion:** `Block.shouldRenderFace` uses a thread-local 256-entry access-ordered cache keyed by pairs of face-occlusion shapes. It performs the expensive shape join only on a cache miss.
2. **Packed light and shade brightness:** `BlockModelLighter.Cache` uses thread-local linked maps with 100-entry limits for packed-light and shade-brightness results.
3. **Section lifecycle:** `SectionCompiler.compile` enables the lighting cache before compiling a section and clears it at the end of the normal compile path.
4. **Directional face checks:** `ModelBlockRenderer` tracks checked and visible directions with bit masks during ambient-occlusion tessellation, avoiding repeated neighbor checks for a direction across model parts.
5. **Tint reuse:** `ModelBlockRenderer` keeps tint-source results for the current block and has a last-index/last-color fast path, so repeated tint requests need not recompute the color.

The CI bytecode verifier checks these details. If Minecraft changes these internals, the check fails and requires a fresh review rather than silently relying on an outdated assumption.

Adding second caches for these same calculations is not a justified optimization. It can duplicate lookups, memory, and invalidation complexity. The current investigation therefore moves on to per-quad geometry and AO preparation, where the bytecode shows work is performed for each baked quad; any optimization there must preserve exact output and be measured before it is considered.

## Scope

Only block and terrain rendering. Out of scope: entities, particles, HUD/overlays, keybinds, automatic setting changes, chunk scheduling, startup/GUI optimization, gameplay, and anything that lowers visual quality.

## What remains before production integration

1. Identify a genuinely redundant operation in the verified section-compilation path that vanilla does not already cache.
2. Prove a candidate preserves ordered native vertex output, UVs, tint, lighting, and ambient occlusion through actual section compilation.
3. Compare screenshots/world geometry and run repeatable before/after benchmarks on the same world, hardware, and settings.
4. Only then consider a narrowly scoped production mixin with a verified injection point.

The current candidate parity checks are isolated fixtures, not an active renderer replacement. No production mixin is enabled and no FPS gain is claimed. Do not install this build expecting better FPS; it currently makes no rendering changes.

## Build

GitHub Actions uses Java 25 and Gradle 9.6.0. Download the `void-fps-build` artifact from the latest successful Actions run.

## License

MIT. See `LICENSE`.
