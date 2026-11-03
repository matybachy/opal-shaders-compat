package dev.vitrailvk;

import dev.vitrail.Vitrail;
import dev.vitrail.pack.option.EngineDefines;
import dev.vitrailvk.dev.AutoTest;
import dev.vitrailvk.pack.ShaderPacks;
import dev.vitrailvk.screen.ShaderPages;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = VitrailVk.MOD_ID, dist = Dist.CLIENT)
public final class VitrailVk {

	public static final String MOD_ID = "vitrailvk";
	public static final Logger LOGGER = LoggerFactory.getLogger("VitrailVK");

	public VitrailVk(IEventBus modBus, ModContainer container) {
		String vulkanMod = ModList.get().getModContainerById("vulkanmod")
				.map(c -> c.getModInfo().getVersion().toString())
				.orElseThrow(() -> new IllegalStateException("VulkanMod Reforged is not loaded"));
		LOGGER.info("{} {} running on VulkanMod Reforged {}", MOD_ID,
				container.getModInfo().getVersion(), vulkanMod);

		Vitrail.initClient(new NeoForgePlatform(container.getModInfo().getVersion().toString()));
		// Packs branch on MC_VERSION; Vitrail's default is 26.2.
		EngineDefines.machine(EngineDefines.Environment.of(GameVersion.MC_VERSION));

		ShaderPages.register();

		modBus.addListener(FMLClientSetupEvent.class, event -> {
			logDevice();
			if (Boolean.getBoolean("vitrailvk.reportAll")) {
				ShaderPacks.reportAll();
			} else {
				ShaderPacks.selectedPath().ifPresent(ShaderPacks::loadAndReport);
			}
		});

		if (AutoTest.ENABLED) {
			NeoForge.EVENT_BUS.register(AutoTest.INSTANCE);
		}
	}

	private static void logDevice() {
		if (DeviceManager.device != null) {
			LOGGER.info("Vulkan device: {}", DeviceManager.device.deviceName);
		} else {
			LOGGER.warn("VulkanMod Reforged has not created a Vulkan device yet");
		}
	}
}
