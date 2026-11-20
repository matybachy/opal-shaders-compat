package dev.opalcompat.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.vitrail.render.EntityFrame;
import net.vulkanmod.vulkan.Drawer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.AutoIndexBuffer;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/**
 * What one mesh draw binds. A mesh the game built this frame is copied, since it is closed when the
 * call returns; a {@code VBO} is already on the GPU and only referred to.
 * <p>
 * Binding 0 is the mesh, binding 1 one {@link PackPipeline.Constants} record for what its format has
 * not got, binding 2 (only where a program reads them and the vertices are at hand)
 * {@link PackPipeline.Derived}: the owner's ids, the middle of the quad's sprite, its tangent. A vanilla
 * format has no room for those, and a batch mixes owners, so they cannot be one record per draw.
 */
final class MeshDraw {
	private final long vertexBuffer;
	private final long vertexOffset;
	private final long indexBuffer;
	private final long indexOffset;
	private final int indexType;
	private final int count;
	private final FrameArena.Slice constants;
	private final FrameArena.Slice derived;

	private MeshDraw(long vertexBuffer, long vertexOffset, long indexBuffer, long indexOffset, int indexType, int count,
			FrameArena.Slice constants, FrameArena.Slice derived) {
		this.vertexBuffer = vertexBuffer;
		this.vertexOffset = vertexOffset;
		this.indexBuffer = indexBuffer;
		this.indexOffset = indexOffset;
		this.indexType = indexType;
		this.count = count;
		this.constants = constants;
		this.derived = derived;
	}

	/**
	 * Null for an empty mesh.
	 *
	 * @param derivedReads what of {@link PackPipeline.Derived} the programs drawing it read, as its {@code READS_}
	 *                     bits; nought for none, where no derived stream is built
	 */
	static MeshDraw immediate(FrameArena arena, MeshData.DrawState state, ByteBuffer sourceVertices,
			ByteBuffer sourceIndices, DrawIds[] ids, int derivedReads, float lineWidth) {
		int vertices = state.vertexCount();
		if (vertices <= 0) return null;
		int vertexBytes = Math.multiplyExact(vertices, state.format().getVertexSize());
		FrameArena.Slice vertexSlice = copyBytes(arena, sourceVertices, vertexBytes);
		FrameArena.Slice constants = constants(arena, lineWidth);
		// Worked out from the copy, the same bytes, which is mapped memory read where it lies.
		FrameArena.Slice derived = derivedReads != 0 ? derive(arena, state, vertexSlice, ids, derivedReads) : null;
		if (sourceIndices != null) {
			int count = state.indexCount();
			if (count <= 0) return null;
			int type = state.indexType() == VertexFormat.IndexType.SHORT ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32;
			FrameArena.Slice indexSlice = copyBytes(arena, sourceIndices, Math.multiplyExact(count, state.indexType().bytes));
			return new MeshDraw(vertexSlice.buffer(), vertexSlice.offset(), indexSlice.buffer(), indexSlice.offset(), type,
					count, constants, derived);
		}
		return automatic(arena, state.mode(), vertices, vertexSlice.buffer(), vertexSlice.offset(), constants, derived);
	}

	/** The same vertices, drawn through other indices: one run of a batch that mixes owners. */
	MeshDraw withIndices(FrameArena arena, ByteBuffer indices, int indexCount) {
		FrameArena.Slice slice = copyBytes(arena, indices, Math.multiplyExact(indexCount, Integer.BYTES));
		return new MeshDraw(this.vertexBuffer, this.vertexOffset, slice.buffer(), slice.offset(), VK_INDEX_TYPE_UINT32,
				indexCount, this.constants, this.derived);
	}

	/**
	 * A {@code VBO}'s geometry: its own buffers, read where they are.
	 *
	 * @param indices    its index buffer, or null where it is drawn in order
	 * @param indexCount how many of them, or how many vertices where it is drawn in order
	 */
	static MeshDraw stored(FrameArena arena, long vertexBuffer, long vertexOffset, IndexBuffer indices, int indexCount,
			float lineWidth) {
		FrameArena.Slice constants = constants(arena, lineWidth);
		if (indices == null) {
			return new MeshDraw(vertexBuffer, vertexOffset, 0L, 0L, VK_INDEX_TYPE_UINT32, indexCount, constants, null);
		}
		return new MeshDraw(vertexBuffer, vertexOffset, indices.getId(), indices.getOffset(), indices.indexType.type,
				indexCount, constants, null);
	}

	static int topology(VertexFormat.Mode mode) {
		return switch (mode) {
			case DEBUG_LINES, DEBUG_LINE_STRIP -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
			default -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
		};
	}

	/** The shared index buffers VulkanMod's own draws use, with its own counts ({@code Drawer.draw}). */
	private static MeshDraw automatic(FrameArena arena, VertexFormat.Mode mode, int vertices, long vertexBuffer,
			long vertexOffset, FrameArena.Slice constants, FrameArena.Slice derived) {
		Drawer drawer = Renderer.getDrawer();
		AutoIndexBuffer shared;
		int count;
		switch (mode) {
			case QUADS -> {
				count = vertices / 4 * 6;
				if (count > AutoIndexBuffer.U16_MAX_INDEX_COUNT) {
					FrameArena.Slice slice = longQuads(arena, vertices, count);
					return new MeshDraw(vertexBuffer, vertexOffset, slice.buffer(), slice.offset(), VK_INDEX_TYPE_UINT32, count,
							constants, derived);
				}
				shared = drawer.getQuadsIndexBuffer();
			}
			case LINES -> {
				count = vertices / 4 * 6;
				shared = drawer.getLinesIndexBuffer();
			}
			case TRIANGLE_FAN -> {
				count = Math.max(0, vertices - 2) * 3;
				shared = drawer.getTriangleFanIndexBuffer();
			}
			case TRIANGLE_STRIP, LINE_STRIP -> {
				count = Math.max(0, vertices - 2) * 3;
				shared = drawer.getTriangleStripIndexBuffer();
			}
			case DEBUG_LINE_STRIP -> {
				count = Math.max(0, vertices - 1) * 2;
				shared = drawer.getDebugLineStripIndexBuffer();
			}
			default -> {
				return new MeshDraw(vertexBuffer, vertexOffset, 0L, 0L, VK_INDEX_TYPE_UINT32, vertices, constants, derived);
			}
		}
		if (count == 0) return null;
		shared.checkCapacity(vertices);
		IndexBuffer buffer = shared.getIndexBuffer();
		return new MeshDraw(vertexBuffer, vertexOffset, buffer.getId(), buffer.getOffset(), buffer.indexType.type, count,
				constants, derived);
	}

	/** Past what the 16 bit quad buffer holds; the 32 bit one has no getter on {@code Drawer}. */
	private static FrameArena.Slice longQuads(FrameArena arena, int vertices, int count) {
		FrameArena.Slice slice = arena.allocate(Math.multiplyExact(count, Integer.BYTES));
		IntBuffer out = slice.bytes().asIntBuffer();
		for (int quad = 0; quad + 3 < vertices; quad += 4) {
			out.put(quad).put(quad + 1).put(quad + 2).put(quad).put(quad + 2).put(quad + 3);
		}
		return slice;
	}

	private static FrameArena.Slice copyBytes(FrameArena arena, ByteBuffer source, int bytes) {
		ByteBuffer copy = source.duplicate();
		if (copy.remaining() < bytes) throw new IllegalArgumentException("Mesh buffer is shorter than its draw state");
		copy.limit(copy.position() + bytes);
		FrameArena.Slice slice = arena.allocate(bytes);
		slice.bytes().put(copy);
		return slice;
	}

	/** Iris's stand-ins for an attribute the format has not got ({@code VanillaTransformer}). */
	private static FrameArena.Slice constants(FrameArena arena, float lineWidth) {
		FrameArena.Slice slice = arena.allocate(PackPipeline.Constants.STRIDE);
		ByteBuffer out = slice.bytes();
		for (int i = 0; i < 4; i++) out.putFloat(PackPipeline.Constants.COLOR + i * 4, 1.0F);
		out.putFloat(PackPipeline.Constants.UV0, 0.5F).putFloat(PackPipeline.Constants.UV0 + 4, 0.5F);
		// OverlayTexture.NO_OVERLAY: the row of the overlay image that tints nothing.
		out.putInt(PackPipeline.Constants.UV1, 0).putInt(PackPipeline.Constants.UV1 + 4, 10);
		out.putInt(PackPipeline.Constants.UV2, 240).putInt(PackPipeline.Constants.UV2 + 4, 240);
		out.putFloat(PackPipeline.Constants.NORMAL, 0.0F).putFloat(PackPipeline.Constants.NORMAL + 4, 0.0F)
				.putFloat(PackPipeline.Constants.NORMAL + 8, 1.0F).putFloat(PackPipeline.Constants.NORMAL + 12, 0.0F);
		PackRenderer.currentDrawIds().put(out, PackPipeline.Constants.IDENTIFIERS);
		out.putFloat(PackPipeline.Constants.MID_TEX, 0.5F).putFloat(PackPipeline.Constants.MID_TEX + 4, 0.5F);
		out.putFloat(PackPipeline.Constants.TANGENT, 1.0F).putFloat(PackPipeline.Constants.TANGENT + 4, 0.0F)
				.putFloat(PackPipeline.Constants.TANGENT + 8, 0.0F).putFloat(PackPipeline.Constants.TANGENT + 12, 1.0F);
		out.putFloat(PackPipeline.Constants.LINE_WIDTH, lineWidth);
		return slice;
	}

	/**
	 * The derived stream of a mesh, from its copy in the arena.
	 * <p>
	 * Every vertex of a block entity the game draws each frame comes through here, twice with the light's map:
	 * a Create water wheel or a Mekanism wind generator is thousands of them, and they are drawn again however
	 * still the wheel stands. So the stream is written straight into mapped memory rather than through a
	 * {@link ByteBuffer}'s checked accessors, the same native-order bytes the buffer wrote, and only what the
	 * programs read is worked out: a shadow program taking the owner's ids alone gets no tangents.
	 */
	private static FrameArena.Slice derive(FrameArena arena, MeshData.DrawState state, FrameArena.Slice vertices,
			DrawIds[] ids, int reads) {
		int count = state.vertexCount();
		FrameArena.Slice slice = arena.allocate(Math.multiplyExact(count, PackPipeline.Derived.STRIDE));
		long out = MemoryUtil.memAddress0(slice.bytes());
		boolean readsIds = (reads & PackPipeline.Derived.READS_IDS) != 0;
		for (int vertex = 0; vertex < count; vertex++) {
			long at = out + (long) vertex * PackPipeline.Derived.STRIDE;
			DrawIds id = readsIds && ids != null && vertex < ids.length ? ids[vertex] : DrawIds.NONE;
			id.put(at + PackPipeline.Derived.IDENTIFIERS);
			MemoryUtil.memPutFloat(at + PackPipeline.Derived.MID_TEX, 0.5F);
			MemoryUtil.memPutFloat(at + PackPipeline.Derived.MID_TEX + 4, 0.5F);
			MemoryUtil.memPutInt(at + PackPipeline.Derived.TANGENT, EntityFrame.FLAT);
		}
		if (state.mode() == VertexFormat.Mode.QUADS
				&& (reads & (PackPipeline.Derived.READS_MID_TEX | PackPipeline.Derived.READS_TANGENT)) != 0) {
			fillQuadFrames(out, state, vertices.bytes(), (reads & PackPipeline.Derived.READS_TANGENT) != 0);
		}
		return slice;
	}

	/**
	 * Each quad's sprite middle and, where it is read, tangent. Other primitives keep the stand-ins above.
	 * <p>
	 * The tangent of a corner depends on the quad's first three corners and on that corner's own normal, so the
	 * corners of a flat quad, which carry one normal between them, all get the same word: it is worked out once and
	 * written four times, and again only where a corner's normal differs from the one before it. The arithmetic is
	 * the same, in the same order, so the words are the same bits.
	 *
	 * @param source the mesh's vertices in mapped memory, from their first byte
	 */
	private static void fillQuadFrames(long out, MeshData.DrawState state, ByteBuffer source, boolean tangents) {
		VertexFormat format = state.format();
		List<String> names = format.getElementAttributeNames();
		int positionIndex = names.indexOf("Position");
		int uvIndex = names.indexOf("UV0");
		int normalIndex = names.indexOf("Normal");
		if (positionIndex < 0 || uvIndex < 0) return;
		VertexFormatElement position = format.getElements().get(positionIndex);
		VertexFormatElement uv = format.getElements().get(uvIndex);
		if (position.type() != VertexFormatElement.Type.FLOAT || position.count() < 3
				|| uv.type() != VertexFormatElement.Type.FLOAT || uv.count() < 2) return;
		VertexFormatElement normal = normalIndex < 0 ? null : format.getElements().get(normalIndex);
		if (normal != null && (normal.type() != VertexFormatElement.Type.BYTE || normal.count() < 3)) normal = null;
		int stride = format.getVertexSize();
		int count = state.vertexCount();
		if ((long) count * stride > source.capacity()) return;
		long in = MemoryUtil.memAddress0(source);
		int p = format.getOffset(position);
		int t = format.getOffset(uv);
		int n = normal == null ? -1 : format.getOffset(normal);
		boolean flatten = n >= 0;
		float[] face = new float[3];
		int quadStride = 4 * PackPipeline.Derived.STRIDE;
		for (int first = 0; first + 3 < count; first += 4) {
			long c0 = in + (long) first * stride;
			long c1 = c0 + stride;
			long c2 = c1 + stride;
			long c3 = c2 + stride;
			float x0 = MemoryUtil.memGetFloat(c0 + p), y0 = MemoryUtil.memGetFloat(c0 + p + 4), z0 = MemoryUtil.memGetFloat(c0 + p + 8);
			float x1 = MemoryUtil.memGetFloat(c1 + p), y1 = MemoryUtil.memGetFloat(c1 + p + 4), z1 = MemoryUtil.memGetFloat(c1 + p + 8);
			float x2 = MemoryUtil.memGetFloat(c2 + p), y2 = MemoryUtil.memGetFloat(c2 + p + 4), z2 = MemoryUtil.memGetFloat(c2 + p + 8);
			float u0 = MemoryUtil.memGetFloat(c0 + t), v0 = MemoryUtil.memGetFloat(c0 + t + 4);
			float u1 = MemoryUtil.memGetFloat(c1 + t), v1 = MemoryUtil.memGetFloat(c1 + t + 4);
			float u2 = MemoryUtil.memGetFloat(c2 + t), v2 = MemoryUtil.memGetFloat(c2 + t + 4);
			float u3 = MemoryUtil.memGetFloat(c3 + t), v3 = MemoryUtil.memGetFloat(c3 + t + 4);
			// Summed from nought corner by corner, as it always was: the same float, to the last bit.
			float midU = 0.0F;
			float midV = 0.0F;
			midU += u0 * 0.25F;
			midV += v0 * 0.25F;
			midU += u1 * 0.25F;
			midV += v1 * 0.25F;
			midU += u2 * 0.25F;
			midV += v2 * 0.25F;
			midU += u3 * 0.25F;
			midV += v3 * 0.25F;

			long quad = out + (long) first * PackPipeline.Derived.STRIDE;
			for (long dst = quad; dst < quad + quadStride; dst += PackPipeline.Derived.STRIDE) {
				MemoryUtil.memPutFloat(dst + PackPipeline.Derived.MID_TEX, midU);
				MemoryUtil.memPutFloat(dst + PackPipeline.Derived.MID_TEX + 4, midV);
			}
			if (!tangents) continue;

			boolean hasFace = n < 0 && EntityFrame.faceNormal(face, x0, y0, z0, x1, y1, z1, x2, y2, z2,
					MemoryUtil.memGetFloat(c3 + p), MemoryUtil.memGetFloat(c3 + p + 4), MemoryUtil.memGetFloat(c3 + p + 8));
			int tangent = 0;
			// NaN matches nothing, so the first corner is always worked out.
			float lastX = Float.NaN;
			float lastY = Float.NaN;
			float lastZ = Float.NaN;
			for (int corner = 0; corner < 4; corner++) {
				long at = c0 + (long) corner * stride;
				float nx = n >= 0 ? Math.max(-1.0F, MemoryUtil.memGetByte(at + n) / 127.0F) : hasFace ? face[0] : 0.0F;
				float ny = n >= 0 ? Math.max(-1.0F, MemoryUtil.memGetByte(at + n + 1) / 127.0F) : hasFace ? face[1] : 0.0F;
				float nz = n >= 0 ? Math.max(-1.0F, MemoryUtil.memGetByte(at + n + 2) / 127.0F) : hasFace ? face[2] : 1.0F;
				if (nx != lastX || ny != lastY || nz != lastZ) {
					tangent = EntityFrame.tangent(nx, ny, nz, flatten,
							x0, y0, z0, u0, v0,
							x1, y1, z1, u1, v1,
							x2, y2, z2, u2, v2);
					lastX = nx;
					lastY = ny;
					lastZ = nz;
				}
				MemoryUtil.memPutInt(quad + (long) corner * PackPipeline.Derived.STRIDE + PackPipeline.Derived.TANGENT, tangent);
			}
		}
	}

	boolean derived() {
		return this.derived != null;
	}

	void draw(VkCommandBuffer cmd, MemoryStack stack) {
		if (this.derived != null) {
			vkCmdBindVertexBuffers(cmd, 0, stack.longs(this.vertexBuffer, this.constants.buffer(), this.derived.buffer()),
					stack.longs(this.vertexOffset, this.constants.offset(), this.derived.offset()));
		} else {
			vkCmdBindVertexBuffers(cmd, 0, stack.longs(this.vertexBuffer, this.constants.buffer()),
					stack.longs(this.vertexOffset, this.constants.offset()));
		}
		if (this.indexBuffer == 0L) {
			vkCmdDraw(cmd, this.count, 1, 0, 0);
		} else {
			vkCmdBindIndexBuffer(cmd, this.indexBuffer, this.indexOffset, this.indexType);
			vkCmdDrawIndexed(cmd, this.count, 1, 0, 0, 0);
		}
	}
}
