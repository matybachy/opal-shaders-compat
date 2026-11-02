package dev.vitrailvk.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.pack.source.OpenedPack;
import dev.vitrail.pack.target.ChainPlan;
import dev.vitrail.render.PackValues;
import dev.vitrail.uniform.UniformBlock;
import dev.vitrailvk.compile.SpirvCompiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lazily translates each format and family combination requested by a mesh draw. */
final class MeshPrograms implements AutoCloseable {
	private record Key(Families.Piece piece, VertexFormat format, boolean afterDeferred) {
	}

	record ShadowProgram(PackProgram.Loaded loaded, PackPipeline pipeline, UniformBlock uniforms) {
	}

	record Program(PackProgram.Loaded loaded, PackPipeline pipeline, UniformBlock uniforms, ChainPlan.Pass pass) {
	}

	private final OpenedPack pack;
	private final PackProgram.Chain chain;
	private final PackValues values;
	private final SpirvCompiler compiler;
	private final Map<Key, Optional<Program>> programs = new HashMap<>();
	private final Map<Key, Optional<ShadowProgram>> shadows = new HashMap<>();

	MeshPrograms(OpenedPack pack, PackProgram.Chain chain, PackValues values, SpirvCompiler compiler) {
		this.pack = pack;
		this.chain = chain;
		this.values = values;
		this.compiler = compiler;
	}

	Optional<Program> resolve(Families.Piece piece, VertexFormat format, boolean afterDeferred) {
		return this.programs.computeIfAbsent(new Key(piece, format, afterDeferred), this::load);
	}

	Optional<ShadowProgram> resolveShadow(Families.Piece piece, VertexFormat format) {
		return this.shadows.computeIfAbsent(new Key(piece, format, false), this::loadShadow);
	}

	// TODO-VERIFY: check shadow entity variants, uniforms and sampler bindings in a live frame.
	private Optional<ShadowProgram> loadShadow(Key key) {
		Families.Piece piece = key.piece();
		PackProgram.GeometryElement element = new PackProgram.GeometryElement("shadowMesh", piece.program(),
				piece.alpha(), piece.inputs(), piece.bound(), false);
		PackProgram.Loaded loaded;
		try {
			loaded = PackProgram.loadGeometry(this.pack, this.chain.place(), List.of(element)).get("shadowMesh");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (loaded == null) return Optional.empty();
		loaded = loaded.rebind(this.chain.targets(), Optional.empty());
		PackPipeline pipeline = new PackPipeline("shadowMesh:" + loaded.path(), loaded.program(), this.compiler,
				PackPipeline.Kind.MESH, key.format());
		try {
			UniformBlock uniforms = new UniformBlock(loaded.program().uniforms(), this.values.shadowGeometryCatalog());
			return Optional.of(new ShadowProgram(loaded, pipeline, uniforms));
		} catch (RuntimeException | Error e) {
			pipeline.close();
			throw e;
		}
	}
	// TODO-VERIFY: validate translated mesh programs and target halves during an actual frame.
	private Optional<Program> load(Key key) {
		Families.Piece piece = key.piece();
		PackProgram.GeometryElement element = new PackProgram.GeometryElement("mesh", piece.program(),
				piece.alpha(), piece.inputs(), piece.bound(), false);
		PackProgram.Loaded loaded;
		try {
			loaded = PackProgram.loadGeometry(this.pack, this.chain.place(), List.of(element)).get("mesh");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (loaded == null) return Optional.empty();
		String path = loaded.path();
		String served = path.substring(path.lastIndexOf('/') + 1);
		Optional<ChainPlan.Pass> pass = this.chain.chain().geometryOf(served, key.afterDeferred());
		if (pass.isEmpty()) return Optional.empty();
		var schedule = this.chain.targets().schedule();
		loaded = loaded.rebind(this.chain.targets(), key.afterDeferred()
				? schedule.stepAfterDeferred(served) : schedule.step(served));
		PackPipeline pipeline = new PackPipeline("mesh:" + path, loaded.program(), this.compiler,
				PackPipeline.Kind.MESH, key.format());
		try {
			UniformBlock uniforms = new UniformBlock(loaded.program().uniforms(), this.values.geometryCatalog());
			return Optional.of(new Program(loaded, pipeline, uniforms, pass.get()));
		} catch (RuntimeException | Error e) {
			pipeline.close();
			throw e;
		}
	}

	@Override
	public void close() {
		this.programs.values().forEach(program -> program.ifPresent(value -> value.pipeline().close()));
		this.programs.clear();
		this.shadows.values().forEach(program -> program.ifPresent(value -> value.pipeline().close()));
		this.shadows.clear();
	}
}
