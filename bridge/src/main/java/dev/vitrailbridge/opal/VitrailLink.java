package dev.vitrailbridge.opal;

import dev.opal.client.Opal;
import dev.opal.client.OpalClient;
import dev.opal.client.ShaderOptionsScreen;
import dev.opal.client.ShaderPackScreen;
import dev.opal.config.OpalConfig;
import dev.opal.pack.OptionValues;
import dev.opal.pack.PackSource;
import dev.opal.pack.ShaderPack;
import dev.vitrail.Vitrail;
import dev.vitrail.settings.SettingsFile;
import dev.vitrailbridge.VitrailBridge;
import dev.vitrailvk.pack.ShaderPacks;
import dev.vitrailvk.render.PackRenderer;
import dev.vitrailvk.screen.ShaderPages;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps what Opal and Vitrail VK each have selected the same, whichever screen it was chosen on. */
final class VitrailLink {

	private static String seen;
	private static long settingsTime;
	private static boolean started;

	private VitrailLink() {
	}

	/** Vitrail's choice stands; Opal's counts when there is none, as in an instance set up with Opal alone. */
	static void start() {
		OpalConfig opal = OpalClient.config;
		String wanted = opal.enabled ? opal.pack : "";
		if (ShaderPacks.selectedName().isEmpty() && !wanted.isEmpty() && System.getProperty("vitrailvk.pack") == null) {
			select(wanted);
		}
		seen = ShaderPacks.selectedName();
		tellOpal(seen);
		settingsTime = settingsTime(seen);
		started = true;
		// Opal's screens look like Vectors' and work on the same files, so with Opal there they are the pack screens everywhere.
		ShaderPages.screens(ShaderPackScreen::new, VitrailLink::opalSettings);
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
	}

	private static Screen opalSettings(Screen parent, String pack) {
		Path directory = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
		try {
			return new ShaderOptionsScreen(parent, new ShaderPack(PackSource.open(directory.resolve(pack)), OptionValues.load(directory.resolve(pack + ".txt"))));
		} catch (IOException | RuntimeException e) {
			VitrailBridge.LOGGER.warn("Opal cannot read the settings of {}", pack, e);
			return null;
		}
	}

	/** Called on Opal's request to load; see {@code OpalOnVulkanMixin}. */
	static void opalChanged() {
		if (!started) return;
		OpalConfig opal = OpalClient.config;
		String wanted = opal.enabled ? opal.pack : "";
		if (!wanted.equals(ShaderPacks.selectedName())) {
			select(wanted);
			seen = wanted;
			settingsTime = settingsTime(wanted);
			PackRenderer.reload();
		} else {
			// Opal's settings screen saves and asks to load; so does every resource reload, which changes nothing for a pack.
			long time = settingsTime(wanted);
			if (time != settingsTime) {
				settingsTime = time;
				PackRenderer.reload();
			}
		}
	}

	private static void tick() {
		String selected = ShaderPacks.selectedName();
		if (!selected.equals(seen)) {
			seen = selected;
			settingsTime = settingsTime(selected);
			tellOpal(selected);
		}
		Opal.lastError = PackRenderer.lastError().orElse(null);
	}

	private static void select(String pack) {
		try {
			ShaderPacks.select(pack);
		} catch (IOException e) {
			VitrailBridge.LOGGER.error("Cannot save the shader pack chosen in Opal", e);
		}
	}

	private static void tellOpal(String pack) {
		OpalConfig opal = OpalClient.config;
		boolean enabled = !pack.isEmpty();
		if (opal.enabled == enabled && (!enabled || opal.pack.equals(pack))) return;
		opal.enabled = enabled;
		if (enabled) opal.pack = pack;
		try {
			opal.save(FMLPaths.CONFIGDIR.get().resolve("opal.json"));
		} catch (IOException e) {
			VitrailBridge.LOGGER.warn("Cannot save Opal's settings", e);
		}
	}

	private static long settingsTime(String pack) {
		if (pack.isEmpty()) return 0;
		Path file = SettingsFile.of(Vitrail.platform().gameDirectory(), pack);
		try {
			return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
		} catch (IOException e) {
			return 0;
		}
	}
}
