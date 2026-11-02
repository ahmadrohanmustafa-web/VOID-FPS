# VOID FPS Core

Minecraft Java 26.3 · Fabric · Java 25+

## Objective

Optimize in-world rendering and CPU/GPU workload while leaving every player graphics setting unchanged, including maximum-quality settings. VOID FPS does not lower render distance, simulation distance, particle settings, clouds, shadows, ambient occlusion, or other visual options.

## Implemented systems

### Pickup render-state culling
- Configurable subpixel culling for ordinary dropped item entities and experience orbs.
- Separate item and orb switches, plus a master pickup-culling switch.
- Custom-named pickups are preserved.
- Entities remain loaded and simulated; only render-state extraction is skipped below the configured projected-size threshold.

### Particle render-state culling
- Checks projected on-screen diameter of SingleQuadParticle instances before render-state creation.
- Subpixel particles are skipped only during rendering. Particle ticking, lifetime, movement, collision, spawning, and effects continue normally.
- Vibration signal particles are explicitly preserved.
- Separate enable switch and configurable threshold; F8 toggles the feature at runtime.
- Does not change Minecraft's particle quality setting or particle spawn limit.

### Monitoring and benchmarking
- F9 exports up to 30 seconds of frame-time samples and up to 60 seconds of client-tick samples to config/voidfps/.
- The CSV includes frame average/p99/1% low/min/max, client tick average/p95/maximum, counts of frame/tick spikes above 50 ms and 100 ms, plus per-category culling counters.
- Client tick timing is measured separately from frame timing, helping distinguish CPU-side tick workload from rendering/frame pacing. It measures the client tick callback window, not integrated-server or remote-server tick time.
- F10 toggles the monitor; F11 toggles pickup culling; F12 resets render counters.
- Monitor reports FPS, average and p99 frame time, average and p95 client tick time, 1% low, frame/tick spike counts above 50 ms and 100 ms, JVM GC collection count/time, approximate Java heap occupancy, and skipped render-state counters.
- JVM GC counters are sampled once per second; VOID FPS never triggers garbage collection.
- Projected-size factors are precomputed once per client tick instead of multiplying screen height and projection scale for every candidate entity/particle.
- Client-tick percentile calculation reuses a fixed scratch array to avoid temporary array allocation on each HUD statistics refresh.
- Summary statistics reuse a fixed scratch buffer to reduce temporary allocations during periodic HUD refreshes.

## Configuration

File: config/voidfps.properties

- tinyPickupCullingEnabled
- itemEntityCullingEnabled
- experienceOrbCullingEnabled
- minimumPickupPixels (0.25–2.0)
- tinyParticleCullingEnabled
- minimumParticlePixels (0.1–1.0)
- hudEnabled

Controls can be rebound in Minecraft's Controls menu.

## Status and safety

Optimizations are implemented and build-tested, but real-world FPS impact has not yet been measured on target hardware. Counters measure skipped render-state extraction, not entities or particles removed from the world. No FPS multiplier is promised. Graphics settings remain untouched.

VOID FPS must not delete blocks, alter collision or interaction, disable world simulation, or interfere with networking, lighting updates, or mob AI.

## Build

Requirements: Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API 0.164.0+26.3, Java 25+, Gradle 9.8.2.

Run: gradle --no-daemon clean build

The remapped JAR is in build/libs/. GitHub Actions builds every push and pull request and uploads the JAR artifact.

## Ecosystem boundaries

- VOID FPS Core: in-world rendering and CPU/GPU performance optimization.
- VOID Startup: startup, loading, title screen, main menu.
- VOID UI: HUD and inventory/container interface efficiency and customization.

License: MIT.
