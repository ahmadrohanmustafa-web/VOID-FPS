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
