package dev.opalcompat.mixin;

import dev.opalcompat.terrain.ExtendedVertexBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.vulkanmod.render.chunk.build.LiquidRenderer;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A fluid's quads carry the fluid's own block, as Iris gives them ({@code fluidState.createLegacyBlock()}).
 * <p>
 * VulkanMod Reforged's {@code BuildTask} hands the builder the state of the block the fluid stands in and then draws
 * the fluid ({@code setBlockAttributes(blockState)}, then {@code renderLiquid}). For a water block that is water, but
 * for a waterlogged one, glow lichen, a slab, a fence, it is that block: the water in it took the block's number, the
 * pack did not know it for water and drew it as a plain translucent quad, flat blue amid water drawn as water. The
 * block that follows sets its own state again before its quads.
 */
@Mixin(value = LiquidRenderer.class, remap = false)
public abstract class LiquidRendererMixin {

	@Inject(method = "renderLiquid", at = @At("HEAD"))
	private void opalcompat$fluidBlock(BlockState blockState, FluidState fluidState, BlockPos pos,
			TerrainBufferBuilder builder, CallbackInfo ci) {
		if (((TerrainBufferBuilderAccessor) builder).opalcompat$vertexBuilder() instanceof ExtendedVertexBuilder extended) {
			extended.block(fluidState.createLegacyBlock());
		}
	}
}
