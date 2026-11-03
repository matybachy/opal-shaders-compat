package dev.vitrailvk.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vitrailvk.render.HandPass;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void vitrailvk$selectHand(AbstractClientPlayer player, float tick, float pitch, InteractionHand hand,
			float swing, ItemStack stack, float equip, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (HandPass.skip(stack)) ci.cancel();
	}
}
