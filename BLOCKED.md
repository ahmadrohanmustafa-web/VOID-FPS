# Current implementation boundary

VOID FPS is a client-side Minecraft 26.3 investigation project. The shipped mod remains behavior-neutral: it logs its status and does not replace block or terrain rendering. No FPS gain is claimed.

## Verified in CI

- The Minecraft 26.3 client boots in the test environment under Xvfb.
- Test-only fixtures create a disposable world, load a real client chunk, place/restore test blocks, and complete the save/disconnect lifecycle.
- Native resource-loaded block models are collected through Minecraft's model manager. Resource-pack override tests reload real temporary folder packs and verify conservative eligibility/fallback decisions.
- The test harness invokes Minecraft's real `ModelBlockRenderer` path for live-world fixtures and captures ordered native vertex records. Repeated vanilla captures are checked for exact equality.
- The harness compares the candidate's selected native quads and per-block packed vertex output against vanilla for supported full-cube fixtures, neighbour-culling cases, renderer AO/culling flag combinations, and several deterministic model seeds.
- A separate section-region test captures the real vanilla `SectionCompiler` mesh and checks its vertex format, stride, byte contents, metadata, and repeat-capture stability. This is a vanilla section-mesh capture, not a candidate-generated replacement mesh.
- Candidate and vanilla block captures are also written through Minecraft's native `BufferBuilder` into real `MeshData` using the SOLID layer's vertex format; draw metadata and every vertex byte are compared exactly.
- The section shadow traversal concatenates all eligible-block vertex records in section traversal order, validates contiguous per-block quad ranges, writes the complete eligible-only substream through `BufferBuilder`, and compares the resulting vanilla/candidate `MeshData` exactly. Unsupported blocks remain classified for vanilla fallback and are not included in this candidate substream.
- Native vertex parity comparison rejects null captures, null records, invalid tolerances, non-finite vertex fields, count mismatches, ordering changes, and packed integer-field differences.

## Still required before a production optimization

1. **Complete candidate section compilation.** Extend the eligible-only `MeshData` proof into a section compiler that also routes unsupported blocks to vanilla exactly once, handles every render layer and fluid path, and preserves native ordering/metadata.
2. **Whole-section parity.** Compare the complete vanilla and candidate section meshes using the same world, block states, model seeds, section origin, layer routing, lighting/AO settings, and neighbours. Compare vertex bytes and all relevant mesh metadata; unsupported blocks must remain vanilla-only with no duplicate or missing output.
3. **Broader fallback proof.** Confirm partial shapes, internal geometry, unculled/multi-quad faces, tint, animated/emissive materials, non-solid layers, block entities, offsets, fluids, and resource-pack overrides are rejected or routed to vanilla before candidate output.
4. **Visual and performance validation.** Check visual equivalence, then benchmark repeatably on the same scene and hardware. A candidate that is not measurably faster must not ship as an optimization.
5. **Only after all gates pass:** consider a narrowly scoped, verified production integration point. Never enable an unverified renderer replacement just to make the mod appear active.

## Why the original face-culling idea is not enough

Minecraft 26.3 already caches face-occlusion checks and reuses several lighting/tint calculations. Reimplementing those paths can add overhead or change behaviour. The remaining investigation is limited to proving whether a native-output-compatible geometry path can remove genuinely redundant work.

## Safety rules for the project

- Keep production rendering disabled until whole-section parity and repeatable performance evidence pass.
- Preserve vanilla rendering for every unsupported or uncertain block/model.
- Do not weaken tests, skip difficult fixtures, or infer parity from screenshots or a successful client launch.
- Keep test-only bootstrap, capture, and fixture code out of the shipped JAR.
