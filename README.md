# VOID FPS

Client-side Fabric project for investigating CPU-side block and terrain rendering work in Minecraft Java Edition **26.3**.

> **Current verdict: no additional optimization is enabled.** The 26.3 Minecraft client bytecode was inspected through the successful GitHub Actions build. Vanilla already performs face-occlusion caching, so adding a second cache for the same checks would duplicate existing work. No other candidate has yet met the evidence and benchmark requirements below.

## Verified project status

- GitHub Actions successfully builds the project with Java 25 and Gradle 9.6.0.
- The build produces a client JAR and a source JAR.
- The client entrypoint only logs a status message. It does not modify rendering.
- The inspection artifact includes signatures and bytecode for `Block`, `ModelBlockRenderer`, and `SectionCompiler`.
- The inspected vanilla `Block.shouldRenderFace` uses a thread-local, 256-entry LRU-style cache keyed by the two face-occlusion shapes. `ModelBlockRenderer.shouldRenderFace` delegates to that vanilla method.
- GitHub Actions successfully launched Minecraft 26.3 under Xvfb and confirmed that the VOID FPS client entrypoint loaded (startup smoke test only).
- No native vanilla/candidate vertex capture, visual-equivalence test, or before/after performance benchmark has been performed.

## Scope

Only block and terrain rendering. Out of scope: entities, particles, HUD/overlays, keybinds, automatic setting changes, chunk scheduling, startup/GUI optimization, gameplay, and anything that lowers visual quality.

## Why no optimization was added

The most obvious proposed optimization—caching repeated block-face visibility checks—is already implemented by Minecraft 26.3. Reimplementing it in VOID FPS would add another lookup/cache and could make performance or memory use worse.

The inspection also found vanilla's existing renderer and tint-cache paths. No further change is accepted until actual 26.3 bytecode/source proves redundant work that can safely be removed without changing output geometry, lighting, ambient occlusion, tint, or texture behavior.

## What is still untested

1. Loading a real test world and capturing native vanilla and candidate vertex output.
2. Comparing ordered vertex records for supported and rejected block fixtures.
3. Comparing screenshots/world geometry with and without any future renderer integration.
4. Measuring a repeatable baseline and modded run on the same world and settings.

A successful CI build is not a runtime test and does not demonstrate an FPS gain. Do not install this build expecting better FPS; it currently makes no rendering changes.

## Build

The GitHub Actions workflow builds with Java 25 and Gradle 9.6.0. Download the `void-fps-build` artifact from the latest successful run in the Actions tab. It contains the JAR, sources JAR, and inspection output.

## License

MIT. See `LICENSE`.
