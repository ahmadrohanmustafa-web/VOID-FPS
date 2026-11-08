#!/usr/bin/env python3
"""Dump the real method signatures of Minecraft's block/terrain rendering classes.

VOID FPS must not guess mixin targets. Run this after a successful Gradle build/Loom setup
(so the Minecraft jar is in Loom's cache) and send the output files back for review.

Usage:
    python3 tools/inspect_targets.py                    # auto-find the Minecraft jar
    python3 tools/inspect_targets.py --jar path/to.jar  # or point at it explicitly
    python3 tools/inspect_targets.py --bytecode         # also dump javap -c for the key classes

Use the javap from JDK 25 (Minecraft 26.x classes are Java 25 bytecode): either put JDK 25's
bin directory first on PATH or pass --javap /path/to/jdk-25/bin/javap.

Only the Python standard library is used. Classes are located by simple name inside the jar,
so no package name is assumed.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

# Simple names of classes whose signatures we need. Located by name, not by package.
TARGET_SIMPLE_NAMES = [
    "ModelBlockRenderer",
    "SectionCompiler",
    "BlockQuadOutput",
    "BlockStateModelPart",
    "BlockModelLighter",
    "RenderSectionRegion",
    "SectionCopy",
    "FluidRenderer",
    "BlockStateModel",
    "ModelBaker",
    "BlockModel",
    "SingleVariant",
    "WeightedVariants",
    "QuadCollection",
    "BakedQuad",
    "ChunkSectionLayer",
    "BlockAndTintGetter",
    "BlockColors",
    "BlockTintSource",
    "Block",
    "BlockBehaviour",
    "BlockStateBase",
    "BlockState",
    "BlockEntityRenderDispatcher",
    "BlockEntityRenderer",
    "BlockEntity",
    "BlockEntityType",
    "Shapes",
    "VoxelShape",
    "BlockPos",
    "Vec3",
    # Resource/model reload APIs for real resource-pack compatibility tests.
    "Minecraft",
    "ModelManager",
    "BlockStateModelLoader",
    "ReloadableResourceManager",
    "ResourceManager",
    "Resource",
    "PackRepository",
    "PackResources",
    "PathPackResources",
    "FilePackResources",
    "PreparableReloadListener",
    "Pack",
    "PackLocationInfo",
    "PackMetadataSection",
    "FolderRepositorySource",
    "PackType",
    "Identifier",
    # Exact pack construction/metadata types used by the real folder-pack integration test.
    "PackSelectionConfig",
    "PackSource",
    "PackMetadataResources",
    "AbstractPackMetadataResources",
    "PackFormat",
    "InclusiveRange",
    "KnownPack",
    "Pack$ResourcesSupplier",
    "Pack$Metadata",
    "BlockStateModelSet",
    # Exact format/compatibility and world-version types for pack.mcmeta validation.
    "DirectoryValidator",
    "PackCompatibility",
    "WorldVersion",
    "SharedConstants",
    "Pack$Position",
]

# Classes that must be matched exactly by full internal path because the simple name is ambiguous.
EXACT_CLASSES = {
    "Block": "net/minecraft/world/level/block/Block.class",
    "BlockBehaviour": "net/minecraft/world/level/block/state/BlockBehaviour.class",
    "BakedQuad": "net/minecraft/client/resources/model/geometry/BakedQuad.class",
    "BlockQuadOutput": "net/minecraft/client/renderer/block/BlockQuadOutput.class",
    "BlockColors": "net/minecraft/client/color/block/BlockColors.class",
    "BlockTintSource": "net/minecraft/client/color/block/BlockTintSource.class",
    "BlockEntityRenderDispatcher": "net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher.class",
    "BlockEntityRenderer": "net/minecraft/client/renderer/blockentity/BlockEntityRenderer.class",
    "BlockEntity": "net/minecraft/world/level/block/entity/BlockEntity.class",
    "BlockEntityType": "net/minecraft/world/level/block/entity/BlockEntityType.class",
    "Shapes": "net/minecraft/world/phys/shapes/Shapes.class",
    "VoxelShape": "net/minecraft/world/phys/shapes/VoxelShape.class",
    "BlockPos": "net/minecraft/core/BlockPos.class",
    "Vec3": "net/minecraft/world/phys/Vec3.class",
    "Pack": "net/minecraft/server/packs/repository/Pack.class",
    "PackLocationInfo": "net/minecraft/server/packs/PackLocationInfo.class",
    "PackMetadataSection": "net/minecraft/server/packs/metadata/pack/PackMetadataSection.class",
    "FolderRepositorySource": "net/minecraft/server/packs/repository/FolderRepositorySource.class",
    "PackType": "net/minecraft/server/packs/PackType.class",
    "Identifier": "net/minecraft/resources/Identifier.class",
    "PackSelectionConfig": "net/minecraft/server/packs/PackSelectionConfig.class",
    "PackSource": "net/minecraft/server/packs/repository/PackSource.class",
    "PackMetadataResources": "net/minecraft/server/packs/PackMetadataResources.class",
    "AbstractPackMetadataResources": "net/minecraft/server/packs/AbstractPackMetadataResources.class",
    "PackFormat": "net/minecraft/server/packs/metadata/pack/PackFormat.class",
    "InclusiveRange": "net/minecraft/util/InclusiveRange.class",
    "KnownPack": "net/minecraft/server/packs/repository/KnownPack.class",
    "Pack$ResourcesSupplier": "net/minecraft/server/packs/repository/Pack$ResourcesSupplier.class",
    "Pack$Metadata": "net/minecraft/server/packs/repository/Pack$Metadata.class",
    "BlockStateModelSet": "net/minecraft/client/renderer/block/BlockStateModelSet.class",
    "DirectoryValidator": "net/minecraft/world/level/validation/DirectoryValidator.class",
    "PackCompatibility": "net/minecraft/server/packs/repository/PackCompatibility.class",
    "WorldVersion": "net/minecraft/WorldVersion.class",
    "SharedConstants": "net/minecraft/SharedConstants.class",
    "Pack$Position": "net/minecraft/server/packs/repository/Pack$Position.class",
}

# Lines in javap output that are worth highlighting in summary.md.
KEYWORDS = re.compile(
    r"tesselate|tessellate|shouldRenderFace|isFaceOccluded|skipRendering|compile|tint|colorInWorld|reload|resource|pack|findResources|getResource|listPacks|"
    r"getLightCoords|getShade|renderModel|addFace|cull|occlu|isSolidRender|"
    r"getFaceOcclusionShape|isCollisionShapeFullBlock|useShapeForLightOcclusion|"
    r"canOcclude|getVisualShape|getOcclusionShape|hasOffsetFunction|getOffset|offset|Cache|getRenderer|blockentity|blockEntity",
    re.IGNORECASE,
)

BYTECODE_CLASSES = [
    "ModelBlockRenderer", "SectionCompiler", "BlockModelLighter", "BlockQuadOutput",
    "BlockStateModelPart", "BlockStateModel", "ModelBaker", "BlockModel",
    "SingleVariant", "WeightedVariants", "QuadCollection", "BlockAndTintGetter", "BlockColors", "BlockTintSource",
    "Block", "BlockBehaviour", "BlockStateBase", "BlockState",
    "BlockEntityRenderDispatcher", "BlockEntityRenderer", "BlockEntity", "BlockEntityType",
    "Shapes", "VoxelShape", "BlockPos", "Vec3",
    "Minecraft", "ModelManager", "BlockStateModelLoader", "ReloadableResourceManager",
    "ResourceManager", "Resource", "PackRepository", "PackResources",
    "PathPackResources", "FilePackResources", "PreparableReloadListener",
    "Pack", "PackLocationInfo", "PackMetadataSection", "FolderRepositorySource",
    "PackType", "Identifier", "PackSelectionConfig", "PackSource",
    "PackMetadataResources", "AbstractPackMetadataResources", "PackFormat",
    "InclusiveRange", "KnownPack", "Pack$ResourcesSupplier", "Pack$Metadata",
    "BlockStateModelSet", "DirectoryValidator", "PackCompatibility",
    "WorldVersion", "SharedConstants", "Pack$Position"
]


def default_search_roots() -> list[Path]:
    home = Path.home()
    roots = [
        home / ".gradle" / "caches" / "fabric-loom",
        Path(".gradle") / "loom-cache",
        Path(".gradle"),
        home / ".gradle" / "caches" / "modules-2",
    ]
    gradle_home = os.environ.get("GRADLE_USER_HOME")
    if gradle_home:
        roots.insert(0, Path(gradle_home) / "caches" / "fabric-loom")
        roots.append(Path(gradle_home) / "caches" / "modules-2")
    return [r for r in roots if r.exists()]


def jar_has_marker(jar: Path) -> bool:
    try:
        with zipfile.ZipFile(jar) as zf:
            return any(n.endswith("/ModelBlockRenderer.class") and n.startswith("net/minecraft/")
                       for n in zf.namelist())
    except (zipfile.BadZipFile, OSError):
        return False


def find_minecraft_jar(roots: list[Path]) -> Path | None:
    candidates: list[Path] = []
    for root in roots:
        for path in root.rglob("*.jar"):
            name = path.name.lower()
            if name.endswith("-sources.jar"):
                continue
            candidates.append(path)
    # Prefer jars that look like Minecraft jars, largest first (merged/client jars are big).
    candidates.sort(key=lambda p: ("minecraft" not in p.name.lower(), -p.stat().st_size))
    for jar in candidates:
        if jar_has_marker(jar):
            return jar
    return None


def resolve_classes(jar: Path) -> dict[str, list[str]]:
    """Map each target simple name to the jar entries (without .class) that belong to it."""
    found: dict[str, list[str]] = {name: [] for name in TARGET_SIMPLE_NAMES}
    with zipfile.ZipFile(jar) as zf:
        names = [n for n in zf.namelist() if n.startswith("net/minecraft/") and n.endswith(".class")]
    for simple in TARGET_SIMPLE_NAMES:
        if simple in EXACT_CLASSES:
            exact = EXACT_CLASSES[simple]
            base = exact[: -len(".class")]
            matches = [n[: -len(".class")] for n in names
                       if n == exact or n.startswith(base + "$")]
        else:
            matches = []
        if not matches:
            # Not at the expected path (or not an exact-path class): fall back to simple-name search.
            for n in names:
                stem = n[: -len(".class")]
                last = stem.rsplit("/", 1)[-1]
                if last == simple or last.startswith(simple + "$"):
                    matches.append(stem)
        # Skip anonymous classes such as Foo$1; they are never mixin targets worth listing.
        matches = [m for m in matches if not re.search(r"\$\d+$", m)]
        found[simple] = sorted(set(matches))
    return found


def run_javap(javap: str, jar: Path, internal_name: str, bytecode: bool) -> str:
    fqcn = internal_name.replace("/", ".")
    cmd = [javap, "-p"]
    if bytecode:
        cmd.append("-c")
    cmd += ["-classpath", str(jar), fqcn]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        return f"// javap FAILED for {fqcn} (exit {proc.returncode})\n// {proc.stderr.strip()}\n"
    return proc.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", type=Path, help="Minecraft jar to inspect (auto-detected if omitted)")
    parser.add_argument("--javap", default="javap", help="javap executable (use JDK 25's)")
    parser.add_argument("--out", type=Path, default=Path("build") / "void-fps-inspect",
                        help="output directory (default: build/void-fps-inspect)")
    parser.add_argument("--bytecode", action="store_true",
                        help="also write javap -c output for ModelBlockRenderer and SectionCompiler")
    args = parser.parse_args()

    jar = args.jar
    if jar is None:
        roots = default_search_roots()
        jar = find_minecraft_jar(roots)
        if jar is None:
            print("ERROR: could not find a Minecraft jar containing ModelBlockRenderer.\n"
                  "Run a Gradle build first so Loom downloads Minecraft, or pass --jar.\n"
                  f"Searched: {[str(r) for r in roots] or 'no existing Gradle cache directories'}",
                  file=sys.stderr)
            return 2
    elif not jar_has_marker(jar):
        print(f"ERROR: {jar} does not contain net/minecraft/**/ModelBlockRenderer.class", file=sys.stderr)
        return 2

    args.out.mkdir(parents=True, exist_ok=True)
    classes = resolve_classes(jar)

    sig_path = args.out / "signatures.txt"
    summary_path = args.out / "summary.md"
    missing = [name for name, entries in classes.items() if not entries]

    summary_lines = [
        "# VOID FPS target inspection",
        "",
        f"- Jar: `{jar}`",
        f"- javap: `{args.javap}`",
        f"- Classes not found by simple name: {', '.join(missing) if missing else 'none'}",
        "",
        "Lines below match rendering-related keywords. They are leads, not proof of a safe hook.",
        "",
    ]

    with sig_path.open("w", encoding="utf-8") as sig:
        sig.write(f"// Jar: {jar}\n\n")
        for simple in TARGET_SIMPLE_NAMES:
            for internal in classes[simple]:
                text = run_javap(args.javap, jar, internal, bytecode=False)
                sig.write(f"// ===== {internal.replace('/', '.')} =====\n{text}\n")
                hits = [ln.strip() for ln in text.splitlines() if KEYWORDS.search(ln)]
                if hits:
                    summary_lines.append(f"## {internal.replace('/', '.')}")
                    summary_lines.append("```")
                    summary_lines.extend(hits)
                    summary_lines.append("```")
                    summary_lines.append("")
    summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

    written = [sig_path, summary_path]
    if args.bytecode:
        # Keep short filenames for unique classes; disambiguate duplicate simple names.
        basename_counts: dict[str, int] = {}
        for entries in classes.values():
            for internal in entries:
                basename = internal.rsplit("/", 1)[-1]
                basename_counts[basename] = basename_counts.get(basename, 0) + 1
        for simple in BYTECODE_CLASSES:
            for internal in classes.get(simple, []):
                if re.search(r"\$\d+$", internal):
                    continue
                basename = internal.rsplit("/", 1)[-1]
                filename = (
                    f"{internal.replace('/', '_')}.bytecode.txt"
                    if basename_counts.get(basename, 0) > 1
                    else f"{basename}.bytecode.txt"
                )
                path = args.out / filename
                path.write_text(run_javap(args.javap, jar, internal, bytecode=True), encoding="utf-8")
                written.append(path)

    print("Wrote:")
    for p in written:
        print(f"  {p}")
    if missing:
        print(f"Note: not found by simple name: {', '.join(missing)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
