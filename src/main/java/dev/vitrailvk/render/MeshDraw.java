package dev.vitrailvk.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** A copy of one immediate mesh, kept alive by the arena until its frame slot is reusable. */
final class MeshDraw {
	private final FrameArena.Slice vertices;
	private final FrameArena.Slice indices;
	private final int count;
	private final int indexType;
	private final int topology;

	private MeshDraw(FrameArena.Slice vertices, FrameArena.Slice indices, int count, int indexType, int topology) {
		this.vertices = vertices;
		this.indices = indices;
		this.count = count;
		this.indexType = indexType;
		this.topology = topology;
	}

	/** Copies before the caller releases MeshData. Returns null for an empty mesh. */
	static MeshDraw copy(FrameArena arena, MeshData mesh) {
		MeshData.DrawState state = mesh.drawState();
		int vertices = state.vertexCount();
		if (vertices <= 0) return null;
		int vertexBytes = Math.multiplyExact(vertices, state.format().getVertexSize());
		FrameArena.Slice vertexSlice = copyBytes(arena, mesh.vertexBuffer(), vertexBytes);
		ByteBuffer sourceIndices = mesh.indexBuffer();
		int topology = switch (state.mode()) {
			case DEBUG_LINES, DEBUG_LINE_STRIP -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
			default -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
		};
		if (sourceIndices != null) {
			int count = state.indexCount();
			if (count <= 0) return null;
			int size = Math.multiplyExact(count, state.indexType().bytes);
			int type = state.indexType() == VertexFormat.IndexType.SHORT ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32;
			return new MeshDraw(vertexSlice, copyBytes(arena, sourceIndices, size), count, type, topology);
		}
		int count = switch (state.mode()) {
			case QUADS, LINES -> Math.multiplyExact(vertices / 4, 6);
			case TRIANGLE_FAN, TRIANGLE_STRIP, LINE_STRIP -> Math.multiplyExact(Math.max(0, vertices - 2), 3);
			case DEBUG_LINE_STRIP -> Math.multiplyExact(Math.max(0, vertices - 1), 2);
			case TRIANGLES, DEBUG_LINES -> 0;
		};
		if (count == 0) {
			if (state.mode() != VertexFormat.Mode.TRIANGLES && state.mode() != VertexFormat.Mode.DEBUG_LINES) return null;
			return new MeshDraw(vertexSlice, null, vertices, VK_INDEX_TYPE_UINT32, topology);
		}
		FrameArena.Slice indexSlice = arena.allocate(Math.multiplyExact(count, Integer.BYTES));
		IntBuffer out = indexSlice.bytes().asIntBuffer();
		switch (state.mode()) {
			case QUADS -> {
				for (int i = 0; i + 3 < vertices; i += 4) out.put(i).put(i + 1).put(i + 2).put(i).put(i + 2).put(i + 3);
			}
			case LINES -> {
				for (int i = 0; i + 3 < vertices; i += 4) out.put(i).put(i + 1).put(i + 2).put(i + 3).put(i + 2).put(i + 1);
			}
			case TRIANGLE_FAN -> {
				for (int i = 1; i + 1 < vertices; i++) out.put(0).put(i).put(i + 1);
			}
			case TRIANGLE_STRIP, LINE_STRIP -> {
				for (int i = 0; i + 2 < vertices; i++) out.put((i & 1) == 0 ? i : i + 1)
						.put((i & 1) == 0 ? i + 1 : i).put(i + 2);
			}
			case DEBUG_LINE_STRIP -> {
				for (int i = 0; i + 1 < vertices; i++) out.put(i).put(i + 1);
			}
			default -> throw new IllegalStateException("Unexpected indexed mode " + state.mode());
		}
		return new MeshDraw(vertexSlice, indexSlice, count, VK_INDEX_TYPE_UINT32, topology);
	}

	private static FrameArena.Slice copyBytes(FrameArena arena, ByteBuffer source, int bytes) {
		ByteBuffer copy = source.duplicate();
		if (copy.remaining() < bytes) throw new IllegalArgumentException("Mesh buffer is shorter than its draw state");
		copy.limit(copy.position() + bytes);
		FrameArena.Slice slice = arena.allocate(bytes);
		slice.bytes().put(copy);
		return slice;
	}

	int topology() {
		return this.topology;
	}

	// TODO-VERIFY: validate Vulkan binding and lifetime with immediate meshes in a running frame.
	void draw(VkCommandBuffer cmd, MemoryStack stack, FrameArena.Slice constants) {
		vkCmdBindVertexBuffers(cmd, 0, stack.longs(this.vertices.buffer(), constants.buffer()),
				stack.longs(this.vertices.offset(), constants.offset()));
		if (this.indices == null) {
			vkCmdDraw(cmd, this.count, 1, 0, 0);
		} else {
			vkCmdBindIndexBuffer(cmd, this.indices.buffer(), this.indices.offset(), this.indexType);
			vkCmdDrawIndexed(cmd, this.count, 1, 0, 0, 0);
		}
	}
}
