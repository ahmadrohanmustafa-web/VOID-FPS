#!/usr/bin/env python3
"""Verify the real Minecraft 26.3 section-mesh inspection APIs before test harness work."""
from pathlib import Path
import sys

root = Path("build/void-fps-inspect")
checks = {
    "net_minecraft_client_renderer_chunk_SectionCompiler$Results.bytecode.txt": [
        "public final java.util.Map<net.minecraft.client.renderer.chunk.ChunkSectionLayer, com.mojang.blaze3d.vertex.MeshData> renderedLayers;",
        "public void release();",
    ],
    "MeshData.bytecode.txt": [
        "public java.nio.ByteBuffer vertexBuffer();",
        "public com.mojang.blaze3d.vertex.MeshData$DrawState drawState();",
        "public void close();",
    ],
    "com_mojang_blaze3d_vertex_MeshData$DrawState.bytecode.txt": [
        "public com.mojang.renderpearl.api.vertex.VertexFormat format();",
        "public int vertexCount();",
        "public int indexCount();",
        "public com.mojang.renderpearl.api.pipeline.PrimitiveTopology primitiveTopology();",
    ],
    "SectionBufferBuilderPack.bytecode.txt": [
        "public net.minecraft.client.renderer.SectionBufferBuilderPack();",
        "public com.mojang.blaze3d.vertex.ByteBufferBuilder buffer(net.minecraft.client.renderer.chunk.ChunkSectionLayer);",
        "public void clearAll();",
        "public void discardAll();",
        "public void close();",
    ],
    "ByteBufferBuilder$Result.bytecode.txt": [
        "public java.nio.ByteBuffer byteBuffer();",
        "public void close();",
    ],
    "ChunkSectionLayer.bytecode.txt": [
        "public static final net.minecraft.client.renderer.chunk.ChunkSectionLayer SOLID;",
        "public static final net.minecraft.client.renderer.chunk.ChunkSectionLayer CUTOUT;",
        "public static final net.minecraft.client.renderer.chunk.ChunkSectionLayer TRANSLUCENT;",
        "public com.mojang.renderpearl.api.vertex.VertexFormat vertexFormat();",
    ],
}

failed = False
for filename, needles in checks.items():
    path = root / filename
    if not path.is_file():
        print(f"FAIL: required Minecraft 26.3 bytecode dump missing: {path}")
        failed = True
        continue
    content = path.read_text(errors="replace")
    missing = [needle for needle in needles if needle not in content]
    if missing:
        print(f"FAIL: {filename} missing expected mesh API evidence: {missing}")
        failed = True
    else:
        print(f"PASS: {filename} confirms {len(needles)} mesh API details")

if failed:
    sys.exit("Section mesh API inspection incomplete or changed; do not write the capture harness yet.")
print("VOID FPS Minecraft 26.3 section-mesh API inspection passed.")
