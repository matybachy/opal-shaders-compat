package dev.vitrailvk.mixin;

import dev.vitrailvk.dh.VkDhBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where DH binds its accessors to other mods, ahead of the classes that keep them in statics. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.neoforge.NeoforgeMain", remap = false)
public abstract class DhMainMixin {

	@Inject(method = "initializeModCompat", at = @At("TAIL"))
	private void vitrailvk$accessor(CallbackInfo ci) {
		VkDhBridge.bindAccessor();
	}
}
