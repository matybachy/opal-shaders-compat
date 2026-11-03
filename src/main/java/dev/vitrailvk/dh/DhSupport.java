package dev.vitrailvk.dh;

import dev.vitrailvk.VitrailVk;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Whether the bridge to Distant Horizons stands, and what the renderer asks of it. Loads no class of that mod:
 * everything that does is behind {@link #ACTIVE}, in {@link VkDhBridge} and its neighbours.
 * <p>
 * Off with {@code -Dvitrailvk.dh=false}, and off for any version but the one the bridge is built against: it
 * stands in for internal classes of the mod ({@code core.wrapperInterfaces.render}), and those move.
 */
public final class DhSupport {

	private static final String MOD_ID = "distanthorizons";
	private static final String BUILT_AGAINST = "3.3.3";

	public static final boolean ACTIVE = decide();

	private DhSupport() {
	}

	private static boolean decide() {
		if ("false".equals(System.getProperty("vitrailvk.dh"))) return false;
		try {
			var file = FMLLoader.getLoadingModList().getModFileById(MOD_ID);
			if (file == null) return false;
			String version = file.getMods().stream().filter(mod -> mod.getModId().equals(MOD_ID)).findFirst()
					.map(IModInfo::getVersion).map(Object::toString).orElse("");
			if (version.startsWith(BUILT_AGAINST)) return true;
			VitrailVk.LOGGER.warn("Distant Horizons {} is not the {} the Vulkan bridge is built against; the bridge stays off",
					version, BUILT_AGAINST);
		} catch (RuntimeException | LinkageError e) {
			VitrailVk.LOGGER.warn("Cannot tell whether Distant Horizons is here; the Vulkan bridge stays off", e);
		}
		return false;
	}

	/** At the head of a level: the far water waits for the deferred stage while a pack draws. */
	public static void frame(boolean packRunning) {
		if (ACTIVE) VkDhBridge.frame(packRunning);
	}

	/** The far terrain's half from the light, while the shadow stage stands open. */
	public static void shadow(boolean water) {
		if (ACTIVE) VkDhBridge.shadow(water);
	}

	/** What the frame just past really drew with: {near, far, blocks}, or null where it drew nothing. */
	public static float[] planes() {
		return ACTIVE ? VkDhBridge.planes() : null;
	}
}
