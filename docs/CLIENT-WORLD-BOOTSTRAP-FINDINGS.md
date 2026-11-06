# Client-world bootstrap findings (Minecraft 26.3)

## CI status

Build #229 passed on `89cc8cb98c6cae860ca4d96e329e35c19c8ef639`.
Build #230 passed on `859e97b5f7c563d45a77b0b2211c8f1456024230`.

- [Build #229](https://github.com/ahmadrohanmustafa-web/VOID-FPS/actions/runs/42217942246)
- [Build #230](https://github.com/ahmadrohanmustafa-web/VOID-FPS/actions/runs/42217947452)

## New bytecode findings

The expanded Minecraft 26.3 dump confirms:

- `Minecraft.createWorldOpenFlows()` is the normal world-opening entry point.
- `Minecraft.doWorldLoad(...)` is part of the normal world-load lifecycle.
- `Minecraft` exposes `hasSingleplayerServer()` and `getSingleplayerServer()`.
- `ClientPacketListener.handleLogin(...)` creates the live `ClientLevel`, installs it through `Minecraft.setLevel(...)`, and initializes the normal client game mode.
- `ClientChunkCache` exposes `getChunk(x, z, status, load)`, `getLoadedChunksCount()`, and `getLightEngine()`.
- `IntegratedServer` is constructed as part of the single-player lifecycle, not as a standalone shortcut.

This supports using the real single-player world-opening flow for the fixture. It does not yet prove which automated API can safely create a disposable world without UI interaction.

## Next investigation

Inspect `WorldOpenFlows`, `CreateWorldScreen`, `LevelStorageSource`,
`MinecraftServer`, `WorldStem`, and the relevant world-generation/bootstrap
types. Identify the smallest supported test-only route to create/open a
disposable flat world through the real lifecycle.

Do not directly instantiate `ClientPacketListener` or `ClientLevel`, and do
not hand-build an `IntegratedServer`. Those are lifecycle-managed objects.
After a real world is open, wait for actual chunk delivery and lighting before
building the section-region fixture.

## Safety gate

No production mixins or renderer activation. This investigation remains test
infrastructure only; all production block rendering stays vanilla.

## Follow-up: verified creation path from Build #232

Builds #231 and #232 passed. Build #232's artifact contains the Minecraft 26.3
bytecode for the world-opening classes. The exact `WorldOpenFlows.createFreshLevel(...)` signature is:

```java
createFreshLevel(
    String levelId,
    LevelSettings settings,
    WorldOptions options,
    Function<HolderLookup.Provider, WorldDimensions> dimensionsFactory,
    Screen returnScreen
)
```


Its bytecode confirms this is not a fake-world shortcut: it creates a
`LevelStorageAccess` through `LevelStorageSource.validateAndCreateAccess(...)`,
loads the selected datapacks and world data, builds a `WorldStem`,
then calls `Minecraft.doWorldLoad(...)` to enter the normal integrated
single-player lifecycle. `WorldOptions.testWorldWithRandomSeed()`
also exists in this target version, but the exact flat-dimension factory and
`LevelSettings` construction still need inspection.

Important limitation: `createFreshLevel(...)` changes the active screen,
creates a world save directory, and performs world loading. The CI test must
therefore use an isolated temporary game directory and a unique test-world ID;
it must not touch a player's normal saves. Cleanup must happen only after the
server and storage access have shut down cleanly.

## Next test-only inspection

Build #233 expands bytecode coverage for `LevelSettings`,
`WorldDimensions`, `WorldLoader`, `WorldCreationContext`,
`ServerPacksSource`, `LevelDataAndDimensions`, and related
configuration types. Once those constructors/factories are confirmed, add a
test-only client bootstrap that calls the verified normal flow, waits for a
real `ClientLevel` and loaded chunks, then checks the section region and
lighting. Keep the production renderer disabled until that real-world test
and native vertex parity checks pass.

## Build #233 bytecode results

Build #233 passed and its artifact confirms these Minecraft 26.3 APIs:

- `LevelSettings` has a public constructor taking the world name, game type,
  difficulty settings, command permission flag, and `WorldDataConfiguration`.
- `WorldOptions.defaultWithRandomSeed()` and
  `WorldOptions.testWorldWithRandomSeed()` are available.
- `WorldDimensions` can be created from the dimension-stem registry or a
  dimension map, and supports replacing the overworld generator.
- `WorldPresets.FLAT` is a registered preset key; `WorldPresets.fromSettings`
  can identify a preset from dimensions.
- `FlatLevelGeneratorSettings.getDefault(...)` accepts biome, structure-set,
  and placed-feature lookups. Its constructor is public when supplied a biome
  holder and placed-feature list.
- `LevelStorageSource.createDefault(...)` and
  `validateAndCreateAccess(...)` are available. The resulting
  `LevelStorageAccess` exposes `safeClose()` / `close()` and
  `deleteLevel()`; cleanup must happen only after the integrated server has
  fully stopped and the access has been closed.
- `WorldCreationContext` has constructors for assembled world-generation
  settings and for options/dimensions plus the layered registries and
  datapack resources.

The flat generator source's exact constructor and the nested loader types are
being added to the next bytecode dump before any bootstrap code is written.
The current CI artifact also still only proves title-screen startup and the
existing native model/vertex tests; it has not opened a world.

## Test-world isolation requirements

The future test must use a dedicated temporary game directory and a unique
world ID. It must never reuse the developer's normal saves directory. It must
wait for the normal integrated-server shutdown before closing storage and
removing the disposable save. If the client cannot reliably complete this
lifecycle under CI, retain the API inspection tests and do not fake a
`ClientLevel`, chunks, or lighting.

## Build #235 bytecode inspection: flat generator and built-in test preset

Build #235 passed. The artifact verifies:

- `FlatLevelSource(FlatLevelGeneratorSettings)` is the actual flat chunk
  generator constructor.
- `WorldDimensions.replaceOverworldGenerator(provider, generator)` can
  replace the overworld generator while retaining the other dimensions.
- `WorldCreationContext` carries the options, selected dimensions,
  world-generation registries, server datapack resources, and data
  configuration required by the normal create-world flow.
- `LevelStorageAccess` implements `AutoCloseable`; its public
  `safeClose()`, `close()`, and `deleteLevel()` operations make orderly
  cleanup possible.
- `CreateWorldScreen.testWorld(minecraft, callback)` is a vanilla-provided
  test-world entry point. Its bytecode selects
  `WorldPresets.FLAT_ALL_DIMENSIONS` and prepares the normal world-creation
  screen and datapack/registry bootstrap.

Important finding: `CreateWorldScreen.testWorld(...)` does **not** itself
start a world. It opens the world-creation UI; the create action is still
performed later by the screen's `onCreate()` path. We must not mistake this
method for an automatic headless fixture. A CI test would need a verified,
test-only way to drive the screen's normal create action (or a separate
supported API path), with a dedicated temporary game directory and orderly
server/storage cleanup.

The bytecode also shows the test-world callback ultimately delegates to the
screen's regular `createWorldAndCleanup(...)` path. That is preferable to
hand-constructing a client world, but UI automation and teardown behavior
still need to be proven before enabling it in CI.
