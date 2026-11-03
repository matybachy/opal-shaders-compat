package dev.opalcompat.dh;

import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.VertexBuffer;

import java.nio.ByteBuffer;

/** One buffer of far terrain on the device, in Distant Horizons' own sixteen byte vertex. */
public class LodBuffer {

	public static final int STRIDE = 16;

	private VertexBuffer vertices;
	private int count;

	/** On the render thread, DH's own {@code RenderThreadTaskHandler} sees to that. */
	protected final void upload(ByteBuffer data, int vertexCount) {
		free();
		if (vertexCount <= 0) return;
		VertexBuffer buffer = new VertexBuffer(vertexCount * STRIDE, MemoryTypes.GPU_MEM);
		buffer.copyToVertexBuffer(STRIDE, vertexCount, data);
		this.vertices = buffer;
		this.count = vertexCount;
		LodIndices.need(vertexCount);
	}

	/** Through VulkanMod's queue of frames: a recorded draw may still use it. */
	protected final void free() {
		if (this.vertices != null) this.vertices.freeBuffer();
		this.vertices = null;
		this.count = 0;
	}

	public final boolean ready() {
		return this.vertices != null;
	}

	public final long handle() {
		return this.vertices.getId();
	}

	public final long offset() {
		return this.vertices.getOffset();
	}

	public final int vertices() {
		return this.count;
	}

	/** Two triangles a quad, four vertices apart, DH's own order ({@code IndexBufferBuilder}). */
	public final int indices() {
		return this.count / 4 * 6;
	}
}
