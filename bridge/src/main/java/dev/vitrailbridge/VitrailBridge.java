package dev.vitrailbridge;

import dev.vitrailbridge.dev.GlAutoTest;
import dev.vitrailbridge.gui.VulkanGui;
import dev.vitrailbridge.opal.OpalOnVulkan;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the mods in {@link Mods} need so that they run in one game. With VulkanMod Reforged, Opal's OpenGL renderer stands
 * down and its choice of pack goes to Vitrail VK, and the settings of all of them are on VulkanMod's screen. Without it,
 * Opal is the renderer and gets what {@link dev.vitrailbridge.mixin.BridgeMixinPlugin} lists for OpenGL.
 */
@Mod(value = VitrailBridge.MOD_ID, dist = Dist.CLIENT)
public final class VitrailBridge {

	public static final String MOD_ID = "vitrailbridge";
	public static final Logger LOGGER = LoggerFactory.getLogger("VitrailBridge");

	public VitrailBridge(IEventBus modBus, ModContainer container) {
		LOGGER.info("{} {}: VulkanMod Reforged {}, Vitrail VK {}, Opal {}, Vectors {}", MOD_ID, container.getModInfo().getVersion(),
				Mods.VULKAN, Mods.VITRAIL, Mods.OPAL, Mods.VECTORS);
		if (Mods.VULKAN) {
			if (Mods.OPAL) OpalOnVulkan.start();
			VulkanGui.register();
		}
		if (GlAutoTest.ENABLED) {
			NeoForge.EVENT_BUS.register(GlAutoTest.INSTANCE);
		}
	}
}
