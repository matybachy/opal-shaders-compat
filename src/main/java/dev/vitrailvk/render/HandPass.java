package dev.vitrailvk.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vitrail.render.HandDraw;
import dev.vitrailvk.mixin.GameRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Two hand submissions around the deferred stage, following Iris's 1.21.1 ordering. */
public final class HandPass {
	private static boolean active;
	private static boolean solid;

	private HandPass() {
	}

	public static boolean active() {
		return active;
	}

	public static boolean skip(ItemStack stack) {
		boolean translucent = stack.getItem() instanceof BlockItem block
				&& ItemBlockRenderTypes.getChunkRenderType(block.getBlock().defaultBlockState()) == RenderType.translucent();
		return active && solid == translucent;
	}

	// TODO-VERIFY: confirm hand transforms, depth scale, render hooks and light state in a live frame.
	public static void render(Camera camera, GameRenderer game, float tick, boolean opaque) {
		Minecraft mc = Minecraft.getInstance();
		GameRendererAccessor access = (GameRendererAccessor) game;
		if (!access.vitrailvk$renderHand() || game.isPanoramicMode() || camera.isDetached()
				|| !(camera.getEntity() instanceof Player) || mc.player == null || mc.gameMode == null
				|| mc.options.hideGui || mc.player.isSleeping() || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
		Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
		Matrix4f handProjection = new Matrix4f().scale(1.0F, 1.0F, 0.125F)
				.mul(game.getProjectionMatrix(access.vitrailvk$getFov(camera, tick, false)));
		PoseStack poses = new PoseStack();
		access.vitrailvk$bobHurt(poses, tick);
		if (mc.options.bobView().get()) access.vitrailvk$bobView(poses, tick);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		active = true;
		solid = opaque;
		HandDraw.drawing(true);
		PackRenderer.pushPhase(Families.Phase.HAND);
		modelView.pushMatrix();
		try (ByteBufferBuilder storage = new ByteBufferBuilder(1536)) {
			game.resetProjectionMatrix(handProjection);
			modelView.set(poses.last().pose());
			RenderSystem.applyModelViewMatrix();
			game.lightTexture().turnOnLightLayer();
			MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(storage);
			game.itemInHandRenderer.renderHandsWithItems(tick, new PoseStack(), buffers, mc.player,
					mc.getEntityRenderDispatcher().getPackedLightCoords(camera.getEntity(), tick));
		} finally {
			game.lightTexture().turnOffLightLayer();
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
			game.resetProjectionMatrix(projection);
			PackRenderer.popPhase();
			HandDraw.drawing(false);
			active = false;
		}
	}
}
