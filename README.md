# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Features

- F8 cycles Vanilla → Gaming → Extreme → Vanilla.
- **Gaming** caps render distance at 10 and simulation distance at 6 chunks, without increasing the player's existing settings.
- Gaming also lowers biome blending to at most 1, entity visibility distance to at most 85%, uses Fast clouds, and reduces particles by one level when the original setting is All.
- Gaming preserves the player's entity-shadow and ambient-occlusion settings.
- **Extreme** caps render distance at 6 and simulation distance at 4 chunks, sets biome blending to 0, entity visibility distance to at most 65%, disables clouds and entity shadows, disables ambient occlusion, and sets particles to Minimal.
- Returning to Vanilla restores every option captured before the first profile switch: render/simulation distance, biome blending, entity distance, clouds, particles, entity shadows, and ambient occlusion.
- F10 toggles the monitor; F9 exports the latest 240 frame intervals to config/voidfps/.
- Monitor shows FPS, average frame time, 99th-percentile frame time, approximate Java heap occupancy, render distance, simulation distance, and profile.
- Detects Sodium, Lithium, FerriteCore, ImmediatelyFast, and Entity Culling; it does not bundle or duplicate them.

Client-only. VOID FPS changes vanilla client graphics options only. It does not delete blocks, alter collision or interaction, disable world simulation, or interfere with networking, lighting updates, or mob AI.

## Build

Requirements: Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API 0.165.0+26.3, Java 25+, Gradle 9.8.2.

Run: gradle --no-daemon clean build

The remapped JAR is in build/libs/. GitHub Actions builds every push and pull request and uploads the JAR artifact.

## Honest performance notes

These profiles use vanilla settings to reduce rendering work; they are not a custom renderer or a guaranteed FPS multiplier. The main expected gains come from lower render/entity distances, fewer particles, simpler clouds, and reduced visual blending. Actual gains depend on the world, mods, drivers, resolution, and hardware.

Gaming is the visual/performance balance. Extreme trades visual detail and view distance for lower rendering workload. Heap occupancy is not total system RAM or GPU memory. Frame-time samples are diagnostics, not a controlled benchmark. Real before/after testing on the target Intel HD 520 laptop remains necessary.

## Ecosystem boundaries

- VOID FPS Core: in-world graphics profiles and frame-time monitoring.
- VOID Startup: startup, loading, title screen, main menu.
- VOID UI: HUD and inventory/container interface efficiency and customization.

License: MIT.
