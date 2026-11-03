# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Objective

Improve in-game frame pacing and reduce rendering workload without changing the player's graphics options. VOID FPS does not lower render distance, simulation distance, particle quality, clouds, shadows, ambient occlusion, or other video settings.

## Implemented optimization systems

### 1. Terrain block-render suppression

- Skips the final client terrain-geometry draw submission for chunk-section render groups.
- This intentionally makes normal terrain blocks invisible.
- It does **not** delete blocks or change chunk palettes, block states, textures/assets, block behavior, collision, raycasts, mining/placing, block updates, lighting data, block-entity data, packets, or server state.
- World simulation and interaction continue. This is a visual rendering suppression feature, not world editing.
- Block entity rendering is not intentionally disabled by this mixin.

### 2. Chunk compilation scheduling

- Redirects the synchronous section-mesh compilation path to the dispatcher's asynchronous compile queue.
- Uses the existing immutable section-region snapshot and vanilla compilation implementation; it does not change block data or visual settings.
- Intended to reduce render-thread stalls during chunk updates.

### 3. Pickup render-state culling

- Configurable subpixel culling for ordinary dropped item entities and experience orbs.
- Separate item and orb switches, plus a master pickup-culling switch.
- Custom-named pickups are preserved.
- Entities remain loaded and simulated; only render-state extraction is skipped below the configured projected-size threshold.

### 4. Particle render-state culling

- Checks projected on-screen diameter of single-quad particles before render-state creation.
- Subpixel particles are skipped only during rendering. Particle ticking, lifetime, movement, collision, spawning, and effects continue normally.
- Vibration signal particles are explicitly preserved.
- Separate enable switch and configurable threshold. F8 toggles this feature at runtime.
- Does not change Minecraft's particle quality setting or particle spawn limit.

### 5. Monitoring and benchmarking

- F9 exports up to 30 seconds of frame-time samples and up to 60 seconds of client-tick samples to `config/voidfps/`.
- CSV includes frame average/p99/1% low/min/max, client tick average/p95/maximum, counts of frame/tick spikes above 50 ms and 100 ms, plus per-category culling counters.
- Client tick timing is measured separately from frame timing. It measures the client tick callback window, not integrated-server or remote-server tick time.
- The diagnostic overlay is OFF by default. F10 toggles it; F11 toggles pickup culling; F12 resets render counters.
- Overlay reports FPS, average and p99 frame time, average and p95 client tick time, 1% low, frame/tick spike counts, JVM GC collection count/time, approximate Java heap occupancy, and skipped render-state counters.
- JVM GC counters are sampled once per second. VOID FPS never triggers garbage collection.
- Rolling monitor buffers and scratch arrays are reused to limit diagnostic allocations.

## Controls

All controls can be rebound in Minecraft's Controls menu:

- F8 — toggle tiny-particle culling
- F9 — export benchmark CSV
- F10 — toggle performance monitor
- F11 — toggle pickup culling
- F12 — reset render counters

## Configuration

File: `config/voidfps.properties`

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
- Terrain block rendering is intentionally suppressed, so this is not a normal-visibility gameplay profile.
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
