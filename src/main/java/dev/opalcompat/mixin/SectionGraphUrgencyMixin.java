package dev.opalcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.opalcompat.terrain.ChunkFlow;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.RenderRegionBuilder;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.render.chunk.graph.SectionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Which sections are urgent is VulkanMod Reforged's own call, made while the section is still marked dirty: changed by the
 * player, or near the camera. It goes with the task that is made for the section: {@link ChunkFlow}.
 */
@Mixin(value = SectionGraph.class, remap = false)
public abstract class SectionGraphUrgencyMixin {

	@Shadow
	private boolean isUrgentRebuild(RenderSection section) {
		throw new AssertionError();
	}

	@WrapOperation(method = "schedulePendingRebuilds", at = @At(value = "INVOKE",
			target = "Lnet/vulkanmod/render/chunk/RenderSection;rebuildChunkAsync(Lnet/vulkanmod/render/chunk/build/TaskDispatcher;Lnet/vulkanmod/render/chunk/build/RenderRegionBuilder;)Z"))
	private boolean opalcompat$urgent(RenderSection section, TaskDispatcher dispatcher, RenderRegionBuilder regions, Operation<Boolean> original) {
		ChunkFlow.markNext(ChunkFlow.ENABLED && this.isUrgentRebuild(section));
		try {
			return original.call(section, dispatcher, regions);
		} finally {
			ChunkFlow.markNext(false);
		}
	}
}
