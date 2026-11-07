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

## Build #237: actual create action and confirmation path

Build #237 passed. Inspecting the artifact's full bytecode clarified the
remaining UI step:

- `CreateWorldScreen.onCreate()` is private. It bakes the selected dimensions,
  builds `LevelSettings`, `PrimaryLevelData`, `WorldGenSettings`, and
  `WorldDataAndGenSettings`, then calls
  `WorldOpenFlows.confirmWorldCreation(...)`.
- For stable world data, `confirmWorldCreation` immediately runs the normal
  create callback. Experimental or deprecated registry lifecycle states show
  a `ConfirmScreen` instead.
- The test-world callback ultimately calls
  `CreateWorldScreen.createNewWorld(...)`. That method creates the save
  access, copies datapacks, and calls
  `WorldOpenFlows.createLevelFromExistingSettings(...)`.
- The create-world screen has a regular button action wired to `onCreate()`.
  This is the safest automation target: drive the normal button callback
  after the test-world screen is fully initialized, rather than directly
  constructing a `ClientLevel` or integrated server.

Correction/precision: `CreateWorldScreen.testWorld(...)` passes the
`FLAT_ALL_DIMENSIONS` preset key to the screen, while its worldgen supplier
uses `WorldPresets.createTestWorldDimensions(...)`. It should be described
as Minecraft's built-in test-world flow, not assumed to be the ordinary
custom-flat preset.

## Next implementation boundary

The next CI work should inspect the exact button/widget access and confirmation
screen callbacks, then add a test-only driver with a strict timeout. It must
wait for the test-world screen's asynchronous datapack/registry preparation,
activate the normal Create button, handle only the expected stable/known
confirmation state, and fail closed on any unexpected screen.

The test must run with a unique world name under an isolated temporary game
directory. After the world opens, wait for `Minecraft.hasSingleplayerServer()`,
a non-null `ClientLevel`, and actual loaded chunks before inspecting
`RenderSectionRegion` and lighting. Shutdown the integrated server through
Minecraft's normal lifecycle before deleting the temporary directory.

No production mixin, renderer activation, or FPS claim is justified by these
API-inspection results alone.


## Builds #239–#240: verified normal Create button callback

Builds #239 and #240 passed. Build #240's Minecraft 26.3 bytecode confirms:

- `CreateWorldScreen.init()` creates the Create button with a `Button.OnPress`
  callback. The generated callback is `lambda$init$0(Button)`, and its body
  calls the screen's private `onCreate()` method.
- `Screen.children()` returns the screen's GUI event-listener children.
  The Create button is registered as a widget during the screen's normal
  layout initialization.
- `Button.onPress(InputWithModifiers)` invokes the button's stored
  `Button.OnPress` callback. It does not independently recreate world
  creation logic; it reaches the same callback used by the button.
- `AbstractWidget.mouseClicked(...)` is a separate mouse-event route. The
  `Button.onPress(...)` method is a simpler test-driver entry point, but its
  input parameter and enabled/active-state behavior must be checked before
  choosing it.

This means a test-only driver can likely locate the Create button among the
screen's children and invoke its normal callback, without reflecting into the
private CreateWorldScreen.onCreate() method. Do not implement this until the
`AbstractButton` and `InputWithModifiers` APIs are confirmed and the driver
checks the button is active and visible.

The CI workflow now also dumps `AbstractButton`, `Button.OnPress`, and
`InputWithModifiers` bytecode for that final check.


## Builds #241–#242: button activation API boundary

Builds #241 and #242 passed. The Build #242 bytecode verifies:

- `AbstractButton.onPress(InputWithModifiers)` is abstract; `Button`
  implements it by invoking its stored `Button.OnPress` callback.
- `AbstractButton.onClick(MouseButtonEvent, boolean)` forwards directly to
  `onPress(...)`. Calling this method directly would bypass the screen's
  normal hit-testing and active-state checks, so it is not the preferred
  automation route.
- `AbstractButton.keyPressed(KeyEvent)` checks `isActive()` and only invokes
  the callback for a selection key. This is closer to normal input handling,
  but requires a correctly constructed key event and focused widget.
- `ConfirmScreen` builds its own yes/no buttons and can delay their activation
  through `setDelay(...)`; a test driver must not blindly press a confirmation
  button or accept an unexpected confirmation screen.

The next inspection adds `Screen`, `MouseButtonEvent`, `KeyEvent`, and the
widget/container event-handler classes. The intended route is to dispatch a
real screen input event at the located active Create button, allowing vanilla
hit-testing to choose the widget. Direct callback invocation remains a
fallback only if normal event dispatch cannot be made deterministic.


## Build #243: vanilla screen mouse-event dispatch verified

Build #243 passed. Its Minecraft 26.3 bytecode confirms the normal dispatch
path is inherited by `Screen` from `ContainerEventHandler`:

- `Screen.children()` exposes the registered GUI event listeners.
- `ContainerEventHandler.getChildAt(x, y)` searches those children using
  each listener's `isMouseOver(x, y)` hit-test.
- `ContainerEventHandler.mouseClicked(event, doubleClick)` dispatches to
  the hit-tested child. If that child handles the event and requests focus,
  the container updates focus; it also updates drag state for the relevant
  mouse button.
- `AbstractWidget.mouseClicked(...)` checks the widget's active state and
  valid click button before invoking its click behavior.

Therefore the preferred driver is to find the actual Create button among the
expected `CreateWorldScreen` children, verify it is visible and active, and
send a primary-button event through the screen's inherited
`mouseClicked(...)` dispatch. This preserves vanilla hit-testing and widget
checks. Do not call the button's callback or `onClick(...)` directly.

The `MouseButtonEvent` constructor takes screen-space x/y coordinates and a
`MouseButtonInfo`; Build #243 did not include the latter class's bytecode.
Build #245 adds that final constructor inspection. Coordinates must be the
button center in the screen's GUI coordinate system, not raw window pixels.

This is still API verification, not a working world-creation driver. The
test must also prove that the button is uniquely identified after asynchronous
screen preparation, that the expected screen remains current, and that
world-open/chunk/lighting waits and orderly teardown work. Unexpected
confirmation screens remain fail-closed.

No production rendering changes are authorized by this finding.


## Builds #245–#246: real mouse event and Create button identity

Builds #245 and #246 passed. The Minecraft 26.3 bytecode confirms:

- `MouseButtonInfo(int button, int modifiers)` is the metadata record.
  The primary mouse button is index `0`; no modifiers is `0`.
- `MouseButtonEvent(double x, double y, MouseButtonInfo)` carries GUI
  screen-space coordinates and exposes the button and modifier values.
- `ContainerEventHandler.mouseClicked(event, doubleClick)` uses
  `getChildAt(x, y)`, then dispatches to the hit-tested child. The widget's
  own `mouseClicked` checks active state, valid button, and hover bounds.
- `CreateWorldScreen` builds its Create button with the translatable key
  `selectWorld.create`; its callback is the normal `onCreate()` path.
  The Cancel button is a separate sibling.
- `Screen.children()` exposes the registered event listeners. The Create
  button is added through the normal layout widget registration.

The test driver should identify exactly one widget by its translatable
component key, require it to be a visible and active `Button`, and verify
the click center resolves back to that same widget through
`getChildAt` before dispatch. This extra identity check matters because
`getChildAt` returns the first overlapping child in list order. Only then
may it send `new MouseButtonEvent(centerX, centerY,
new MouseButtonInfo(0, 0))` through the expected current
`CreateWorldScreen`.

The current test entrypoint still runs only the native vertex and
resource-loaded stone-model checks and then stops the client. No world has
been created yet. Before adding the driver, the next inspection verifies the
component's translatable-key accessor and GUI window coordinate semantics.
The test run must continue to use its isolated `run/test-client` directory;
production entrypoints and the published JAR remain unchanged.

No production rendering changes or FPS claims follow from these API findings.


## Build #249: GUI coordinate semantics verified

Build #249 passed. The corrected bytecode inspection targets
`com.mojang.blaze3d.platform.Window` (the actual Minecraft window class) and
`net.minecraft.client.MouseHandler`.

Verified APIs and behavior:

- `Window.getGuiScaledWidth()` and `getGuiScaledHeight()` expose the
  screen's GUI-scaled dimensions.
- `Window.getScreenWidth()` and `getScreenHeight()` expose window screen
  dimensions; `getWidth()` / `getHeight()` instead return framebuffer
  dimensions.
- Vanilla `MouseHandler` converts physical mouse positions into GUI
  coordinates before constructing `MouseButtonEvent`. The conversion is
  `physicalX * guiScaledWidth / screenWidth` and
  `physicalY * guiScaledHeight / screenHeight`.
- The event is then dispatched to the current `Screen.mouseClicked(...)`.
  This confirms that widget coordinates and manually constructed
  `MouseButtonEvent` coordinates must both be GUI-scaled coordinates.
- `Component.getContents()` is the public content accessor, and
  `TranslatableContents.getKey()` is the public stable translation-key
  accessor. The Create button identity check can use
  `getMessage().getContents() instanceof TranslatableContents` followed by
  `getKey().equals("selectWorld.create")`.

Driver safety requirements remain unchanged: verify the current screen is the
expected CreateWorldScreen, exactly one visible/active Create button matches,
the button center hit-tests to that same child, and dispatch a primary click
through the screen. Wait for the asynchronous world-creation screen to be
ready; fail closed on unexpected screens. This is API verification only:
Build #249 did not create a world or validate chunk/lighting fixtures.


## Build #249 follow-up: test-world screen readiness and creation callback

Further inspection of the same Build #249 artifact confirms:

- `CreateWorldScreen.testWorld(minecraft, callback)` queues Minecraft's
  "preparing world data" loading screen, loads the required datapack/registry
  context, and only then installs the `CreateWorldScreen` on the client's
  GUI. The screen is not ready merely because `testWorld(...)` was called.
- The screen's `init()` builds its widgets; the driver must wait until the
  expected screen is current and the widget list has been initialized before
  searching for the Create button.
- The Create button's callback reaches private `onCreate()`, which constructs
  the normal world settings and passes them through
  `WorldOpenFlows.confirmWorldCreation(...)`.
- The built-in test-world callback ultimately invokes the screen's
  `createNewWorld(...)` path. This uses the normal save creation and
  integrated-server startup flow; do not replace it with a fabricated
  `ClientLevel` or manually injected chunks.
- The built-in test-world flow uses the test-world preset/dimensions and a
  random test-world seed. It is a dedicated Minecraft test flow, not a normal
  user save.

The remaining implementation work is a test-only state machine: request the
test-world flow once; wait with a deadline for the expected screen and unique
Create widget; validate visibility, active state, and hit-test identity; send
the vanilla primary-button event; then wait for the integrated server, client
level, and real loaded chunks. Any unexpected screen or timeout must fail the
test and shut down cleanly. The disposable test client directory remains
isolated at `run/test-client`.

No real test world has yet been opened by CI, and no native section-region
fixture has yet been built from live chunks.
