# Real client-world bootstrap investigation (Minecraft 26.3)

## Current verified boundary

Build #228 passed on commit `e6eaaffbaf53dff2a9b14c2910d558887b754980`.
The workflow completed the Gradle build, 37 self-checks, and the title-screen
Minecraft client smoke test. It also uploaded the Minecraft bytecode inspection
artifact.

The title-screen test still has no live `ClientLevel` or loaded `LevelChunk`.
The section-region API findings therefore remain a design constraint, not a
working world fixture.

## Objective

Find a supported way for the test client to enter a genuine local world and
obtain its real client-side chunks before constructing the 27-section
`RenderSectionRegion`. Prefer the normal integrated-server/client connection
path over manually constructing internal network or world objects.

## APIs to inspect next

The next CI bytecode dump expands inspection around:

- `ClientPacketListener` and its constructor dependencies;
- `ClientLevel.ClientLevelData` and `LevelExtractor`;
- `ClientChunkCache` and chunk installation/loading entry points;
- `IntegratedServer` and the normal single-player startup path;
- `DimensionType`, level-height accessors, and the relevant holder inputs.

These signatures are discovery targets only. Their presence in a bytecode dump
does not prove that manually calling their constructors is safe or equivalent
to opening a real world.

## Acceptance criteria for a real fixture

1. The CI client starts an actual local integrated world using Minecraft's
   supported lifecycle.
2. The test waits until the client has a live `ClientLevel` and the required
   neighboring chunks are genuinely loaded.
3. Every `SectionCopy` is made from the corresponding real client chunk and
   section index; the 27-entry order is checked against the native index API.
4. The fixture asserts the target block and six neighboring block states
   through `RenderSectionRegion`.
5. Lighting and tint are read from the same live client world used by the
   region. No invented light values or fake `BlockAndTintGetter` are allowed.
6. The native model renderer capture is compared against vanilla section
   compilation before any production rendering change is considered.

## Explicit non-goals

Do not synthesize a `ClientLevel`, mock chunks, fabricate lighting, or claim
native parity from a startup-only smoke test. Do not enable a mixin, replace
vanilla section compilation, or activate the renderer during this investigation.
Unsupported blocks continue through vanilla rendering.
