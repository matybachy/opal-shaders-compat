package dev.opalcompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.opalcompat.world.CloudHeight;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The cloud height setting ({@link CloudHeight}), where the game and a shader pack's {@code cloudHeight} both read it. */
@Mixin(DimensionSpecialEffects.class)
public abstract class DimensionSpecialEffectsMixin {

	@ModifyReturnValue(method = "getCloudHeight", at = @At("RETURN"))
	private float opalcompat$cloudHeight(float dimension) {
		return CloudHeight.apply(dimension);
	}
}
