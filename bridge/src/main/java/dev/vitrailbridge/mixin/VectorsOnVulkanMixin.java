package dev.vitrailbridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vectors suspends its entity, block entity and weather limits when a shader mod is loaded, and counts Opal as one. On
 * VulkanMod Reforged Opal draws nothing, and Vitrail VK asks the same dispatchers for the shadow pass as for the world, so
 * the limits mean the same with a pack. Without this the Workload page would be settings that do nothing.
 */
@Pseudo
@Mixin(targets = "dev.vector.fabric.Compatibility", remap = false)
public abstract class VectorsOnVulkanMixin {

	@Inject(method = "shaders", at = @At("HEAD"), cancellable = true)
	private static void vitrailbridge$noOpalRenderer(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
