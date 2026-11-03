# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Objective

Improve in-game frame pacing and reduce rendering workload without changing the player's graphics options or making the world visually incomplete.

VOID FPS does not lower render distance, simulation distance, particle quality, clouds, shadows, ambient occlusion, or other video settings.

## Block rendering: design and technical boundary

VOID FPS preserves normal Minecraft block rendering and all six original face textures.

Minecraft does not render a solid filled cube for every block. Its chunk renderer builds meshes from block-model faces, and the vanilla model renderer already omits many faces covered by neighboring opaque blocks. A texture also cannot be displayed without some geometry carrying it.

The target for a future VOID mesh optimization is therefore not to delete a block's interior (Minecraft does not draw a solid interior volume). It is to reduce the cost of building, storing, uploading, and drawing chunk meshes while retaining every face that can contribute to the correct image.

Any custom mesh path must preserve:
- all visible faces and original texture UVs;
- block tint, biome tint, ambient occlusion, light values and shading;
- cutout, translucent, emissive and animated block models;
- non-cube models, fluids, block entities and modded models;
- chunk-boundary visibility and correct rebuilds after block changes;
- the original world data, collision, interaction, mining, placing and updates.

A naive camera-facing-only face filter is not correct for a 3D world: a face facing away from the camera can still be visible on a different block, and translucent/custom models have different rules. VOID FPS will not use that shortcut.

## Implemented optimization systems

### 1. Chunk compilation scheduling

- Redirects the synchronous section-mesh compilation path to the dispatcher's asynchronous compile queue.
- Uses the existing immutable section-region snapshot and vanilla compilation implementation; it does not change block data or visual settings.
- Intended to reduce render-thread stalls during chunk updates.

### 2. Pickup render-state culling

- Configurable subpixel culling for ordinary dropped item entities and experience orbs.
- Separate item and orb switches, plus a master pickup-culling switch.
- Custom-named pickups are preserved.
- Entities remain loaded and simulated; only render-state extraction is skipped below the configured projected-size threshold.

### 3. Particle render-state culling

- Checks projected on-screen diameter of single-quad particles before render-state creation.
- Subpixel particles are skipped only during rendering. Particle ticking, lifetime, movement, collision, spawning, and effects continue normally.
- Vibration signal particles are explicitly preserved.
- Separate enable switch and configurable threshold. F8 toggles this feature at runtime.
- Does not change Minecraft's particle quality setting or particle spawn limit.

### 4. Adaptive render culling

- F7 toggles adaptive culling.
- Monitors the most recent frame time with a low-cost sample; it does not sort the benchmark history every tick.
- When frames remain slow, it gradually increases the distance threshold for tiny pickup and single-quad particle render-state culling, up to 2.5x.
- When frame times recover, it gradually returns to the normal threshold.
- Does not alter entity simulation, particle simulation, block rendering, render distance, or world data.
- Disable with F7 or `adaptiveCullingEnabled=false` if you prefer fixed culling.

### 5. Monitoring and benchmarking

- F9 exports up to 30 seconds of frame-time samples and up to 60 seconds of client-tick samples to `config/voidfps/`.
- CSV includes frame average/p99/1% low/min/max, client tick average/p95/maximum, counts of frame/tick spikes above 50 ms and 100 ms, plus per-category culling counters.
- Client tick timing is measured separately from frame timing. It measures the client tick callback window, not integrated-server or remote-server tick time.
- The diagnostic overlay is OFF by default. F10 toggles it; F11 toggles pickup culling; F12 resets render counters.
- Overlay reports FPS, average and p99 frame time, average and p95 client tick time, 1% low, frame/tick spike counts, JVM GC collection count/time, approximate Java heap occupancy, skipped render-state counters, and the adaptive culling multiplier.
- JVM GC counters are sampled once per second. VOID FPS never triggers garbage collection.
- Rolling monitor buffers and scratch arrays are reused to limit diagnostic allocations.

## Controls

All controls can be rebound in Minecraft's Controls menu:

- F7 — toggle adaptive culling
- F8 — toggle tiny-particle culling
- F9 — export benchmark CSV
- F10 — toggle performance monitor
- F11 — toggle pickup culling
- F12 — reset render counters

## Configuration

File: `config/voidfps.properties`

- `adaptiveCullingEnabled`
- `tinyPickupCullingEnabled`
- `itemEntityCullingEnabled`
- `experienceOrbCullingEnabled`
- `minimumPickupPixels` (0.25–2.0)
- `tinyParticleCullingEnabled`
- `minimumParticlePixels` (0.1–1.0)
- `hudEnabled`

## Compatibility and limitations

- Client-only; no server installation is required.
- Detects Sodium, Lithium, FerriteCore, ImmediatelyFast, and Entity Culling without bundling or duplicating them.
- Mixins are version-sensitive. A successful compile does not prove that every injection behaves correctly in a live Minecraft session.
- Normal terrain visibility is preserved. This version does not contain a custom replacement chunk renderer, chunk-region draw batching, or a compact custom terrain vertex format.
- Performance gains and side effects must be measured in Minecraft on the target hardware. No FPS multiplier is promised.

## Build

Requirements: Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API 0.164.0+26.3, Java 25+, Gradle 9.8.2.

Run: `gradle --no-daemon clean build`

The remapped JAR is in `build/libs/`. GitHub Actions builds every push and pull request and uploads the JAR artifact.

## Ecosystem boundaries

- VOID FPS Core: in-world rendering and CPU/GPU performance optimization.
- VOID Startup: startup, loading, title screen, main menu.
- VOID UI: HUD and inventory/container interface efficiency and customization.

License: MIT.


## Automatic render distance (F6)

F6 enables an optional frame-time controller. It records the render distance
active when enabled, then adjusts it gradually within a four-chunk lower range.
It lowers distance after sustained frame times of 33.3 ms or more and raises it
only after sustained frame times of 23.5 ms or less. Each change has a cooldown
to avoid repeatedly rebuilding chunks. Press F6 again to disable it and restore
the original distance. This trades some view distance for smoother performance
when the CPU or GPU is overloaded; it does not replace Minecraft's terrain
renderer and cannot guarantee an FPS increase.
