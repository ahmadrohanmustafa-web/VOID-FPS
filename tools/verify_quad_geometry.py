#!/usr/bin/env python3
"""Verify Minecraft 26.3 quad-shape preparation and its cache-safety limits."""
from pathlib import Path
import re
import sys

inspection_root = Path("build/void-fps-inspect")
vanilla_root = inspection_root / "vanilla-bytecode"
lighter_path = vanilla_root / "BlockModelLighter.bytecode.txt"
quad_path = vanilla_root / "BakedQuad.bytecode.txt"
collection_path = inspection_root / "QuadCollection.bytecode.txt"
builder_path = inspection_root / "QuadCollection$Builder.bytecode.txt"
interner_path = inspection_root / "ModelBakery$InternerImpl.bytecode.txt"
face_bakery_path = inspection_root / "FaceBakery.bytecode.txt"

required_paths = (lighter_path, quad_path, collection_path, builder_path, interner_path, face_bakery_path)
missing_files = [str(path) for path in required_paths if not path.is_file()]
if missing_files:
    sys.exit("Missing required vanilla bytecode dump(s): " + ", ".join(missing_files))

lighter = lighter_path.read_text(errors="replace")
quad = quad_path.read_text(errors="replace")
collection = collection_path.read_text(errors="replace")
builder = builder_path.read_text(errors="replace")
interner = interner_path.read_text(errors="replace")
face_bakery = face_bakery_path.read_text(errors="replace")

checks = {
    "BlockModelLighter.prepareQuadShape": (
        lighter,
        [
            "private void prepareQuadShape",
            "BakedQuad.position:(I)Lorg/joml/Vector3fc;",
            "java/lang/Math.min:(FF)F",
            "java/lang/Math.max:(FF)F",
            "facePartial",
            "faceCubic",
            "BlockState.isCollisionShapeFullBlock",
        ],
    ),
    "QuadCollection retained lists": (
        collection,
        [
            "private final java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> all;",
            "private final java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> north;",
            "private final java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> south;",
            "getQuads(net.minecraft.core.Direction)",
            "getAll()",
        ],
    ),
    "ModelBakery vector interner": (
        interner,
        [
            "private final com.google.common.collect.Interner<org.joml.Vector3fc> vectors;",
            "Interners.newStrongInterner",
            "Interner.intern:(Ljava/lang/Object;)Ljava/lang/Object;",
            "public org.joml.Vector3fc vector(org.joml.Vector3fc);",
        ],
    ),
    "FaceBakery baked-position path": (
        face_bakery,
        [
            "FaceInfo$VertexInfo.select:(Lorg/joml/Vector3fc;Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;",
            "ModelBaker$Interner.vector:(Lorg/joml/Vector3fc;)Lorg/joml/Vector3fc;",
            "aastore",
            "BakedQuad.\"<init>\":",
        ],
    ),
    "BakedQuad storage": (
        quad,
        [
            "private final org.joml.Vector3fc position0;",
            "private final org.joml.Vector3fc position1;",
            "private final org.joml.Vector3fc position2;",
            "private final org.joml.Vector3fc position3;",
            "putfield      #7",
            "putfield      #13",
            "putfield      #16",
            "putfield      #19",
        ],
    ),
}

failed = False
for label, (source, needles) in checks.items():
    absent = [needle for needle in needles if needle not in source]
    if absent:
        print(f"FAIL: {label} missing bytecode evidence: {absent}")
        failed = True
    else:
        print(f"PASS: {label} contains all {len(needles)} expected bytecode markers")

builder_markers = [
    "ImmutableList.builder",
    "ImmutableList$Builder.build",
    "ArrayListMultimap.create",
    "createFromSublists",
]
missing_builder_markers = [marker for marker in builder_markers if marker not in builder]
if missing_builder_markers:
    print(f"FAIL: QuadCollection.Builder missing bytecode evidence: {missing_builder_markers}")
    failed = True
else:
    print("PASS: QuadCollection.Builder uses immutable-list construction and directional sublists")

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
    sys.exit(
        "Quad geometry inspection changed or is incomplete; "
        "do not assume a reusable geometry cache is safe."
    )

print(
    "VOID FPS quad-geometry inspection passed: FaceBakery bakes mutable Vector3f instances into BakedQuad positions through a strong interner; "
    "the interner reuses references but does not establish vector immutability, and faceCubic remains state-dependent."
)
