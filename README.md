# VOID FPS

Minecraft Java 26.3 · Fabric · Java 25+

## Purpose

VOID FPS is a specialized block and terrain rendering optimization mod. It is not intended to replace Sodium or duplicate general-purpose optimization features.

VOID FPS is limited to block-model face visibility. Minecraft 26.3 already performs normal hidden-face culling during section meshing. VOID FPS therefore does not add a second, unsafe occlusion rule; it memoizes repeated identical vanilla face-visibility queries within one block-model tessellation.

## Strict visual requirements

- Keep the player's render distance and graphics settings unchanged.
- Never lower texture quality, lighting, ambient occlusion, or visual effects.
- Never hide a face that can contribute to the image.
- Preserve transparent, cutout, translucent, animated, emissive, fluid, non-cube, and modded block models.
- Preserve chunk-boundary visibility and correct updates after blocks change.
- Do not add optimization keybinds, toggles, or a performance HUD.

## What the current implementation does

### Repeated face-decision cache

`BlockModelRendererMixin` memoizes a face-visibility result during the tessellation of one block model.

- The first query for a direction always calls Minecraft's original `shouldRenderFace` implementation.
- A repeated query is reused only when the level, block state, block position, direction, and neighbor position match.
- The cache is thread-local, so worker-thread chunk compilation does not share mutable cache state.
- The cache is reset for every block-model tessellation.
- It does not invent new occlusion rules or remove any additional geometry.

This is a narrow CPU-side optimization for models that ask the same directional visibility question multiple times. Ordinary cube models may not repeat those queries, so this change is not expected to produce a large universal FPS increase.

## Important rendering fact

Minecraft 26.3 already removes many hidden opaque block faces during its own model tessellation. A second independent face-culling rule would usually duplicate vanilla work and could break special models. The safe first optimization is therefore to reuse identical vanilla face decisions rather than changing the visibility result.

## Current implementation status

The branch contains the repeated face-decision cache. Unrelated chunk-compilation scheduling has been removed so the mod stays within its block-rendering-only scope.

This is **not yet a complete replacement terrain renderer**, and no measurable in-game FPS improvement has been established. Runtime mixin application, visual parity, and before/after performance still require testing in Minecraft 26.3.

Do not describe the mod as finished or performance-verified until those tests pass.

## Build

Requirements:
- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API 0.164.0+26.3
- Java 25+
- Gradle 9.8.2

Run:

`gradle --no-daemon clean build`

The JAR is generated in `build/libs/`. A successful build verifies compilation only; it does not establish runtime mixin compatibility or a measurable FPS gain.

## Compatibility

The face-decision cache delegates the first query to Minecraft's own renderer and only reuses an identical result within the same block tessellation. If the mixin cannot apply, the mod must fail safely and leave vanilla rendering available.
