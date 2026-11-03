# VOID FPS

Client-side Fabric mod aimed at reducing **CPU-side** block and terrain rendering work in Minecraft Java
Edition **26.3**, with low-end hardware (for example Intel HD Graphics 520) as the target.

> **Current status: scaffold. No optimization is implemented or active.**
> The mod loads, logs one line, and changes nothing. This is deliberate: no Minecraft 26.3 mixin target
> could be verified in the environment this was produced in, and unverified mixins would be guesses.

## What is in this repository

| Path | Status |
|---|---|
| `build.gradle`, `settings.gradle`, `gradle.properties` | Written from sources listed in `docs/INVESTIGATION.md`. **Never executed.** |
| `gradle/wrapper/gradle-wrapper.properties` | Present. `gradlew`, `gradlew.bat` and the wrapper JAR are **missing**; generate them (see below). |
| `src/main/resources/fabric.mod.json` | Client-only, depends on Loader >= 0.19.5, Minecraft ~26.3, Java >= 25. **Never loaded.** |
| `src/main/java/dev/voidfps/**` | Client entrypoint that logs a status line. Not compiled here (no JDK 25). |
| `tools/inspect_targets.py` | Dumps real 26.3 method signatures from the Minecraft jar. Logic tested against a synthetic jar only. |
| `docs/INVESTIGATION.md` | What was researched, what is verified, what vanilla already does. |
| `docs/NEXT_STEPS.md` | Exactly what is needed to implement the real optimizations. |
| `docs/templates/voidfps.mixins.json` | Mixin config template, intentionally **not** registered (no mixins exist). |
| `.github/workflows/build.yml` | Untested CI that builds and uploads the signature dump. |

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Java 25
- Fabric API is **not** required

## Build

    gradle wrapper --gradle-version 9.6.0   # once; creates gradlew and the wrapper JAR
    ./gradlew build                          # output: build/libs/void-fps-0.1.0.jar

Build result so far: **not run.** Runtime testing: **not performed.** Benchmarks: **none exist.**

## Scope (fixed)

Only block and terrain rendering. Out of scope: entities, particles, HUD/overlays, keybinds, automatic
setting changes, chunk scheduling, startup/GUI optimization, gameplay, and anything that lowers visual
quality. No configuration screen; the mod works automatically once installed.

## Honest performance expectations

None can be stated. Vanilla already culls hidden faces and caches light/AO inputs, so the remaining headroom
may be small. CPU-side changes in this mod cannot speed up a GPU-bound scene; on HD Graphics 520 the GPU is
often the limit. Any future optimization will be kept only if it shows a measured CPU-side gain with
unchanged geometry; otherwise it will be removed.

## License

MIT, see `LICENSE`. The copyright holder line ("VOID FPS contributors") is a placeholder; change it.
