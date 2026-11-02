package dev.vitrailvk.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	// TODO-VERIFY: verify item and block-item identifiers through nested and custom item renderers.
	@WrapMethod(method = "render")
	private void vitrailvk$itemIds(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poses,
			MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original) {
		PackRenderer.pushItem(stack);
		try {
			original.call(stack, context, leftHand, poses, buffers, light, overlay, model);
		} finally {
			PackRenderer.popDrawIds();
		}
	}
}
