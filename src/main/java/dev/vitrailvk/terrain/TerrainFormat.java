package dev.vitrailvk.terrain;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.vitrailvk.pack.ShaderPacks;

/**
 * VulkanMod Reforged's compressed terrain format with the elements shader packs read appended.
 * <p>
 * VulkanMod fixes the terrain stride when its classes initialise ({@code DrawBuffers.VERTEX_SIZE},
 * {@code POS_OFFSET}) and compares the format by identity in several places, so the extension is
 * decided once, at startup, by whether a shader pack is selected, and is installed as
 * {@code CustomVertexFormat.COMPRESSED_TERRAIN} itself. Switching between "a pack" and "no pack"
 * therefore needs a restart.
 * <p>
 * Element types are the combinations VulkanMod's {@code GraphicsPipeline} can turn into attribute
 * formats, because its own terrain pipeline is built against this format too.
 */
public final class TerrainFormat {

	/** Whether the mesh carries the extension this session. Read once. */
	public static final boolean EXTENDED = selected();

	public static final int BASE_STRIDE = 20;
	public static final int STRIDE = 40;

	public static final int OFFSET_BLOCK_ID = 20;
	public static final int OFFSET_MID_TEX = 24;
	public static final int OFFSET_NORMAL = 28;
	public static final int OFFSET_TANGENT = 32;
	public static final int OFFSET_MID_BLOCK = 36;

	private TerrainFormat() {
	}

	private static boolean selected() {
		try {
			return !ShaderPacks.selectedName().isEmpty();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** The compressed format VulkanMod built, with the five elements appended. */
	public static VertexFormat extend(VertexFormat base) {
		VertexFormat.Builder builder = VertexFormat.builder();
		for (String name : base.getElementAttributeNames()) {
			builder.add(name, base.getElements().get(base.getElementAttributeNames().indexOf(name)));
		}
		builder.add("BlockId", new VertexFormatElement(8, 0, VertexFormatElement.Type.INT, VertexFormatElement.Usage.GENERIC, 1));
		builder.add("MidTexCoord", new VertexFormatElement(9, 0, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 2));
		builder.add("Normal", new VertexFormatElement(10, 0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.NORMAL, 4));
		builder.add("Tangent", new VertexFormatElement(11, 0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.NORMAL, 4));
		builder.add("MidBlock", new VertexFormatElement(12, 0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.POSITION, 4));
		VertexFormat extended = builder.build();
		if (extended.getVertexSize() != STRIDE) {
			throw new IllegalStateException("Extended terrain format is " + extended.getVertexSize() + " bytes, expected " + STRIDE);
		}
		return extended;
	}
}
