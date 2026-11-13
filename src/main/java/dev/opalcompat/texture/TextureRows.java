package dev.opalcompat.texture;

import dev.opalcompat.perf.LeanFrame;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The part of an image an upload reads.
 * <p>
 * VulkanMod Reforged copies the whole image an upload is given into its staging buffer and has the device read a
 * rectangle out of that copy. For an animated sprite the image is every frame of the animation and the rectangle is
 * one of them, so each frame of each animation copied all of them, twenty times a second. Here the upload is given the
 * rows the rectangle lies in and nothing else; the device reads the same texels.
 */
public final class TextureRows {

	private TextureRows() {
	}

	/**
	 * @param rowLength texels from one row of the image to the next, 0 for the rectangle's width
	 * @param texel     bytes a texel
	 * @return the bytes from the rectangle's first texel to its last, or null where the image is to be handed on whole
	 */
	public static ByteBuffer read(ByteBuffer image, int width, int height, int skipRows, int skipPixels, int rowLength, int texel) {
		if (image == null) return null;
		long whole = image.limit();
		LeanFrame.uploadBytes += whole;
		if (!LeanFrame.ENABLED || image.position() != 0 || texel <= 0 || width <= 0 || height <= 0
				|| skipRows < 0 || skipPixels < 0 || rowLength < 0) return null;
		long stride = rowLength == 0 ? width : rowLength;
		if (stride < width) return null;
		// Where VulkanMod Reforged has the device begin reading, and how far the rectangle reaches from there.
		long first = ((long) rowLength * skipRows + skipPixels) * texel;
		long length = ((height - 1) * stride + width) * texel;
		if (first + length > whole || length >= whole) return null;
		LeanFrame.uploadBytesLeftOut += whole - length;
		return MemoryUtil.memByteBuffer(MemoryUtil.memAddress0(image) + first, (int) length);
	}
}
