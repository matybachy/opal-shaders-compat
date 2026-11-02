package dev.vitrailvk.mixin;

import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The level's matrices, and the start of the pack's frame. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void vitrailvk$beginLevel(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
			GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
			CallbackInfo ci) {
		PackRenderer.beginLevel(frustumMatrix, projectionMatrix);
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
			target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"))
	private void vitrailvk$beforeSky(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
			GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
			CallbackInfo ci) {
		var position = camera.getPosition();
		PackRenderer.beforeSky(position.x, position.y, position.z);
	}
}
