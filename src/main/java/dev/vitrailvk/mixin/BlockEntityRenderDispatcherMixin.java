package dev.vitrailvk.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	// TODO-VERIFY: confirm block-entity context and buffered vertices in a live frame.
	@WrapMethod(method = "render")
	private <E extends BlockEntity> void vitrailvk$blockEntityIds(E entity, float tick, PoseStack poses,
			MultiBufferSource buffers, Operation<Void> original) {
		PackRenderer.pushBlockEntity(entity);
		try {
			original.call(entity, tick, poses, buffers);
		} finally {
			PackRenderer.popDrawIds();
		}
	}
}
