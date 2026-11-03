package dev.vitrailvk.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vitrail.pack.source.ShadowCasters;
import dev.vitrail.render.PackValues;
import dev.vitrailvk.mixin.LevelRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.render.chunk.RenderSection;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Submits visible entities and block entities into the light's mesh pass. */
final class ShadowFeatures {
	private ShadowFeatures() {
	}

	// TODO-VERIFY: validate light frustum, client-side renderer side effects, buffer flushing and state restoration.
	static void render(PackValues values, Camera camera, float tick, Collection<RenderSection> sections,
			ShadowCasters casters) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !casters.anyFeature()) return;
		var position = camera.getPosition();
		Matrix4f view = new Matrix4f(), projection = new Matrix4f();
		values.drawnShadowPair(view, projection);
		Frustum frustum = new Frustum(view, projection);
		frustum.prepare(position.x, position.y, position.z);
		Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		PoseStack poses = new PoseStack();
		PackRenderer.pushPhase(Families.Phase.SHADOW);
		modelView.pushMatrix();
		try (ByteBufferBuilder storage = new ByteBufferBuilder(4096)) {
			modelView.set(view);
			RenderSystem.applyModelViewMatrix();
			mc.gameRenderer.resetProjectionMatrix(projection);
			MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(storage);
			if (casters.entities() || casters.player()) {
				for (Entity entity : mc.level.entitiesForRendering()) {
					if ((!casters.entities() && entity != mc.player) || entity.isSpectator()
							|| !mc.getEntityRenderDispatcher().shouldRender(entity, frustum, position.x, position.y, position.z)) continue;
					((LevelRendererAccessor) mc.levelRenderer).vitrailvk$renderEntity(entity, position.x, position.y,
							position.z, tick, poses, buffers);
				}
			}
			if (casters.anyBlockEntity()) {
				Set<BlockEntity> blocks = new LinkedHashSet<>();
				for (RenderSection section : sections) blocks.addAll(section.getCompiledSection().getBlockEntities());
				mc.levelRenderer.iterateVisibleBlockEntities(blocks::add);
				for (BlockEntity block : blocks) {
					if (block.isRemoved()) continue;
					var pos = block.getBlockPos();
					if (casters.emittersOnly() && block.getBlockState().getLightEmission(mc.level, pos) == 0) continue;
					poses.pushPose();
					try {
						poses.translate(pos.getX() - position.x, pos.getY() - position.y, pos.getZ() - position.z);
						mc.getBlockEntityRenderDispatcher().render(block, tick, poses, buffers);
					} finally {
						poses.popPose();
					}
				}
			}
			buffers.endBatch();
		} finally {
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
			mc.gameRenderer.resetProjectionMatrix(oldProjection);
			PackRenderer.popPhase();
		}
	}
}
