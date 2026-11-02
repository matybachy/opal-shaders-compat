package dev.vitrailvk.compile;

import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.ProgramTranslator;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.glsl.VertexInputs;
import dev.vitrail.pack.load.PackLoader;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.pack.program.ProgramSet;
import dev.vitrail.pack.source.DimensionSet;
import dev.vitrail.pack.source.OpenedPack;
import dev.vitrail.pack.source.ShaderPackSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Translates every program of a pack with Vitrail's translator and compiles every unit to SPIR-V.
 * Failures are logged with the program, the stage and the line, and the translated text of a failing
 * unit is written next to the report so the line can be read in context.
 * <p>
 * Used out of game by {@link PackCompileTool}.
 */
public final class PackCompileCheck {

	private static final Logger LOGGER = LoggerFactory.getLogger("VitrailVK/compile");

	public record Failure(String program, String stage, String detail) {
	}

	public record Report(String pack, int programs, int units, int compiled, List<Failure> failures,
			long millis) {
		public boolean clean() {
			return failures.isEmpty() && compiled == units;
		}
	}

	private PackCompileCheck() {
	}

	public static Report check(Path packPath, SpirvCompiler compiler, Path dumpDir) throws IOException {
		long start = System.nanoTime();
		Map<String, Set<ProgramStage>> programs = new TreeMap<>();
		String packName;
		try (ShaderPackSource source = ShaderPackSource.open(packPath)) {
			packName = source.packName();
			ProgramSet set = ProgramSet.enumerate(source, DimensionSet.discover(source));
			for (ProgramSet.ProgramKey key : set.keys()) {
				String file = key.file();
				String path = file.substring(0, file.lastIndexOf('.'));
				programs.computeIfAbsent(path, p -> EnumSet.noneOf(ProgramStage.class)).add(key.stage());
			}
		}

		// Programs the pack's shaders.properties switches off under its default settings are not drawn,
		// and are not expected to compile either (BSL's shadowcomp needs MULTICOLORED_BLOCKLIGHT).
		Set<String> switchedOff = PackLoader.load(packPath).disabledPrograms();

		List<Failure> failures = new ArrayList<>();
		int units = 0;
		int compiled = 0;
		try (OpenedPack opened = OpenedPack.open(packPath, Map.of(), "")) {
			for (Map.Entry<String, Set<ProgramStage>> entry : programs.entrySet()) {
				String path = entry.getKey();
				if (switchedOff.contains(path)) {
					LOGGER.info("[compile] {} {}: switched off by shaders.properties", packName, path);
					continue;
				}
				Optional<ProgramTranslator.TranslatedProgram> program;
				try {
					program = translate(packPath, opened, path, entry.getValue());
				} catch (Exception e) {
					failures.add(new Failure(path, "translate", e.toString()));
					LOGGER.error("[compile] {} {}: translation failed", packName, path, e);
					continue;
				}
				if (program.isEmpty()) {
					LOGGER.info("[compile] {} {}: not served (stages {})", packName, path, entry.getValue());
					continue;
				}
				for (TranslatedUnit unit : program.get().stages().values()) {
					units++;
					String name = path + "." + unit.stage().extension();
					SpirvCompiler.Result result = compiler.compile(name, unit.text(), unit.stage());
					if (Boolean.getBoolean("vitrailvk.dumpAll")) dump(dumpDir, packName, name, unit.text());
					if (result.ok()) {
						compiled++;
						result.diagnostics().forEach(d -> LOGGER.debug("[compile] {} {}: {}", packName, name, d));
						continue;
					}
					StringBuilder detail = new StringBuilder();
					result.diagnostics().forEach(d -> detail.append("\n    ").append(d));
					failures.add(new Failure(path, unit.stage().extension(), detail.toString()));
					LOGGER.error("[compile] {} {}: {} error(s){}", packName, name, result.errors(), detail);
					dump(dumpDir, packName, name, unit.text());
				}
			}
		}
		Report report = new Report(packName, programs.size(), units, compiled, failures,
				(System.nanoTime() - start) / 1_000_000L);
		LOGGER.info("[compile] {}: {} programs, {}/{} units compiled, {} failures, {} ms", packName,
				report.programs(), compiled, units, failures.size(), report.millis());
		return report;
	}

	private static Optional<ProgramTranslator.TranslatedProgram> translate(Path packPath, OpenedPack opened,
			String path, Set<ProgramStage> stages) throws IOException {
		if (stages.contains(ProgramStage.COMPUTE)) {
			return PackProgram.loadCompute(opened, path).map(c -> c.loaded().program());
		}
		String name = path.substring(path.lastIndexOf('/') + 1);
		return PackProgram.load(packPath, path, inputsFor(name), Map.of(), "")
				.map(PackProgram.Loaded::program);
	}

	/** The vertex format each family is drawn with, as far as compiling it is concerned. */
	static VertexInputs inputsFor(String name) {
		if (name.matches("(composite|deferred|prepare|begin|shadowcomp|setup)\\d*|final")) return VertexInputs.FULLSCREEN;
		if (name.startsWith("dh_")) return VertexInputs.DISTANT;
		if (name.startsWith("gbuffers_terrain") || name.equals("gbuffers_water") || name.startsWith("shadow")) {
			return VertexInputs.TERRAIN;
		}
		if (name.startsWith("gbuffers_sky")) return VertexInputs.SKY;
		// 1.21.1 draws clouds as a plain mesh; Vitrail's CLOUDS prologue is the 26.x cell buffer.
		if (name.equals("gbuffers_clouds")) return VertexInputs.WORLD;
		if (name.equals("gbuffers_armor_glint")) return VertexInputs.GLINT;
		if (name.equals("gbuffers_damagedblock")) return VertexInputs.CRUMBLING;
		if (name.equals("gbuffers_line")) return VertexInputs.LINES;
		if (name.startsWith("gbuffers_particles") || name.equals("gbuffers_weather")
				|| name.startsWith("gbuffers_textured")) return VertexInputs.PARTICLE;
		if (name.startsWith("gbuffers_entities") || name.startsWith("gbuffers_hand")
				|| name.startsWith("gbuffers_block") || name.equals("gbuffers_beaconbeam")
				|| name.equals("gbuffers_spidereyes") || name.equals("gbuffers_lightning")) return VertexInputs.ENTITY;
		return VertexInputs.WORLD;
	}

	private static void dump(Path dumpDir, String pack, String name, String text) {
		if (dumpDir == null) return;
		try {
			Path file = dumpDir.resolve(pack.replaceAll("[^A-Za-z0-9._-]", "_")).resolve(name.replace('/', '_') + ".glsl");
			Files.createDirectories(file.getParent());
			Files.writeString(file, text);
		} catch (IOException e) {
			LOGGER.warn("Cannot dump {}", name, e);
		}
	}
}
