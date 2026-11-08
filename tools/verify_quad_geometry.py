#!/usr/bin/env python3
"""Verify Minecraft 26.3 quad-shape preparation and its cache-safety limits."""
from pathlib import Path
import re
import sys

root = Path("build/void-fps-inspect/vanilla-bytecode")
lighter_path = root / "BlockModelLighter.bytecode.txt"
quad_path = root / "BakedQuad.bytecode.txt"

missing_files = [str(p) for p in (lighter_path, quad_path) if not p.is_file()]
if missing_files:
    sys.exit("Missing required vanilla bytecode dump(s): " + ", ".join(missing_files))

lighter = lighter_path.read_text(errors="replace")
quad = quad_path.read_text(errors="replace")

checks = {
    "BlockModelLighter.prepareQuadShape": [
        "private void prepareQuadShape",
        "BakedQuad.position:(I)Lorg/joml/Vector3fc;",
        "java/lang/Math.min:(FF)F",
        "java/lang/Math.max:(FF)F",
        "facePartial",
        "faceCubic",
        "BlockState.isCollisionShapeFullBlock",
    ],
    "BakedQuad storage": [
        "private final org.joml.Vector3fc position0;",
        "private final org.joml.Vector3fc position1;",
        "private final org.joml.Vector3fc position2;",
        "private final org.joml.Vector3fc position3;",
        "putfield      #7",
        "putfield      #13",
        "putfield      #16",
        "putfield      #19",
    ],
}
failed = False
for label, needles in checks.items():
    source = lighter if label.startswith("BlockModelLighter") else quad
    absent = [needle for needle in needles if needle not in source]
    if absent:
        print(f"FAIL: {label} missing bytecode evidence: {absent}")
        failed = True
    else:
        print(f"PASS: {label} contains all {len(needles)} expected bytecode markers")

constructor = re.search(
    r"public net\.minecraft\.client\.resources\.model\.geometry\.BakedQuad\(.*?"
    r"\n  public org\.joml\.Vector3fc position\(int\);",
    quad,
    re.DOTALL,
)
if constructor is None:
    print("FAIL: could not isolate BakedQuad constructor bytecode")
    failed = True
else:
    body = constructor.group(0)
    if "Vector3f.<init>" in body or "clone:" in body or "copy" in body.lower():
        print("FAIL: BakedQuad constructor appears to copy position vectors; review the bytecode")
        failed = True
    else:
        print("PASS: BakedQuad constructor stores Vector3fc references without a visible defensive copy")

if failed:
    sys.exit("Quad geometry inspection changed or is incomplete; do not assume a reusable geometry cache is safe.")

print("VOID FPS quad-geometry inspection passed: bounds are quad-derived, faceCubic is state-dependent, and position-vector immutability is not guaranteed by BakedQuad.")
