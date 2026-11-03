# VOID FPS

Minecraft Java 26.3 · Fabric · Java 25+

## Purpose

VOID FPS is a specialized block and terrain rendering optimization mod. It is not intended to replace Sodium or duplicate general-purpose optimization features.

Its central goal is to reduce unnecessary block-face and terrain-mesh work while preserving the exact visible world.

## Strict visual requirements

- Keep the player's render distance and graphics settings unchanged.
- Never lower texture quality, lighting, ambient occlusion, or visual effects.
- Never hide a face that can contribute to the image.
- Preserve transparent, cutout, translucent, animated, emissive, fluid, non-cube, and modded block models.
- Preserve chunk-boundary visibility and correct updates after blocks change.
- Do not add optimization keybinds, toggles, or a performance HUD.

## Important rendering fact

Minecraft already omits many faces covered by neighboring opaque blocks during its normal chunk-meshing process. A second face-culling check that simply repeats vanilla's decision would not create a real optimization.

The meaningful target is the actual 26.3 chunk-mesh construction path: reduce the CPU and memory cost of deciding which model faces contribute, building the visible mesh, and preparing it for rendering without changing the resulting image.

A block's texture is carried by rendered geometry. The goal is to omit hidden geometry while keeping all visible textured faces, not to remove visible blocks.

## Current implementation status

This branch is a cleanup and foundation step, not a completed custom block renderer.

- Removed unrelated entity/particle culling, monitoring, adaptive controls, benchmark HUD, and their keybinds.
- Kept the existing chunk compilation scheduling mixin as the only current terrain-related hook.
- A specialized block-face / chunk-mesh optimization has **not yet been implemented or verified**.

Do not describe this branch as delivering the final rendering optimization until that implementation exists and has been tested in-game.

## Build

Requirements:
- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API 0.164.0+26.3
- Java 25+
- Gradle 9.8.2

Run:

`gradle --no-daemon clean build`

The JAR is generated in `build/libs/`.

## Compatibility

The custom block-rendering work must use verified Minecraft 26.3 APIs. If the custom path fails to initialize, vanilla terrain rendering must remain available. A successful compile alone does not prove correct rendering.
