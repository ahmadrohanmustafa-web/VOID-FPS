# Remaining validation

The Minecraft 26.3 source/bytecode inspection is complete for the current face-visibility candidate.

## What the evidence established

Vanilla Minecraft 26.3 already caches block-face occlusion results in a thread-local 256-entry cache. The renderer delegates to that method. VOID FPS therefore does not add a duplicate cache or an unverified rendering mixin.

No other optimization has met the evidence and benchmark requirements. The current JAR is behavior-neutral and must not be advertised as an FPS boost.

## User-side tests still required

1. Install Java 25 and Minecraft Java Edition 26.3 with Fabric Loader 0.19.5 or newer.
2. Put the built `void-fps-0.1.0.jar` in the instance's `mods` folder.
3. Launch the game and check `logs/latest.log` for the VOID FPS startup line and any errors.
4. Load a test world and inspect blocks with different shapes and transparency.
5. Compare the same scene and settings with and without the mod.

Because this build does not change rendering, it is only a compatibility/load test. It is not a performance benchmark and no FPS improvement should be expected.

## Next development decision

Do not add a mixin merely to make the mod appear active. A new candidate must first be demonstrated in the Minecraft 26.3 source/bytecode as redundant work not already avoided by vanilla, then pass geometry-equivalence and repeatable performance tests.
