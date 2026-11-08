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
vertex_output_path = inspection_root / "vertex-output.bytecode.txt"
model_bakery_path = inspection_root / "ModelBakery.bytecode.txt"
model_baker_impl_path = inspection_root / "ModelBakery$ModelBakerImpl.bytecode.txt"

required_paths = (lighter_path, quad_path, collection_path, builder_path, interner_path, face_bakery_path, vertex_output_path, model_bakery_path, model_baker_impl_path)
missing_files = [str(path) for path in required_paths if not path.is_file()]
if missing_files:
    sys.exit("Missing required vanilla bytecode dump(s): " + ", ".join(missing_files))

lighter = lighter_path.read_text(errors="replace")
quad = quad_path.read_text(errors="replace")
collection = collection_path.read_text(errors="replace")
builder = builder_path.read_text(errors="replace")
interner = interner_path.read_text(errors="replace")
face_bakery = face_bakery_path.read_text(errors="replace")
vertex_output = vertex_output_path.read_text(errors="replace")
model_bakery = model_bakery_path.read_text(errors="replace")
model_baker_impl = model_baker_impl_path.read_text(errors="replace")


def method_body(source: str, signature_pattern: str) -> str | None:
    """Return one javap method and its instructions, stopping at the next member."""
    match = re.search(
        signature_pattern + r".*?(?=\n  (?:private|public|protected|static|\})|\Z)",
        source,
        re.DOTALL,
    )
    return match.group(0) if match else None


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

bake_vertex = method_body(face_bakery, r"private static void bakeVertex\(")
rotate_vertex = method_body(face_bakery, r"private static void rotateVertexBy\(")
recalculate_winding = method_body(face_bakery, r"private static void recalculateWinding\(")
if not all((bake_vertex, rotate_vertex, recalculate_winding)):
    print("FAIL: could not isolate FaceBakery vertex transformation/winding methods")
    failed = True
else:
    interner_call = bake_vertex.find("ModelBaker$Interner.vector:")
    transform_markers = [
        "Vector3f.div:(F)Lorg/joml/Vector3f;",
        "rotateVertexBy:(Lorg/joml/Vector3f;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)V",
        "ModelBaker$Interner.vector:",
        "aastore",
    ]
    positions = [bake_vertex.find(marker) for marker in transform_markers]
    if any(position < 0 for position in positions) or positions != sorted(positions):
        print("FAIL: FaceBakery vertex transform/interner sequence changed or is incomplete")
        failed = True
    elif re.search(r"Vector3f\.(?:set|add|sub|mul|div|normalize):", bake_vertex[interner_call:]):
        print("FAIL: mutable Vector3f operation appears after interning in bakeVertex")
        failed = True
    else:
        print("PASS: bakeVertex applies its direct transforms before interning and stores the returned reference")

    rotate_markers = [
        "Vector3f.sub:(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;",
        "Matrix4fc.transformPosition:(Lorg/joml/Vector3f;)Lorg/joml/Vector3f;",
        "Vector3f.add:(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;",
    ]
    rotate_positions = [rotate_vertex.find(marker) for marker in rotate_markers]
    if any(position < 0 for position in rotate_positions) or rotate_positions != sorted(rotate_positions):
        print("FAIL: rotateVertexBy mutation sequence changed or is incomplete")
        failed = True
    else:
        print("PASS: rotateVertexBy mutates the fresh vertex before it reaches the interner")

    if "Vector3f." in recalculate_winding or "Vector3f;" in recalculate_winding:
        print("FAIL: recalculateWinding now references concrete mutable Vector3f operations; review manually")
        failed = True
    else:
        print("PASS: recalculateWinding operates on Vector3fc references and packed UV data, not Vector3f mutators")


# Verify the post-intern FaceBakery path. Once bakeVertex has placed
# interned Vector3fc references in the position array, facing calculation
# writes its normal into a separate fresh Vector3f, and winding correction
# may reorder array references but must not mutate the vectors themselves.
quad_bake = method_body(
    face_bakery,
    r"public static net\.minecraft\.client\.resources\.model\.geometry\.BakedQuad bakeQuad\(net\.minecraft\.client\.resources\.model\.ModelBaker\$Interner,",
)
calculate_facing = method_body(face_bakery, r"private static net\.minecraft\.core\.Direction calculateFacing\(")
find_closest_direction = method_body(face_bakery, r"private static net\.minecraft\.core\.Direction findClosestDirection\(")
find_vertex = method_body(face_bakery, r"private static int findVertex\(")
position_swap = method_body(face_bakery, r"private static void swap\(org\.joml\.Vector3fc\[\],")
if not all((quad_bake, calculate_facing, find_closest_direction, find_vertex, position_swap)):
    print("FAIL: could not isolate all post-intern FaceBakery methods")
    failed = True
else:
    post_bake_markers = [
        "Method bakeVertex:",
        "Method calculateFacing:",
        "Method recalculateWinding:",
        'BakedQuad."<init>":',
    ]
    post_bake_positions = [quad_bake.find(marker) for marker in post_bake_markers]
    if any(position < 0 for position in post_bake_positions) or post_bake_positions != sorted(post_bake_positions):
        print("FAIL: FaceBakery post-intern order changed; review before relying on shared vectors")
        failed = True
    elif not all(marker in calculate_facing for marker in (
        "new           #192                // class org/joml/Vector3f",
        "org/joml/GeometryUtils.normal:",
        "findClosestDirection:(Lorg/joml/Vector3f;)",
    )):
        print("FAIL: calculateFacing no longer writes its normal into a separate temporary Vector3f")
        failed = True
    elif re.search(r"Vector3f\.(?:set|add|sub|mul|div|normalize):", find_closest_direction):
        print("FAIL: findClosestDirection contains a mutable Vector3f operation")
        failed = True
    elif not all(marker in find_vertex for marker in (
        "Vector3fc.x:()F", "Vector3fc.y:()F", "Vector3fc.z:()F"
    )):
        print("FAIL: findVertex no longer compares positions through read-only Vector3fc accessors")
        failed = True
    elif "aastore" not in position_swap or re.search(r"Vector3f\.(?:set|add|sub|mul|div|normalize):", position_swap):
        print("FAIL: position-array swap is no longer reference-only")
        failed = True
    else:
        print("PASS: post-intern FaceBakery path reads shared vectors and only reorders references; facing normal uses a separate temporary")

# Verify the interner's ownership scope. Minecraft creates one interner inside
# each bakeModels invocation, passes that same instance into missing-model
# baking and ModelBakerImpl, and stores it in ModelBakerImpl for the bake.
bake_models = method_body(model_bakery, r"public java\.util\.concurrent\.CompletableFuture.* bakeModels\(")
baker_constructor = method_body(
    model_baker_impl,
    r"private net\.minecraft\.client\.resources\.model\.ModelBakery\$ModelBakerImpl\("
)
baker_interner_accessor = method_body(
    model_baker_impl,
    r"public net\.minecraft\.client\.resources\.model\.ModelBaker\$Interner interner\("
)
if not all((bake_models, baker_constructor, baker_interner_accessor)):
    print("FAIL: could not isolate ModelBakery bake scope and ModelBakerImpl interner ownership")
    failed = True
else:
    interner_creation = re.findall(
        r"^\s*\d+: new\s+#[0-9]+\s+// class net/minecraft/client/resources/model/ModelBakery\$InternerImpl$",
        bake_models,
        re.MULTILINE,
    )
    missing_model_call = re.search(
        r"^\s*\d+: aload_3\s*$.*?ModelBakery\$MissingModels\.bake:",
        bake_models,
        re.MULTILINE | re.DOTALL,
    )
    model_baker_call = re.search(
        r"new\s+#[0-9]+\s+// class net/minecraft/client/resources/model/ModelBakery\$ModelBakerImpl"
        r".*?^\s*\d+: aload_3\s*$.*?ModelBakery\$ModelBakerImpl\.\"<init>\":",
        bake_models,
        re.MULTILINE | re.DOTALL,
    )
    model_bakery_fields = model_bakery.split(
        "  public java.util.concurrent.CompletableFuture", 1
    )[0]
    interner_fields = [
        line.strip()
        for line in model_bakery_fields.splitlines()
        if re.match(r"\s+(?:private|protected|public)\s+.*;", line)
        and "ModelBaker$Interner" in line
    ]
    if len(interner_creation) != 1 or missing_model_call is None or model_baker_call is None:
        print("FAIL: bakeModels no longer creates exactly one interner and passes the same local reference to both bake paths")
        failed = True
    elif interner_fields:
        print(f"FAIL: ModelBakery now retains an interner in an instance/static field: {interner_fields}")
        failed = True
    elif "putfield      #38                 // Field interner:Lnet/minecraft/client/resources/model/ModelBaker$Interner;" not in baker_constructor:
        print("FAIL: ModelBakerImpl no longer stores the supplied interner in its instance field")
        failed = True
    elif "getfield      #38                 // Field interner:Lnet/minecraft/client/resources/model/ModelBaker$Interner;" not in baker_interner_accessor:
        print("FAIL: ModelBakerImpl interner() no longer returns its stored interner")
        failed = True
    else:
        print("PASS: bakeModels creates exactly one interner, passes the same local reference to both bake paths, and ModelBakery has no interner field")

# Audit every inspected method that consumes BakedQuad.position(), not just
# the two currently known classes. A newly discovered call site is reviewed
# automatically for direct Vector3f mutation within that same method.
known_position_consumers = {
    Path("vanilla-bytecode/BlockModelLighter.bytecode.txt"),
    Path("vertex-output.bytecode.txt"),
}
discovered_position_consumers = set()
position_call_sites = []
mutator_pattern = re.compile(
    r"org/joml/Vector3f\.(?:set|add|sub|mul|div|normalize):"
)
# Guard the audit itself: a bytecode comment containing a real JOML mutator
# must match, while a read-only Vector3fc accessor must not.
if not mutator_pattern.search("invokevirtual #1 // Method org/joml/Vector3f.set:(FFF)Lorg/joml/Vector3f;"):
    sys.exit("FAIL: baked-position mutator detector missed a Vector3f.set bytecode reference")
if mutator_pattern.search("invokeinterface #2 // InterfaceMethod org/joml/Vector3fc.x:()F"):
    sys.exit("FAIL: baked-position mutator detector incorrectly matched a read-only Vector3fc accessor")
print("PASS: baked-position mutator detector regression checks")

seen_consumer_dump_names = set()
dump_paths = sorted(
    inspection_root.rglob("*.bytecode.txt"),
    key=lambda path: (path.name, 0 if "vanilla-bytecode" in path.parts else 1),
)
for dump_path in dump_paths:
    # inspect_targets.py and the dedicated vanilla dump step can emit the same
    # class under different folders. Prefer the canonical vanilla-bytecode dump.
    if dump_path.name in seen_consumer_dump_names:
        continue
    dump_text = dump_path.read_text(errors="replace")
    if "BakedQuad.position:(I)Lorg/joml/Vector3fc;" not in dump_text:
        continue
    seen_consumer_dump_names.add(dump_path.name)

    relative_path = dump_path.relative_to(inspection_root)
    discovered_position_consumers.add(relative_path)
    lines = dump_text.splitlines()
    current_header = None
    current_body = []

    def inspect_current_method():
        if current_header is None:
            return
        body = "\\n".join(current_body)
        if "BakedQuad.position:(I)Lorg/joml/Vector3fc;" not in body:
            return True
        label = f"{relative_path}:{current_header.strip()}"
        position_call_sites.append(label)
        mutators = mutator_pattern.findall(body)
        if mutators:
            print(f"FAIL: baked-position consumer method directly invokes mutable Vector3f operations: {label}: {mutators}")
            return False

        # Trace the known position-transform helper at the call boundary.
        # Matrix4f.transformPosition accepts the baked Vector3fc as input and
        # a separate Vector3f destination. Require that destination allocation
        # to appear in this consumer before the helper invocation.
        transform_call = "org/joml/Matrix4f.transformPosition:(Lorg/joml/Vector3fc;Lorg/joml/Vector3f;)Lorg/joml/Vector3f;"
        if transform_call in body:
            call_index = body.find(transform_call)
            allocation_index = body.rfind("new           #", 0, call_index)
            allocation_window = body[allocation_index:call_index] if allocation_index >= 0 else ""
            if "class org/joml/Vector3f" not in allocation_window:
                print(f"FAIL: position transform helper lacks a visible fresh Vector3f destination allocation: {label}")
                return False
            print(f"PASS: position transform helper receives Vector3fc input and a separately allocated Vector3f destination: {label}")

        print(f"PASS: baked-position consumer method has no direct Vector3f mutator calls: {label}")
        return True

    method_failed = False
    for line in lines:
        is_method_header = (
            line.startswith("  ")
            and not line.startswith("    ")
            and "(" in line
            and line.rstrip().endswith(";")
        )
        if is_method_header:
            if inspect_current_method() is False:
                method_failed = True
            current_header = line
            current_body = [line]
        elif current_header is not None:
            current_body.append(line)
    if inspect_current_method() is False:
        method_failed = True

    if method_failed:
        failed = True

missing_consumers = known_position_consumers - discovered_position_consumers
if missing_consumers:
    print(f"FAIL: expected baked-position consumer dump(s) not found: {sorted(map(str, missing_consumers))}")
    failed = True
elif not position_call_sites:
    print("FAIL: no BakedQuad.position() call sites could be isolated into methods")
    failed = True
else:
    print(
        "PASS: inspected all discovered BakedQuad.position() consumer methods "
        f"({len(position_call_sites)} call site(s)) across {len(discovered_position_consumers)} dump(s)"
    )

unexpected_consumers = discovered_position_consumers - known_position_consumers
if unexpected_consumers:
    print(
        "REVIEW: additional baked-position consumer dump(s) were discovered and "
        "their call-site methods were included in the mutation scan: "
        f"{sorted(map(str, unexpected_consumers))}"
    )

if failed:
    sys.exit(
        "Quad geometry inspection changed or is incomplete; "
        "do not assume a reusable geometry cache is safe."
    )

print(
    "VOID FPS quad-geometry inspection passed: FaceBakery transforms each fresh mutable Vector3f before interning it, "
    "then stores the returned Vector3fc reference. This only verifies the inspected baking path; it does not prove "
    "global immutability or rule out mutation through code outside the inspected vanilla consumers. The inspected BlockModelLighter and VertexConsumer paths read positions without direct Vector3f mutators; faceCubic also remains state-dependent."
)
