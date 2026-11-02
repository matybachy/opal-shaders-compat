package dev.vitrailvk.mixin;

import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.graph.SectionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = WorldRenderer.class, remap = false)
public interface WorldRendererAccessor {

	@Accessor("sectionGraph")
	SectionGraph vitrailvk$sectionGraph();

	@Invoker("sortTranslucentSections")
	void vitrailvk$sortTranslucentSections(double camX, double camY, double camZ);
}
