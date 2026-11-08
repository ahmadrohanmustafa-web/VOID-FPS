#!/usr/bin/env python3
"""Assert the Minecraft 26.3 caches that VOID FPS must not redundantly reimplement."""
from pathlib import Path
import sys

root = Path("build/void-fps-inspect")
checks = {
    "Block.bytecode.txt": [
        "OCCLUSION_CACHE", "ThreadLocal", "Object2ByteLinkedOpenHashMap",
        "getAndMoveToFirst", "putAndMoveToFirst", "sipush        256", "removeLastByte",
    ],
    "BlockModelLighter$Cache.bytecode.txt": [
        "Long2IntLinkedOpenHashMap", "Long2FloatLinkedOpenHashMap",
        "getLightCoords", "getShadeBrightness", "bipush        100",
        "removeFirstInt", "removeFirstFloat",
    ],
    "SectionCompiler.bytecode.txt": [
        "BlockModelLighter.enableCaching", "BlockModelLighter.clearCache",
    ],
}
failed = False
for filename, needles in checks.items():
    path = root / filename
    if not path.is_file():
        print(f"FAIL: required bytecode dump missing: {path}")
        failed = True
        continue
    content = path.read_text(errors="replace")
    missing = [needle for needle in needles if needle not in content]
    if missing:
        print(f"FAIL: {filename} missing expected cache evidence: {missing}")
        failed = True
    else:
        print(f"PASS: {filename} confirms {len(needles)} vanilla cache details")
if failed:
    sys.exit("Vanilla cache inspection incomplete or changed; review bytecode before proposing duplicate caches.")
print("VOID FPS vanilla-cache inspection passed.")
