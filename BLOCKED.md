# What is NOT implemented, and why

Nothing in this project has been compiled against Minecraft 26.3 or run in the game.
The `dev.voidfps.surface` core is Minecraft-free; it compiles and passes `SelfTest` on plain JDK 21.

## Blocked until javap / the 26.3 jar is available

1. **Chunk-compile hook (mixin).** The class and method that iterate blocks while building a section
   mesh, and the call that renders a block's model, must be found with `javap` on the 26.3 jar.
   `void_fps_surface.mixins.json` is empty on purpose: no target is invented.
   Required behaviour: per block, look up a cached SurfacePlan; if present call SurfaceCompiler.emit and
   skip vanilla's model call for that block (never both); if absent do nothing.
2. **Adapter: baked model -> ModelInfo/BakedQuadData.** Needs 26.3's model-part / baked-quad accessors
   (vertex positions, UVs, cull face, light face, tint index, shade flag, sprite) and the per-position
   variant selection used for weighted variants. Cache by (state, chosen variant).
3. **Adapter: BlockView.** Map to 26.3 queries for: face occlusion (vanilla shouldRenderFace semantics,
   incl. unloaded neighbours), packed light incl. emission, AO shade brightness, light-block value,
   dimension-dependent directional shade.
4. **Output sink.** Writing vertices into the 26.3 section mesh buffer for the solid layer, in its
   vertex format and light packing (the core assumes sky in the high 16 bits, block in the low 16).
5. **ModelInfo flags**: opaqueFullCube, hasBlockEntityRenderer, solidRenderLayer, ambientOcclusionAllowed.

## Must be compared against vanilla output once buildable (`VERIFY-AGAINST-VANILLA`)
SurfaceLighting: corner-fallback choice, zero-light substitution, light packing, AO brightness values.
Tests only pin this library's own behaviour, not Minecraft's.

## Out of scope for milestone 1 (stay on vanilla)
Tinted quads, cutout/translucent layers, non-cube shapes, quads without cull face, multi-quad faces,
block-entity renderers.
