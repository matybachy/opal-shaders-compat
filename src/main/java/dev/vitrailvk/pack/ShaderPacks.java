package dev.vitrailvk.pack;

import dev.vitrail.Vitrail;
import dev.vitrail.pack.load.LoadedPack;
import dev.vitrail.pack.load.PackLoader;
import dev.vitrail.pack.load.PackReport;
import dev.vitrail.pack.option.OptionValue;
import dev.vitrail.pack.option.PackOption;
import dev.vitrail.pack.program.ProgramSet;
import dev.vitrail.settings.SettingsFile;
import dev.vitrailvk.VitrailVk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Which pack is selected, with which settings, and reading it with the loader taken from Vitrail.
 * <p>
 * {@code config/vitrailvk.properties}: {@code shaderPack=<name>}. Settings live in
 * {@code shaderpacks/<name>.txt}, the file Iris reads, one per pack. The system properties
 * {@code vitrailvk.pack}, {@code vitrailvk.profile} and {@code vitrailvk.options} ({@code A=b,C=d}) go over
 * them (the autotest sets them) until a pack is chosen in the game. An empty pack name means no pack.
 */
public final class ShaderPacks {

	private static final String CONFIG = "vitrailvk.properties";
	private static final String KEY = "shaderPack";

	/** Asked for every frame; the file is read once. */
	private static String selected;
	private static String missingNamed;

	private ShaderPacks() {
	}

	public static Path directory() {
		return PackLoader.directory(Vitrail.platform().gameDirectory());
	}

	private static Properties config() {
		Properties properties = new Properties();
		Path file = configFile();
		if (Files.isRegularFile(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				properties.load(in);
			} catch (IOException e) {
				VitrailVk.LOGGER.warn("Cannot read {}", file, e);
			}
		}
		return properties;
	}

	private static void store(Properties properties) throws IOException {
		Files.createDirectories(configFile().getParent());
		try (OutputStream out = Files.newOutputStream(configFile())) {
			properties.store(out, "VitrailVK");
		}
	}

	public static String selectedName() {
		if (selected == null) {
			String forced = System.getProperty("vitrailvk.pack");
			selected = forced != null ? forced.trim() : config().getProperty(KEY, "").trim();
		}
		return selected;
	}

	public static void select(String name) throws IOException {
		Properties properties = config();
		properties.setProperty(KEY, name);
		store(properties);
		selected = name;
	}

	public static String profile() {
		return System.getProperty("vitrailvk.profile", "").trim();
	}

	/** The settings changed from the selected pack's defaults. */
	public static Map<String, OptionValue> options() {
		Map<String, OptionValue> options = new LinkedHashMap<>();
		String name = selectedName();
		if (!name.isEmpty()) {
			Path file = SettingsFile.of(Vitrail.platform().gameDirectory(), name);
			try {
				SettingsFile.read(file).values().forEach((option, value) -> options.put(option, OptionValue.parse(value)));
			} catch (IOException e) {
				VitrailVk.LOGGER.warn("Cannot read {}, the pack draws with its own defaults", file, e);
			}
		}
		String forced = System.getProperty("vitrailvk.options", "");
		for (String pair : forced.split(",")) {
			int at = pair.indexOf('=');
			if (at > 0) {
				options.put(pair.substring(0, at).trim(), OptionValue.parse(pair.substring(at + 1)));
			}
		}
		return options;
	}

	/** The selected pack on disk, if one is selected and present. */
	public static Optional<Path> selectedPath() {
		String name = selectedName();
		if (name.isEmpty()) return Optional.empty();
		Path path = directory().resolve(name);
		if (!Files.exists(path)) {
			if (!name.equals(missingNamed)) VitrailVk.LOGGER.warn("Selected shader pack {} is not in {}", name, directory());
			missingNamed = name;
			return Optional.empty();
		}
		return Optional.of(path);
	}

	/** Reads one pack and writes Vitrail's report plus the full program and option lists. */
	public static Optional<LoadedPack> loadAndReport(Path path) {
		try {
			LoadedPack pack = PackLoader.load(path);
			PackReport.log(pack);
			logPrograms(pack);
			logOptions(pack);
			return Optional.of(pack);
		} catch (IOException | RuntimeException e) {
			PackReport.couldNotRead(path.getFileName().toString(), e);
			return Optional.empty();
		}
	}

	/** Every pack in {@code shaderpacks/}, for {@code -Dvitrailvk.reportAll=true}. */
	public static void reportAll() {
		try {
			List<Path> candidates = PackLoader.candidates(Vitrail.platform().gameDirectory());
			VitrailVk.LOGGER.info("[pack] {} candidates in {}", candidates.size(), directory());
			candidates.forEach(ShaderPacks::loadAndReport);
		} catch (IOException e) {
			VitrailVk.LOGGER.warn("Cannot list {}", directory(), e);
		}
	}

	private static void logPrograms(LoadedPack pack) {
		Map<String, Map<String, List<String>>> byPlace = new TreeMap<>();
		for (ProgramSet.ProgramKey key : pack.programs().keys()) {
			String place = key.dimension().isEmpty() ? "(root)" : key.dimension();
			String name = key.name().isCompute() ? key.name().baseName() + "_" + key.name().computeSuffix()
					: key.name().baseName();
			byPlace.computeIfAbsent(place, p -> new TreeMap<>())
					.computeIfAbsent(name, n -> new ArrayList<>())
					.add(key.stage().name().toLowerCase(Locale.ROOT));
		}
		byPlace.forEach((place, programs) -> VitrailVk.LOGGER.info("[pack] programs {} ({}): {}", place,
				programs.size(), programs.entrySet().stream()
						.map(e -> e.getKey() + e.getValue().stream().sorted().collect(Collectors.joining(",", "[", "]")))
						.collect(Collectors.joining(" "))));
	}

	private static void logOptions(LoadedPack pack) {
		List<PackOption> options = pack.options().all().stream()
				.sorted(Comparator.comparing(PackOption::name)).toList();
		StringBuilder line = new StringBuilder();
		int written = 0;
		for (PackOption option : options) {
			String value = switch (option.kind()) {
				case TOGGLE -> option.defaultOff() ? "off" : "on";
				default -> option.defaultText();
			};
			line.append(option.name()).append('=').append(value);
			if (option.hasValueList()) line.append('{').append(option.values().size()).append('}');
			line.append(' ');
			if (++written % 12 == 0) {
				VitrailVk.LOGGER.info("[pack] options {}", line.toString().trim());
				line.setLength(0);
			}
		}
		if (!line.isEmpty()) VitrailVk.LOGGER.info("[pack] options {}", line.toString().trim());
		VitrailVk.LOGGER.info("[pack] {}: {} programs, {} options", pack.packName(), pack.programs().count(),
				options.size());
	}

	private static Path configFile() {
		return Vitrail.platform().gameDirectory().resolve("config").resolve(CONFIG);
	}
}
