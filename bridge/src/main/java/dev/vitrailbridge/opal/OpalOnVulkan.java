package dev.vitrailbridge.opal;

import dev.opal.client.OpalClient;
import dev.vitrailbridge.Mods;
import dev.vitrailbridge.VitrailBridge;

/** Opal on VulkanMod Reforged: its screens and keys stay, what they ask for is drawn by Vitrail VK. */
public final class OpalOnVulkan {

	private OpalOnVulkan() {
	}

	public static void start() {
		if (Mods.VITRAIL) {
			VitrailLink.start();
		} else {
			// Nothing would draw the pack. Opal's screens say why with this.
			OpalClient.conflict = "VulkanMod Reforged, Vitrail VK missing";
			VitrailBridge.LOGGER.warn("Opal cannot draw on VulkanMod Reforged and Vitrail VK, which can, is not installed: no shader packs");
		}
	}

	/** Opal was asked to load again: another pack, on or off, other settings, or just new resources. */
	public static void requested() {
		if (Mods.VITRAIL) VitrailLink.opalChanged();
	}
}
