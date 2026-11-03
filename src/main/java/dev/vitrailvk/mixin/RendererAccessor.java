package dev.vitrailvk.mixin;

import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The polygon offset the game has asked for, which a pack pipeline applies as depth bias. */
@Mixin(value = Renderer.class, remap = false)
public interface RendererAccessor {

	@Accessor("appliedDepthBiasUnits")
	float vitrailvk$depthBiasUnits();

	@Accessor("appliedDepthBiasFactor")
	float vitrailvk$depthBiasFactor();
}
