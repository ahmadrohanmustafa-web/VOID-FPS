# Rendering investigation (Minecraft 26.3)

Research date: 2026-11-03. Method: web research only. **No Minecraft jar was inspected**, because the
environment that produced this project had no network access for Gradle/Loom, no JDK 25 and no `javap`.
Everything below is therefore split by how well it is supported.

## A. Toolchain facts (from sources)

| Item | Value | Source |
|---|---|---|
| Minecraft | 26.3 ("Wilderness Bound", released 2026-09-15) | https://fabricmc.net/2026/09/15/263.html |
| Fabric Loader | 0.19.5 | same |
| Loom | 1.17 (exact artifact string unverified: `1.17-SNAPSHOT` assumed) | same |
| Gradle | 9.6.0 "at the time of writing" (9.7.1 is used by at least one 26.3 mod) | same; https://github.com/imCinq/water-optimisation/releases/tag/v0.0.10 |
| Java | 25 | 26.3 mod release notes, e.g. https://www.curseforge.com/minecraft/mc-mods/keenlib |
| Loom plugin id | `net.fabricmc.fabric-loom` (non-obfuscated, 26.1+) | https://docs.fabricmc.net/develop/loom/ |
| Mappings | Mojang official names, unobfuscated, no remap | https://docs.fabricmc.net/26.1.2/develop/porting/ |
| Fabric API (not used) | 0.160.5+26.3 to 0.161.0+26.3 seen in the wild | 26.3 mod release notes |

## B. Rendering classes whose *existence* is reported by third-party primers

Names only. Parameter lists for 26.3 are **not verified**.

- `net.minecraft.client.renderer.block.ModelBlockRenderer#tesselateBlock` — `BlockRenderDispatcher` was
  removed in 26.1 and callers now invoke this directly; `ModelBlockRenderer` takes booleans for ambient
  occlusion and culling. (NeoForge 26.1 primer: https://docs.neoforged.net/primer/docs/26.1/)
- `ModelBlockRenderer$Cache` with `getLightCoords` (renamed from `getLightColor` in 26.1). Vanilla therefore
  already caches per-position light data used by smooth lighting / AO.
- `net.minecraft.client.renderer.chunk.SectionCompiler#compile` — compiles a region into section meshes;
  in 26.2 it stopped taking a `BlockEntityRenderDispatcher`. (NeoForge 26.2 primer)
- `net.minecraft.client.renderer.chunk.RenderSectionRegion`, `SectionCopy` — made public in 26.2.
- 26.3 terrain changes: indirect multi-draw (`Minecraft#multiDrawIndirect`, `SOLID_TERRAIN_MULTIDRAW`
  etc.), improved transparency (OIT) and a sections fade duration. These are draw-submission / GPU-side
  changes and are outside what this mod may touch. (NeoForge 26.3 primer: https://docs.neoforged.net/primer/docs/26.3/)
- Fluid tessellation lives in a `FluidRenderer` class (`FluidRenderer#tesselate`, `addFace`) per the 26.1
  primer and a 26.3 mod's architecture notes. Fluids are out of scope for a *first* pass; see below.

## C. Work vanilla is already reported to do (do not re-implement and advertise)

- Same-fluid face culling happens before geometry is emitted (water-optimisation ARCHITECTURE.md:
  https://github.com/imCinq/water-optimisation/blob/main/docs/ARCHITECTURE.md).
- Per-position light/AO inputs are cached in `ModelBlockRenderer$Cache` (primer above).
- Face culling is a constructor flag of `ModelBlockRenderer` (primer above), meaning culling is core vanilla
  behavior, not something to bolt on.
- Whether vanilla also caches neighbor-face occlusion results (older versions did inside `Block`) in 26.3
  is **unknown** and decides whether the "cache face visibility" idea has any value at all.

## D. Candidate optimizations and their honest status

| # | Candidate | Needs | Status |
|---|---|---|---|
| 1 | Cache repeated face-visibility checks | Whether vanilla already caches; the exact method and its callers | **Not implementable yet.** May turn out to be redundant. |
| 2 | Avoid per-block allocations in `tesselateBlock` | Decompiled body of `tesselateBlock` and helpers | **Not implementable yet.** |
| 3 | Reuse work across multipart/weighted model parts | Shape of `BlockStateModel`/`BlockModelPart` in 26.3 | **Not implementable yet.** Correctness risk is high (random seeds, rotations). |
| 4 | Fluid hidden-face skipping | Already done by third-party mods; out of scope | **Excluded**: not block/terrain model work and already covered elsewhere. |

No candidate has been rejected on evidence, and none has been accepted. That is the real state of the project.

## E. Acceptance criteria for any future optimization

1. The target method and descriptor appear in `signatures.txt` produced by `tools/inspect_targets.py`.
2. The decompiled body shows redundant work that vanilla does not already avoid.
3. Cache keys contain no world/chunk/level references, or are scoped to a single section compile.
4. Compile threads are respected (section compilation is multi-threaded in vanilla): no shared mutable state
   without synchronization, or use thread-confined state.
5. An A/B toggle (JVM property) exists for benchmarking, and output geometry is compared between vanilla
   and VOID FPS for a fixed set of blocks (full cubes, glass, leaves, stairs, slabs, fences, walls, plants,
   multipart, tinted) before the optimization is kept.
6. A measurable CPU-side improvement (section compile time) exists on real hardware; otherwise remove it.
