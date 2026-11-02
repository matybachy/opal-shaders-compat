package dev.vitrailvk.mixin;

import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.render.chunk.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pack's terrain programs draw the chunk layers into its own targets instead of VulkanMod's shader. */
@Mixin(value = WorldRenderer.class, remap = false)
public abstract class WorldRendererMixin {

	@Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
	private void vitrailvk$terrain(RenderType renderType, double camX, double camY, double camZ, Matrix4f modelView,
			Matrix4f projection, CallbackInfo ci) {
		if (PackRenderer.drawTerrain(renderType, camX, camY, camZ)) {
			ci.cancel();
		}
	}
}
