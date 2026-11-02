# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

VOID FPS Core is the in-game performance module of the VOID FPS ecosystem.

## Modes and controls

- F8 cycles Vanilla → Gaming → Extreme → Vanilla.
- Gaming caps render distance at 10 chunks and simulation distance at 6 chunks, without raising either above your previous settings.
- Extreme caps render distance at 6 chunks and simulation distance at 4 chunks.
- Returning to Vanilla restores the values captured before the first profile switch.
- F10 toggles the performance monitor.
- F9 exports the latest 240 frame intervals as CSV to config/voidfps/.
- Keys can be rebound in Minecraft Controls.

The monitor shows current FPS, average frame time, 99th-percentile frame time, approximate Java heap use, render distance, simulation distance, and active profile.

## Safety and compatibility

This is a client-only mod. It does not delete blocks, hide terrain, disable world simulation, or interfere with collision, interaction, networking, lighting, or mob AI.

It detects Sodium, Lithium, FerriteCore, ImmediatelyFast, and Entity Culling and logs their presence. It does not bundle, replace, or duplicate their code. Install only companion versions that explicitly support Minecraft 26.3 and Fabric.

## Build and install

Requirements: Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API 0.165.0+26.3, Java 25+, and Gradle 9.6+.

Run: gradle --no-daemon clean build

Put the remapped JAR from build/libs/ into your Minecraft mods folder. GitHub Actions builds on every push and pull request and uploads the JAR as an Actions artifact.

## Honest performance notes

The profile controls change vanilla settings; they are not a custom renderer or a guaranteed FPS multiplier. Lower render distance reduces chunk rendering work, while lower simulation distance reduces the nearby world simulation area. Heap occupancy is not total system RAM or GPU memory. Frame-time samples are useful diagnostics but are not a controlled benchmark. Real gains depend on the world, mods, drivers, resolution, and hardware.

Gaming is the recommended mode for normal play. Extreme trades view distance for performance.

## Ecosystem boundaries

- VOID FPS Core: in-world profiles and frame-time measurement.
- VOID Startup: startup, loading, title screen, and main menu.
- VOID UI: HUD and inventory/container interface efficiency and customization.

Startup and UI features are not duplicated here.

## Development status

The full source and CI workflow are committed. A green GitHub Actions build is required before treating the JAR as release-ready. In-game testing and before/after benchmarks remain necessary, especially on Intel HD Graphics 520.

License: MIT.
