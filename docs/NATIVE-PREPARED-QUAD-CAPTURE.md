# Native prepared-quad capture design (Minecraft 26.3)

## Verified callback boundary

Build #225's Minecraft 26.3 bytecode confirms this exact path:

- `ModelBlockRenderer.tesselateBlock(...)` selects model parts, applies the model offset, and chooses ambient-occlusion or flat tessellation.
- Both tessellation paths apply vanilla directional face culling when enabled.
- Both paths call the native `BlockModelLighter` to prepare the `QuadInstance`.
- `putQuadWithTint(...)` applies the native tint result when required, then invokes the supplied `BlockQuadOutput.put(float x, float y, float z, BakedQuad quad, QuadInstance instance)`.
- The section compiler's output callbacks route that prepared quad to `BufferBuilder.putBlockBakedQuad(...)`.

This makes `BlockQuadOutput.put` the correct observation boundary for a test-only capture. It is after vanilla has selected the quad, culled it, prepared lighting/AO, and applied tint, but before the section buffer writes packed vertices.

## Important mutable-state detail

`ModelBlockRenderer` owns one reusable `QuadInstance` field. The callback must copy all four colors, all four packed light values, overlay, and other relevant metadata immediately when `put` is called. Storing the `QuadInstance` reference for later inspection would be invalid because the renderer reuses and overwrites it for subsequent quads.

The existing `NativeVertexRecorder` can be used inside the callback:

```java
BlockQuadOutput capture = (x, y, z, quad, instance) ->
        recorder.putBlockBakedQuad(x, y, z, quad, instance);
```

That records the final ordered native vertex arguments through Minecraft's real `VertexConsumer.putBlockBakedQuad` implementation. The capture must still retain per-quad metadata separately (direction, material information, and output-layer identity), because those values are not all represented in the vertex records.

## Fixture requirements

Do not use a synthetic model or a made-up lighting result for the parity milestone. The fixture must provide a real `BlockAndTintGetter` backed by a section-region snapshot with known neighboring block states and light data. This is necessary to exercise the same face-culling, AO/flat-lighting, and tint inputs as the section compiler.

The capture should begin with a plain stone block in a controlled region, then expand to adjacent stone/air, different solid neighbors, and rejected block types. Run separate fixtures for ambient-occlusion and flat-lighting paths where the native model/settings select them.

## Required assertions

For each captured quad, preserve and compare:

- callback order and output-layer selection;
- block-relative x/y/z translation;
- native face direction and material metadata;
- all four positions and UVs;
- all four prepared colors and packed light values;
- overlay and normals after `putBlockBakedQuad`.

The vanilla path is the reference. A candidate path must use the same fixture and inputs and match the complete ordered capture. A test that only invokes `putBlockBakedQuad` with synthetic data does not establish vanilla-versus-candidate parity.

## Safety gate

This is a test design only. Do not add a production mixin, replace the section compiler callback, or enable custom rendering until the real fixture-based capture passes, unsupported blocks demonstrably fall back to vanilla, visual output is equivalent, and repeatable benchmarks show a benefit.
