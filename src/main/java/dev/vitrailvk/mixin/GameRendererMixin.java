package dev.vitrailvk.mixin;

import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pack's full-screen chain runs once the level (and the hand) is drawn, before the GUI. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
			shift = At.Shift.AFTER))
	private void vitrailvk$afterLevel(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		PackRenderer.afterLevel();
	}
}
