package dev.opalcompat.dh;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import dev.opalcompat.OpalCompat;
import dev.opalcompat.render.PackRenderer;
import net.minecraft.client.Minecraft;

/**
 * Where Distant Horizons meets this mod. DH keeps its renderer behind one definition, and picks OpenGL's or
 * Blaze3D's ({@code common.wrappers.DependencySetup}); here it is handed a third, {@link VkDhDefinition}, whose
 * buffers are VulkanMod's and whose terrain is drawn by the pack's {@code dh_} programs. Everything else in DH runs
 * as it is: the database, the LOD generation, the quad tree, the meshes.
 * <p>
 * It is also told, through the accessor it has for Iris, that a pack is drawing and when the light's stage is
 * asking. That is what makes it defer its water, build its list a second time without the camera's frustum, and
 * leave the fog and the fades alone, as under Iris. The mod id is not touched: other mods see no Iris.
 */
public final class VkDhBridge {

	/** Set while the shadow stage asks for its half: what the terrain renderer draws goes into the map. */
	private static boolean shadowPass;
	private static boolean packRunning;
	private static boolean drewFrame;
	private static float[] planes;

	private VkDhBridge() {
	}

	/** From the mod's own constructor, ahead of every class that reads the accessor into a static. */
	public static void bindAccessor() {
		ModAccessorInjector.INSTANCE.bind(IIrisAccessor.class, new Accessor());
	}

	public static void bindRenderApi() {
		new VkDhDefinition().bindRenderers();
		OpalCompat.LOGGER.info("Distant Horizons renders through OpalCompat on VulkanMod Reforged");
	}

	static void frame(boolean running) {
		packRunning = running;
		try {
			DhApiRenderProxy.INSTANCE.setDeferTransparentRendering(running);
		} catch (RuntimeException | LinkageError e) {
			OpalCompat.LOGGER.error("Distant Horizons refused the deferred water switch", e);
		}
	}

	static void shadow(boolean water) {
		// Its render state is filled from the level's chunk layers, so not before one has been reached, and not
		// while it still holds the level the player just left.
		var level = ClientApi.RENDER_STATE.clientLevelWrapper;
		if (!drewFrame || level == null || level.getWrappedMcObject() != Minecraft.getInstance().level) return;
		shadowPass = true;
		try {
			if (water) {
				ClientApi.INSTANCE.renderDeferredLodsForShaders();
			} else {
				ClientApi.INSTANCE.renderLods();
			}
		} finally {
			shadowPass = false;
		}
	}

	static boolean shadowPass() {
		return shadowPass;
	}

	static void drew(float near, float far, int blocks) {
		drewFrame = true;
		planes = new float[] {near, far, blocks};
	}

	static float[] planes() {
		return planes;
	}

	static boolean draw(boolean opaque, boolean light, float near, float far, float[] corners, LodBuffer[][] sections, int count) {
		return PackRenderer.drawDistant(opaque, light, near, far, corners, sections, count);
	}

	private static final class Accessor implements IIrisAccessor {
		@Override
		public String getModName() {
			return "OpalCompat";
		}

		@Override
		public boolean isShaderPackInUse() {
			return packRunning;
		}

		@Override
		public boolean isRenderingShadowPass() {
			return shadowPass;
		}

		@Override
		public boolean isReverseZDuringShaders() {
			return false;
		}

		@Override
		public int getFramebufferDepthTextureId(Object framebuffer) {
			return -1;
		}
	}
}
