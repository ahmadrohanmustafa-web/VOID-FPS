# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Project goal

VOID FPS Core is intended to improve in-game performance while leaving the player's graphics settings unchanged, including maximum-quality settings. It must not lower render distance, simulation distance, particles, clouds, shadows, ambient occlusion, or other visual options automatically.

## Current features

- F9 exports the latest 240 frame intervals to `config/voidfps/`.
- F10 toggles the performance monitor.
- Monitor reports FPS, average frame time, 99th-percentile frame time, approximate Java heap occupancy, render distance, and simulation distance.
- Detects Sodium, Lithium, FerriteCore, ImmediatelyFast, and Entity Culling; it does not bundle or duplicate them.
- Does not change Minecraft video settings.

## Important implementation status

The current monitor and benchmark exporter are diagnostic tools; they do not themselves increase FPS. The previous Vanilla/Gaming/Extreme settings profiles have been removed because changing graphics options is not the intended optimization strategy.

The next optimization work must target actual rendering or CPU/GPU workload while preserving the same visible result and all player settings. Any optimization must be implemented and build-tested before it is described as a feature. Performance gains must be measured on real hardware; no FPS multiplier is promised.

Client-only. VOID FPS must not delete blocks, alter collision or interaction, disable world simulation, or interfere with networking, lighting updates, or mob AI.

## Build

Requirements: Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API 0.164.0+26.3, Java 25+, Gradle 9.8.2.

Run: `gradle --no-daemon clean build`

The remapped JAR is in `build/libs/`. GitHub Actions builds every push and pull request and uploads the JAR artifact.

## Ecosystem boundaries

- VOID FPS Core: in-world rendering and CPU/GPU performance optimization.
- VOID Startup: startup, loading, title screen, main menu.
- VOID UI: HUD and inventory/container interface efficiency and customization.

License: MIT.
