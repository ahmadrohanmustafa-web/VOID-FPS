# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Project goal

VOID FPS Core targets in-world rendering and CPU/GPU workload while leaving the player's graphics settings unchanged, including maximum-quality settings. It does not lower render distance, simulation distance, particles, clouds, shadows, ambient occlusion, or other visual options.

## Current features

- Configurable subpixel culling for ordinary dropped item entities and experience orbs. Entities remain loaded and simulated; only their render-state extraction is skipped below the configured projected-size threshold.
- Separate item-entity and experience-orb culling switches, plus a master pickup-culling switch.
- Custom-named pickups are always preserved.
- The projected-pixel threshold is configurable in `config/voidfps.properties`.
- The HUD reports cumulative skipped pickup render-state extractions.
- F9 exports up to 30 seconds of frame-time samples, average, p99, 1% low, minimum, maximum, and per-type pickup-culling counters to `config/voidfps/`.
- F10 toggles the performance monitor.
- The monitor reports FPS, average frame time, p99 frame time, 1% low, approximate Java heap occupancy, and pickup-culling activity.
- Summary statistics reuse a fixed scratch buffer, avoiding repeated temporary-array allocation during periodic HUD refreshes.
- Detects Sodium, Lithium, FerriteCore, ImmediatelyFast, and Entity Culling; it does not bundle or duplicate their systems.

## Important implementation status

The pickup visibility optimization is implemented and build-tested, but its real-world FPS impact has not yet been measured on target hardware. Culling counters measure skipped render-state extraction, not entities removed from the world. Benchmarking is diagnostic; no FPS multiplier is promised. Graphics settings remain untouched.

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
