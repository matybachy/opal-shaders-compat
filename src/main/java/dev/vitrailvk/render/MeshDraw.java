package dev.vitrailvk.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.vitrail.render.EntityFrame;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

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
		return copy(arena, mesh.drawState(), mesh.vertexBuffer(), mesh.indexBuffer());
	}

	static MeshDraw copy(FrameArena arena, MeshData.DrawState state, ByteBuffer sourceVertices, ByteBuffer sourceIndices) {
		int vertices = state.vertexCount();
		if (vertices <= 0) return null;
		int vertexBytes = Math.multiplyExact(vertices, state.format().getVertexSize());
		FrameArena.Slice vertexSlice = copyBytes(arena, sourceVertices, vertexBytes);
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

	/** A record per vertex preserves identifiers even when the game combines entities in one batch. */
	// TODO-VERIFY: validate per-vertex identifier lanes and missing attribute defaults in live shaders.
	static FrameArena.Slice constants(FrameArena arena, DrawIds[] identifiers, MeshData.DrawState state,
			ByteBuffer source, float lineWidth) {
		int count = state.vertexCount();
		int stride = PackPipeline.Constants.STRIDE;
		FrameArena.Slice slice = arena.allocate(Math.multiplyExact(stride, count));
		ByteBuffer out = slice.bytes();
		for (int i = 0; i < out.capacity(); i++) out.put(i, (byte) 0);
		for (int vertex = 0; vertex < count; vertex++) {
			int base = vertex * stride;
			for (int i = 0; i < 4; i++) out.putFloat(base + PackPipeline.Constants.COLOR + i * 4, 1.0F);
			out.putFloat(base + PackPipeline.Constants.NORMAL + 8, 1.0F);
			DrawIds ids = identifiers != null && vertex < identifiers.length ? identifiers[vertex] : DrawIds.NONE;
			ids.put(out, base + PackPipeline.Constants.IDENTIFIERS);
			out.putFloat(base + PackPipeline.Constants.TANGENT, 1.0F);
			out.putFloat(base + PackPipeline.Constants.TANGENT + 12, 1.0F);
			out.putFloat(base + PackPipeline.Constants.LINE_WIDTH, lineWidth);
		}
		fillQuadFrames(out, state, source);
		return slice;
	}
	// TODO-VERIFY: check tangent and midpoint parity with Iris for packed quad formats at runtime.
	private static void fillQuadFrames(ByteBuffer out, MeshData.DrawState state, ByteBuffer source) {
		if (state.mode() != VertexFormat.Mode.QUADS) return;
		VertexFormat format = state.format();
		List<String> names = format.getElementAttributeNames();
		int positionIndex = names.indexOf("Position"), uvIndex = names.indexOf("UV0"), normalIndex = names.indexOf("Normal");
		if (positionIndex < 0 || uvIndex < 0) return;
		VertexFormatElement position = format.getElements().get(positionIndex), uv = format.getElements().get(uvIndex);
		if (position.type() != VertexFormatElement.Type.FLOAT || position.count() < 3
				|| uv.type() != VertexFormatElement.Type.FLOAT || uv.count() < 2) return;
		VertexFormatElement normal = normalIndex < 0 ? null : format.getElements().get(normalIndex);
		if (normal != null && (normal.type() != VertexFormatElement.Type.BYTE || normal.count() < 3)) normal = null;
		int stride = format.getVertexSize();
		ByteBuffer in = source.duplicate().order(java.nio.ByteOrder.nativeOrder());
		int base = in.position();
		if ((long) state.vertexCount() * stride > in.remaining()) return;
		int p = format.getOffset(position), t = format.getOffset(uv), n = normal == null ? -1 : format.getOffset(normal);
		for (int first = 0; first + 3 < state.vertexCount(); first += 4) {
			float[] xyz = new float[12], uvCoords = new float[8], face = new float[3];
			float midU = 0, midV = 0;
			for (int corner = 0; corner < 4; corner++) {
				int at = base + (first + corner) * stride;
				for (int axis = 0; axis < 3; axis++) xyz[corner * 3 + axis] = in.getFloat(at + p + axis * 4);
				float u = in.getFloat(at + t), v = in.getFloat(at + t + 4);
				uvCoords[corner * 2] = u; uvCoords[corner * 2 + 1] = v;
				midU += u * 0.25F; midV += v * 0.25F;
			}
			boolean hasFace = EntityFrame.faceNormal(face, xyz[0], xyz[1], xyz[2], xyz[3], xyz[4], xyz[5],
					xyz[6], xyz[7], xyz[8], xyz[9], xyz[10], xyz[11]);
			for (int corner = 0; corner < 4; corner++) {
				int at = base + (first + corner) * stride;
				float nx = n >= 0 ? Math.max(-1.0F, in.get(at + n) / 127.0F) : hasFace ? face[0] : 0.0F;
				float ny = n >= 0 ? Math.max(-1.0F, in.get(at + n + 1) / 127.0F) : hasFace ? face[1] : 0.0F;
				float nz = n >= 0 ? Math.max(-1.0F, in.get(at + n + 2) / 127.0F) : hasFace ? face[2] : 1.0F;
				int tangent = EntityFrame.tangent(nx, ny, nz, n >= 0,
						xyz[0], xyz[1], xyz[2], uvCoords[0], uvCoords[1],
						xyz[3], xyz[4], xyz[5], uvCoords[2], uvCoords[3],
						xyz[6], xyz[7], xyz[8], uvCoords[4], uvCoords[5]);
				int dst = (first + corner) * PackPipeline.Constants.STRIDE;
				out.putFloat(dst + PackPipeline.Constants.MID_TEX, midU).putFloat(dst + PackPipeline.Constants.MID_TEX + 4, midV);
				for (int axis = 0; axis < 4; axis++) {
					float value = Math.max(-1.0F, (byte) (tangent >>> (axis * 8)) / 127.0F);
					out.putFloat(dst + PackPipeline.Constants.TANGENT + axis * 4, value);
				}
			}
		}
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
