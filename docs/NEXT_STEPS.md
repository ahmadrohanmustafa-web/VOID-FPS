# What is needed next (in order)

Nothing below can be done by guessing. Each step unlocks the next one.

## 1. Build the skeleton and report the result

1. Install **JDK 25** (Temurin or similar) and make sure `java -version` says 25.
2. Install Gradle 9.6.0 once (or use any Gradle 9.x), then generate the wrapper, which this repo cannot ship
   (it needs a binary JAR that could not be produced here):

       gradle wrapper --gradle-version 9.6.0

   That creates `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`. Commit them.
3. Run `./gradlew build` and send me the **full** output if it fails. Likely failure points, since the build
   has never been run: the Loom artifact string in `gradle.properties`, and the `minecraft` / `implementation`
   dependency lines in `build.gradle`.
4. Run `./gradlew runClient` and send `run/logs/latest.log` (the line starting `VOID FPS loaded` should appear).

## 2. Dump the real 26.3 signatures

    python3 tools/inspect_targets.py --bytecode

Use JDK 25's `javap` (`--javap /path/to/jdk-25/bin/javap` if needed). Send these files:

- `build/void-fps-inspect/signatures.txt`
- `build/void-fps-inspect/summary.md`
- `build/void-fps-inspect/ModelBlockRenderer.bytecode.txt`
- `build/void-fps-inspect/SectionCompiler.bytecode.txt`

Alternative with no local setup: push to GitHub and download the `void-fps-build` artifact from the
Actions run (`.github/workflows/build.yml`, untested). It contains the same files.

## 3. Send the decompiled source of the hot path

Run `./gradlew genSources`, open the 26.3 sources in your IDE and paste (or attach) the full text of:

- `ModelBlockRenderer` (including its `Cache`)
- `SectionCompiler`
- whichever method `summary.md` shows as deciding whether a block face is rendered (the
  `shouldRenderFace`-style method, wherever it lives in 26.3)

## 4. Then, and only then

With the above I can: decide whether each candidate in `docs/INVESTIGATION.md` section D is redundant,
write the mixins with exact descriptors, register `voidfps.mixins.json` (template in `docs/templates/`),
add an A/B toggle and tests, and give you a benchmark protocol that you run on the HD Graphics 520 machine.

## Things that stay your job

- Launching Minecraft 26.3, loading worlds, comparing visuals with and without the mod.
- Before/after measurements on your hardware. No numbers exist for this project.
