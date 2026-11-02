package dev.vitrailvk.mixin;

import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DrawBuffers.DrawParameters.class, remap = false)
public interface DrawParametersAccessor {

	@Accessor("indexCount")
	int vitrailvk$indexCount();

	@Accessor("firstIndex")
	int vitrailvk$firstIndex();

	@Accessor("vertexOffset")
	int vitrailvk$vertexOffset();

	@Accessor("baseInstance")
	int vitrailvk$baseInstance();
}
