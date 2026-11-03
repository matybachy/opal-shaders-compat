package dev.vitrailvk.mixin;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import dev.vitrailvk.render.PackRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BufferUploader.class, priority = 1200)
public abstract class BufferUploaderMixin {
	// TODO-VERIFY: confirm priority and ownership relative to VulkanMod's immediate upload at runtime.
	@Inject(method = "_drawWithShader", at = @At("HEAD"), cancellable = true)
	private static void vitrailvk$drawWithShader(MeshData mesh, CallbackInfo ci) {
		if (PackRenderer.drawImmediate(mesh)) {
			mesh.close();
			ci.cancel();
		}
	}

	// TODO-VERIFY: VulkanMod cancels draw at its own HEAD; confirm this higher-priority hook runs first.
	@Inject(method = "draw", at = @At("HEAD"), cancellable = true)
	private static void vitrailvk$draw(MeshData mesh, CallbackInfo ci) {
		if (PackRenderer.drawImmediate(mesh)) {
			mesh.close();
			ci.cancel();
		}
	}
}
