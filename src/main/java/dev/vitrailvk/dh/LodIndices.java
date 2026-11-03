package dev.vitrailvk.dh;

import dev.vitrailvk.vk.HostBuffer;
import net.vulkanmod.vulkan.memory.MemoryManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

/** The one quad index buffer every {@link LodBuffer} is drawn with. Grows; what it replaces lives on for the frames using it. */
public final class LodIndices {

	private static HostBuffer buffer;
	private static int quads;
	private static int wanted;

	private LodIndices() {
	}

	static void need(int vertices) {
		wanted = Math.max(wanted, vertices / 4);
	}

	/** The buffer, for as many quads as any upload so far holds. */
	public static long handle() {
		if (buffer == null || quads < wanted) grow();
		return buffer.handle();
	}

	private static void grow() {
		int size = Math.max(1024, Math.max(wanted, quads * 2));
		HostBuffer next = new HostBuffer(size * 24L, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 4);
		ByteBuffer bytes = next.view(next.allocate(size * 24), size * 24).order(ByteOrder.LITTLE_ENDIAN);
		for (int quad = 0; quad < size; quad++) {
			int at = quad * 4;
			bytes.putInt(at).putInt(at + 1).putInt(at + 2).putInt(at + 2).putInt(at + 3).putInt(at);
		}
		HostBuffer old = buffer;
		if (old != null) MemoryManager.getInstance().addFrameOp(old::close);
		buffer = next;
		quads = size;
	}
}
