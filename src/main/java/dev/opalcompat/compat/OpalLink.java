package dev.opalcompat.compat;

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
import dev.opalcompat.OpalCompat;
import dev.opalcompat.pack.ShaderPacks;
import dev.opalcompat.render.PackRenderer;
import dev.opalcompat.screen.ShaderPages;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opal on VulkanMod Reforged: its screens and keys stay, and what they ask for is drawn on the Vulkan side, whichever screen it was chosen on. */
public final class OpalLink {

	private static String seen;
	private static long settingsTime;
	private static boolean started;

	private OpalLink() {
	}

	/** What the Vulkan side has stands; Opal's choice counts when there is none, as in an instance set up with Opal alone. */
	static void start() {
		OpalConfig opal = OpalClient.config;
		String wanted = opal.enabled ? opal.pack : "";
		if (ShaderPacks.selectedName().isEmpty() && !wanted.isEmpty() && System.getProperty("opalcompat.pack") == null) {
			select(wanted);
		}
		seen = ShaderPacks.selectedName();
		tellOpal(seen);
		settingsTime = settingsTime(seen);
		started = true;
		// Opal's screens look like Vectors' and work on the same files, so with Opal there they are the pack screens everywhere.
		ShaderPages.screens(ShaderPackScreen::new, OpalLink::opalSettings);
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
	}

	private static Screen opalSettings(Screen parent, String pack) {
		Path directory = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
		try {
			return new ShaderOptionsScreen(parent, new ShaderPack(PackSource.open(directory.resolve(pack)), OptionValues.load(directory.resolve(pack + ".txt"))));
		} catch (IOException | RuntimeException e) {
			OpalCompat.LOGGER.warn("Opal cannot read the settings of {}", pack, e);
			return null;
		}
	}

	/** Called on Opal's request to load; see {@code OpalOnVulkanMixin}. */
	public static void opalChanged() {
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
			OpalCompat.LOGGER.error("Cannot save the shader pack chosen in Opal", e);
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
			OpalCompat.LOGGER.warn("Cannot save Opal's settings", e);
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
