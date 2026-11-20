package dev.opalcompat.mixin;

import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.VertexBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TerrainBufferBuilder.class, remap = false)
public interface TerrainBufferBuilderAccessor {

	@Accessor("vertexBuilder")
	VertexBuilder opalcompat$vertexBuilder();
}
