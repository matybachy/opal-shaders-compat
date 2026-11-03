package dev.vitrailvk.render;

import dev.vitrailvk.vk.HostBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/** Host-visible mesh data retained until VulkanMod reuses the corresponding frame slot. */
final class FrameArena implements AutoCloseable {
	private static final int PAGE_BYTES = 4 << 20;
	private static final int ALIGNMENT = 16;
	private final Map<Integer, List<Page>> frames = new HashMap<>();
	private List<Page> pages;
	private int pageIndex;

	record Slice(long buffer, long offset, ByteBuffer bytes) {
	}

	private static final class Page {
		final int size;
		final HostBuffer buffer;
		int used;

		Page(int size) {
			this.size = size;
			this.buffer = new HostBuffer(size, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
					ALIGNMENT);
		}

		void rewind() {
			this.used = 0;
			this.buffer.rewind();
		}
	}

	/** Call only after the frame slot's fence has been waited by VulkanMod. */
	void begin(int frame) {
		this.pages = this.frames.computeIfAbsent(frame, unused -> new ArrayList<>());
		this.pages.forEach(Page::rewind);
		this.pageIndex = 0;
	}

	Slice allocate(int bytes) {
		if (this.pages == null) throw new IllegalStateException("Frame arena has not begun a frame");
		if (bytes <= 0) throw new IllegalArgumentException("Mesh allocation must be positive");
		while (this.pageIndex < this.pages.size()) {
			Page page = this.pages.get(this.pageIndex);
			long aligned = ((long) page.used + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT;
			if (aligned + bytes <= page.size) return allocate(page, bytes);
			this.pageIndex++;
		}
		Page page = new Page(Math.max(PAGE_BYTES, bytes));
		this.pages.add(page);
		return allocate(page, bytes);
	}

	private Slice allocate(Page page, int bytes) {
		long offset = page.buffer.allocate(bytes);
		page.used = (int) (offset + bytes);
		return new Slice(page.buffer.handle(), offset, page.buffer.view(offset, bytes).order(ByteOrder.nativeOrder()));
	}

	@Override
	public void close() {
		this.frames.values().forEach(list -> list.forEach(page -> page.buffer.close()));
		this.frames.clear();
		this.pages = null;
	}
}
