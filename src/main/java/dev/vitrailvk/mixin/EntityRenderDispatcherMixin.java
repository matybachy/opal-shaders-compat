package dev.vitrailvk.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	// TODO-VERIFY: confirm entity context nesting and deferred buffer submission in a live frame.
	@WrapMethod(method = "render")
	private <E extends Entity> void vitrailvk$entityIds(E entity, double x, double y, double z, float yaw,
			float tick, PoseStack poses, MultiBufferSource buffers, int light, Operation<Void> original) {
		PackRenderer.pushEntity(entity);
		try {
			original.call(entity, x, y, z, yaw, tick, poses, buffers, light);
		} finally {
			PackRenderer.popDrawIds();
		}
	}
}
