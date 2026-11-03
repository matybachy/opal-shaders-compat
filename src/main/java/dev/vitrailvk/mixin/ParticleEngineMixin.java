package dev.vitrailvk.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.vitrailvk.render.Families;
import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	// TODO-VERIFY: confirm particle render types and phase boundaries with NeoForge at runtime.
	@WrapMethod(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V")
	private void vitrailvk$particlePhase(LightTexture light, Camera camera, float tick, Frustum frustum,
			Predicate<ParticleRenderType> predicate, Operation<Void> original) {
		PackRenderer.pushPhase(Families.Phase.PARTICLES);
		try {
			original.call(light, camera, tick, frustum, predicate);
		} finally {
			PackRenderer.popPhase();
		}
	}
}
