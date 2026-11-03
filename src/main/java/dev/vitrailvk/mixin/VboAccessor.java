package dev.vitrailvk.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.IndexUploadState;
import net.vulkanmod.render.VBO;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a pack program needs to draw a {@code VBO} where it lies on the GPU. */
@Mixin(value = VBO.class, remap = false)
public interface VboAccessor {

	@Accessor("vertexBuffer")
	VertexBuffer vitrailvk$vertexBuffer();

	@Accessor("indices")
	IndexUploadState<IndexBuffer> vitrailvk$indices();

	@Accessor("indexCount")
	int vitrailvk$indexCount();

	@Accessor("vertexCount")
	int vitrailvk$vertexCount();

	@Accessor("nativeCountSelected")
	boolean vitrailvk$nativeCountSelected();

	@Accessor("nativeIndexCount")
	int vitrailvk$nativeIndexCount();

	@Accessor("mode")
	VertexFormat.Mode vitrailvk$mode();

	@Accessor("format")
	VertexFormat vitrailvk$format();

	@Accessor("transientGeometry")
	boolean vitrailvk$transientGeometry();

	@Invoker("hasGeometry")
	boolean vitrailvk$hasGeometry();
}
