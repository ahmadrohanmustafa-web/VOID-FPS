# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Features

- F8 cycles Vanilla → Gaming → Extreme → Vanilla.
- Gaming caps render distance at 10 and simulation distance at 6 chunks, without increasing your existing settings.
- Extreme caps render distance at 6 and simulation distance at 4 chunks.
- Returning to Vanilla restores the settings captured before the first profile switch.
- F10 toggles the monitor; F9 exports the latest 240 frame intervals to config/voidfps/.
- Monitor shows FPS, average frame time, 99th-percentile frame time, approximate Java heap occupancy, render distance, simulation distance, and profile.
- Detects Sodium, Lithium, FerriteCore, ImmediatelyFast, and Entity Culling; it does not bundle or duplicate them.

Client-only. It does not delete blocks, hide terrain, disable world simulation, or interfere with collision, interaction, networking, lighting, or mob AI.

## Build

Requirements: Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API 0.165.0+26.3, Java 25+, Gradle 9.8.2.

Run: gradle --no-daemon clean build

The remapped JAR is in build/libs/. GitHub Actions builds every push and pull request and uploads the JAR artifact.

## Honest performance notes

These profiles change vanilla distance settings; they are not a custom renderer or a guaranteed FPS multiplier. Heap occupancy is not total system RAM or GPU memory. Frame-time samples are diagnostics, not a controlled benchmark. Real gains depend on the world, mods, drivers, resolution, and hardware.

Gaming is recommended for normal play; Extreme trades view distance for performance. In-game testing and before/after benchmarks on the target Intel HD 520 laptop remain necessary.

## Ecosystem boundaries

- VOID FPS Core: in-world profiles and frame-time monitoring.
- VOID Startup: startup, loading, title screen, main menu.
- VOID UI: HUD and inventory/container interface efficiency and customization.

License: MIT.
