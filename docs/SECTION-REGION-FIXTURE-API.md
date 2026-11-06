# Minecraft 26.3 section-region fixture API findings

## Verified native construction path

Build #227 inspected the actual Minecraft 26.3 bytecode and signatures for
`SectionCopy`, `RenderSectionRegion`, `LevelChunk`,
`LevelChunkSection`, and `ClientLevel`.

The verified public constructors are:

```java
new SectionCopy(LevelChunk chunk, int sectionIndex)
new RenderSectionRegion(
        ClientLevel level,
        int minSectionX,
        int minSectionY,
        int minSectionZ,
        SectionCopy[] sections)
```

`RenderSectionRegion` has a fixed 3-by-3-by-3 section neighborhood. Its
`index(minX, minY, minZ, x, y, z)` method maps a section coordinate into
that 27-element array. The region delegates block-state, fluid-state, and
block-entity reads to the selected `SectionCopy`.

## What SectionCopy actually snapshots

The constructor reads the source `LevelChunk` and:

- retains the chunk's level-height accessor and debug-world flag;
- copies the block-entity map into an immutable map;
- obtains the chunk's `LevelChunkSection[]` and selects the requested index;
- stores null for an empty/all-air section, otherwise copies that section's
  `PalettedContainer<BlockState>`.

This is a real block-state snapshot, not a fabricated block lookup. The copy
is not a complete immutable world snapshot.

## Important live dependencies

`RenderSectionRegion` stores the supplied `ClientLevel` and obtains
`CardinalLighting` and `LevelLightEngine` from that live level in its
constructor. Its `getBlockTint(...)` also delegates to the live
`ClientLevel`.

Therefore the section fixture has two distinct data sources:

1. Block states and block entities from the copied `SectionCopy[]`.
2. Lighting, cardinal-lighting behavior, tint, and height information from
   the associated live `ClientLevel`.

Do not describe this as a fully frozen light/tint snapshot. The test must
control and record the relevant live-level inputs when asserting parity.

## CI fixture constraint

The existing automated test client reaches the title screen and has no
loaded `ClientLevel` or real `LevelChunk`. Consequently, it cannot honestly
construct a native `RenderSectionRegion` from actual chunk data in the
current startup-only smoke test.

The next fixture milestone must run in a controlled client world with loaded
chunks, or introduce a separately verified test-world bootstrap that creates
a genuine `ClientLevel` and populated `LevelChunk` objects. Do not substitute
mock chunks, synthetic light values, or a made-up `BlockAndTintGetter` and
call that native parity.

## Next test sequence

1. Obtain a real client world and the target `LevelChunk`.
2. Build the complete 27-entry `SectionCopy[]` neighborhood using the
   exact coordinate order verified from `RenderSectionRegion.index(...)`.
3. Construct `RenderSectionRegion` with matching minimum section coordinates.
4. Assert target and six neighbor block states through the region API.
5. Record the light-engine and tint inputs used for the same fixture.
6. Invoke the native `ModelBlockRenderer.tesselateBlock(...)` path with a
   test-only `BlockQuadOutput` capture.
7. Copy mutable `QuadInstance` fields immediately inside every callback.
8. Compare ordered quad metadata and packed vertex output against the vanilla
   section compiler path before attempting any candidate implementation.

## Safety gate

This remains investigation and test-fixture design only. No production mixin,
section compiler replacement, custom renderer, or active optimization is
authorized by these findings. Keep unsupported states on vanilla rendering.
