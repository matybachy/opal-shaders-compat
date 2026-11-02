package dev.vitrailvk.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.LegacyGlsl;
import dev.vitrail.glsl.VertexInputs;
import dev.vitrail.pack.model.BlendMode;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.pack.program.ChainFilter;
import dev.vitrail.pack.program.TerrainPass;
import dev.vitrail.pack.source.OpenedPack;
import dev.vitrail.pack.source.ShadowCasters;
import dev.vitrail.pack.source.ShadowCullState;
import dev.vitrail.pack.target.ChainPlan;
import dev.vitrail.pack.target.PackDirectives;
import dev.vitrail.pack.target.SamplerPlan;
import dev.vitrail.pack.target.TargetDirectives;
import dev.vitrail.render.PackValues;
import dev.vitrail.render.ShadowAmortisation;
import dev.vitrail.render.ShadowCullPlan;
import dev.vitrail.uniform.ClipSpace;
import dev.vitrail.uniform.NoiseTexture;
import dev.vitrail.uniform.UniformBlock;
import dev.vitrail.uniform.UniformCatalog;
import dev.vitrailvk.VitrailVk;
import dev.vitrailvk.compile.SpirvCompiler;
import dev.vitrailvk.compile.SpirvModule;
import dev.vitrailvk.mixin.DrawBuffersAccessor;
import dev.vitrailvk.mixin.DrawParametersAccessor;
import dev.vitrailvk.mixin.WorldRendererAccessor;
import dev.vitrailvk.pack.ShaderPacks;
import dev.vitrailvk.terrain.TerrainFormat;
import dev.vitrailvk.vk.HostBuffer;
import dev.vitrailvk.vk.Std140Writer;
import dev.vitrailvk.vk.Vk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.buffer.AreaBuffer;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Draws the selected pack over VulkanMod Reforged, driven by the plans Vitrail's loader makes
 * ({@code loadChain}, {@code loadTerrain}).
 * <p>
 * A frame: the level's matrices are captured when it starts ({@link #beginLevel}). Just before the sky
 * ({@link #beforeSky}) the frame opens: the targets and the depth are cleared, the begin passes run, the
 * map is drawn from the light and the prepare passes run. The chunk layers are drawn by the pack's terrain programs into its own targets and depth
 * ({@link #drawTerrain}, instead of VulkanMod's shader); before the first translucent layer the depth
 * is copied for {@code depthtex1} and the deferred passes run. After the level ({@link #afterLevel}) the
 * remaining passes and {@code final} run, the last into the swapchain image. Each time, VulkanMod's own
 * render pass is suspended and resumed around the pack's work.
 */
public final class PackRenderer implements AutoCloseable {

	private static final int UBO_BYTES = 16 << 20;
	private static final int UBO_ALIGNMENT = 256;
	private static final int SETS_PER_FRAME = 4096;
	private static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;
	private static final Set<String> ATLAS = Set.of("gtexture", "tex", "texture", "ofTexture");
	private static final String LIGHTMAP = "lightmap";
	private static final Matrix4fc IDENTITY = new Matrix4f();

	private static PackRenderer active;
	private static String activeKey;
	private static String failedKey;
	private static boolean failedDuringFrame;
	private static final java.util.Deque<Families.Phase> phases = new java.util.ArrayDeque<>();
	private static final java.util.Deque<DrawIds> drawIds = new java.util.ArrayDeque<>();
	private static long frameSerial;
	private static SpirvCompiler compiler;

	private final String packName;
	private final OpenedPack opened;
	private final PackValues values;
	private final PackProgram.Chain chain;
	private final PackProgram.Terrain terrain;
	private final Map<String, PackPipeline> pipelines = new LinkedHashMap<>();
	private final Map<String, UniformBlock> blocks = new HashMap<>();
	private final Map<TerrainPass, PackPipeline> terrainPipelines = new EnumMap<>(TerrainPass.class);
	private final Map<TerrainPass, UniformBlock> terrainBlocks = new EnumMap<>(TerrainPass.class);
	private final RenderPasses renderPasses = new RenderPasses();
	private final Map<Integer, Long> descriptorPools = new HashMap<>();
	private final Map<Integer, HostBuffer> uniformBuffers = new HashMap<>();
	private final Set<String> warned = new HashSet<>();
	private final HostBuffer quad;
	private final FrameArena meshArena = new FrameArena();
	private MeshPrograms meshPrograms;
	private long meshArenaSerial = -1;
	private final Std140Writer std140 = new Std140Writer();
	private final VulkanImage noise;
	private final VulkanImage flatNormal;
	private final VulkanImage noSpecular;
	private PackTargets targets;
	private VulkanImage depth;
	private VulkanImage depthNoTranslucent;
	private VulkanImage depthNoHand;
	private final ShadowMaps shadow;

	// Per frame
	private boolean frameOpen;
	private boolean shadowDone;
	private boolean deferredDone;
	private int nextPass;
	private long pool;
	private HostBuffer ubo;
	private boolean announced;

	private PackRenderer(Path packPath, String dimension) throws Exception {
		long start = System.nanoTime();
		boolean terrainMesh = TerrainFormat.EXTENDED;
		Machine.install();
		this.opened = OpenedPack.open(packPath, ShaderPacks.options(), ShaderPacks.profile());
		boolean built = false;
		try {
			this.packName = this.opened.source().packName();
			this.values = PackValues.read(this.opened, dimension);
			this.values.convention(ClipSpace.FORWARD);
			ChainPlan.Families families = new ChainPlan.Families(terrainMesh, false, false, false);
			this.chain = PackProgram.loadChain(this.opened, dimension, ChainFilter.ALL, families);
			this.terrain = terrainMesh
					? PackProgram.loadTerrain(this.opened, this.chain.place(), VertexInputs.TERRAIN)
					: null;
			if (compiler == null) compiler = new SpirvCompiler();
			this.meshPrograms = new MeshPrograms(this.opened, this.chain, this.values, compiler);
			for (Map.Entry<String, PackProgram.Loaded> entry : this.chain.programs().entrySet()) {
				this.pipelines.put(entry.getKey(), new PackPipeline(entry.getKey(), entry.getValue().program(), compiler,
						PackPipeline.Kind.QUAD, null));
				this.blocks.put(entry.getKey(), block(entry.getKey(), entry.getValue(), this.values.catalog()));
			}
			if (this.terrain != null) {
				for (Map.Entry<TerrainPass, PackProgram.Loaded> entry : this.terrain.programs().entrySet()) {
					String name = entry.getKey().name().toLowerCase(Locale.ROOT) + ":" + entry.getValue().path();
					this.terrainPipelines.put(entry.getKey(), new PackPipeline(name, entry.getValue().program(), compiler,
							PackPipeline.Kind.TERRAIN, null));
					// A pass drawn from the light reads the light's matrices under gl_ModelViewMatrix.
					this.terrainBlocks.put(entry.getKey(), block(name, entry.getValue(),
							entry.getKey().shadow() ? this.values.shadowGeometryCatalog() : this.values.geometryCatalog()));
				}
			}
			this.quad = new HostBuffer(6 * 20, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 4);
			ByteBuffer vertices = this.quad.view(this.quad.allocate(6 * 20), 6 * 20).order(ByteOrder.LITTLE_ENDIAN);
			float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 0}, {1, 1}, {0, 1}};
			for (float[] corner : corners) {
				vertices.putFloat(corner[0]).putFloat(corner[1]).putFloat(0.0F).putFloat(corner[0]).putFloat(corner[1]);
			}
			NoiseTexture.Image image = this.values.noiseImage();
			int resolution = this.values.noiseResolution();
			this.noise = image != null
					? uploaded(image.width(), image.height(), image.rgba(), false)
					: uploaded(resolution, resolution, NoiseTexture.rgba(resolution), false);
			// What Iris binds when the resource pack ships no normal or specular map: a flat normal, nothing.
			this.flatNormal = uploaded(1, 1, new byte[] {(byte) 127, (byte) 127, (byte) 255, (byte) 255}, true);
			this.noSpecular = uploaded(1, 1, new byte[] {0, 0, 0, 0}, true);
			this.shadow = new ShadowMaps(this.values.shadowResolution(), shadowNamed(), this.values.shadowColours(),
					this.values.shadowDepths());
			logPlan(System.nanoTime() - start);
			built = true;
		} finally {
			if (!built) closePartly();
		}
	}

	private void closePartly() {
		if (this.meshPrograms != null) this.meshPrograms.close();
		this.pipelines.values().forEach(PackPipeline::close);
		this.terrainPipelines.values().forEach(PackPipeline::close);
		closeOpened();
	}

	private void closeOpened() {
		try {
			this.opened.close();
		} catch (java.io.IOException e) {
			VitrailVk.LOGGER.warn("Closing {} failed", this.packName, e);
		}
	}

	private static VulkanImage uploaded(int width, int height, byte[] rgba, boolean clamp) {
		VulkanImage image = VulkanImage.builder(width, height)
				.setFormat(VK_FORMAT_R8G8B8A8_UNORM)
				.setLinearFiltering(true)
				.setClamp(clamp)
				.createVulkanImage();
		ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
		try {
			pixels.put(rgba).flip();
			image.uploadSubTextureAsync(0, width, height, 0, 0, 0, 0, width, pixels);
			image.readOnlyLayout();
		} finally {
			MemoryUtil.memFree(pixels);
		}
		return image;
	}

	/** Every shadow colour the pack samples or a shadow program writes. */
	private Set<Integer> shadowNamed() {
		Set<Integer> named = new HashSet<>(this.chain.targets().shadowAllocated());
		if (this.terrain != null) {
			for (Map.Entry<TerrainPass, PackProgram.Loaded> entry : this.terrain.programs().entrySet()) {
				if (entry.getKey().shadow()) named.addAll(shadowWrites(entry.getValue()));
			}
		}
		return named;
	}

	/** Which shadow colours a shadow program's draw buffers name, in the order of its outputs. */
	private List<Integer> shadowWrites(PackProgram.Loaded loaded) {
		var fragment = loaded.program().stages().get(ProgramStage.FRAGMENT);
		List<Integer> declared = fragment == null ? List.of() : fragment.drawBuffers();
		return dev.vitrail.pack.target.DrawBuffers.shadowColours(declared, this.chain.targets().shadowCeiling());
	}

	private static UniformBlock block(String name, PackProgram.Loaded loaded, UniformCatalog catalog) {
		UniformBlock block = new UniformBlock(loaded.program().uniforms(), catalog);
		if (!block.unanswered().isEmpty()) {
			VitrailVk.LOGGER.info("[pack] {}: uniforms nothing answers yet (read as zero): {}", name, block.unanswered());
		}
		return block;
	}

	// ----------------------------------------------------------------------------------------------
	// Hooks

	public static void pushEntity(Entity entity) {
		drawIds.push(active == null ? DrawIds.NONE : DrawIds.entity(entity));
	}

	public static void pushBlockEntity(BlockEntity entity) {
		drawIds.push(active == null ? DrawIds.NONE : DrawIds.blockEntity(entity.getBlockState()));
	}

	public static void popDrawIds() {
		drawIds.pop();
	}

	public static boolean capturesDrawIds() {
		return active != null && active.frameOpen && !failedDuringFrame;
	}

	public static DrawIds currentDrawIds() {
		return drawIds.isEmpty() ? DrawIds.NONE : drawIds.peek();
	}
	public static void pushPhase(Families.Phase phase) {
		phases.push(phase);
	}

	public static void popPhase() {
		phases.pop();
	}

	static Families.Phase phase() {
		return phases.isEmpty() ? Families.Phase.LEVEL : phases.peek();
	}

	/** {@code LevelRenderer.renderLevel} HEAD, on the render thread. */
	public static void beginLevel(Matrix4fc modelView, Matrix4fc projection) {
		if (failedDuringFrame) return;
		LevelCapture.capture(modelView, projection);
		ensureLoaded();
		if (active != null) {
			active.values.advance();
			active.frameOpen = false;
		}
	}

	/** Just before {@code LevelRenderer.renderSky}: what the frame needs ahead of the world. */
	public static void beforeSky(double camX, double camY, double camZ) {
		if (active == null || failedDuringFrame) return;
		try {
			active.openAndShadow(camX, camY, camZ);
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	/**
	 * VulkanMod's {@code WorldRenderer.renderSectionLayer} HEAD. Returns whether the pack drew the
	 * layer, in which case VulkanMod's own draw is skipped.
	 */
	public static boolean drawTerrain(RenderType renderType, double camX, double camY, double camZ) {
		if (active == null || failedDuringFrame || active.terrain == null) return false;
		try {
			return active.terrainLayer(renderType, camX, camY, camZ);
		} catch (RuntimeException e) {
			fail(e);
			return false;
		}
	}

	/** Tries a phase-scoped immediate draw, leaving ownership with the caller on failure. */
	// TODO-VERIFY: check mixin ordering and the immediate draw path with VulkanMod at runtime.
	public static boolean drawImmediate(MeshData mesh) {
		if (active == null || failedDuringFrame || !Renderer.isRecording()) return false;
		Families.Phase current = phase();
		if (current != Families.Phase.SKY && current != Families.Phase.PARTICLES
				&& current != Families.Phase.WEATHER && current != Families.Phase.CLOUDS) return false;
		var shader = RenderSystem.getShader();
		if (shader == null) return false;
		try {
			return active.meshLayer(mesh.drawState(), mesh.vertexBuffer(), mesh.indexBuffer(), shader.getName(), ((MeshIds) (Object) mesh).vitrailvk$ids(), RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
		} catch (RuntimeException e) {
			fail(e);
			return false;
		}
	}
	/** Draws a captured static vertex buffer while the sky or cloud renderer owns the call. */
	// TODO-VERIFY: check static VBO draw matrices and upload lifetime in a running VulkanMod frame.
	public static boolean drawStaticMesh(MeshData.DrawState state, ByteBuffer vertices, ByteBuffer indices,
			String shader, Matrix4fc view, Matrix4fc projection) {
		if (active == null || failedDuringFrame || !Renderer.isRecording()) return false;
		if (phase() != Families.Phase.SKY && phase() != Families.Phase.CLOUDS) return false;
		try {
			return active.meshLayer(state, vertices, indices, shader, null, view, projection);
		} catch (RuntimeException e) {
			fail(e);
			return false;
		}
	}
	/** After {@code GameRenderer.renderLevel}, before the GUI. */
	public static void afterLevel() {
		if (active == null || failedDuringFrame) return;
		try {
			active.finishFrame();
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	/** Drops the running pack so the next frame reads it again, with its current selection and settings. */
	public static void reload() {
		shutDown();
		failedKey = null;
	}

	private static void fail(RuntimeException e) {
		VitrailVk.LOGGER.error("Shader pack {} failed while drawing; turning it off", active.packName, e);
		failedKey = activeKey;
		// Recorded commands still reference this pack until VulkanMod submits the frame.
		failedDuringFrame = true;
	}

	/** Called after VulkanMod finishes or discards the recorded frame. */
	public static void frameEnded() {
		frameSerial++;
		if (failedDuringFrame) {
			shutDown();
			failedDuringFrame = false;
		}
	}

	private static void ensureLoaded() {
		ClientLevel level = Minecraft.getInstance().level;
		Optional<Path> pack = ShaderPacks.selectedPath();
		if (level == null || pack.isEmpty()) {
			if (active != null) shutDown();
			return;
		}
		String dimension = dimensionDirectory(level);
		String key = pack.get() + "|" + dimension;
		if (key.equals(activeKey) || key.equals(failedKey)) return;
		shutDown();
		try {
			active = new PackRenderer(pack.get(), dimension);
			activeKey = key;
		} catch (Exception e) {
			VitrailVk.LOGGER.error("Shader pack {} could not be loaded for {}; drawing without it",
					pack.get().getFileName(), dimension, e);
			failedKey = key;
		}
	}

	private static void shutDown() {
		if (active != null) {
			vkDeviceWaitIdle(Vk.device());
			active.close();
		}
		active = null;
		activeKey = null;
	}

	@SuppressWarnings("ReferenceEquality")
	private static String dimensionDirectory(ClientLevel level) {
		if (level.dimension() == Level.NETHER) return "world-1";
		if (level.dimension() == Level.END) return "world1";
		return "world0";
	}

	// ----------------------------------------------------------------------------------------------
	// Frame

	private static VulkanImage screen() {
		return Renderer.getInstance().getMainPass().getColorAttachment();
	}

	/** Once per frame, before the pack's first draw: size, descriptors, clears, begin. */
	private void openFrame(VkCommandBuffer cmd, MemoryStack stack) {
		if (this.frameOpen) return;
		this.frameOpen = true;
		this.deferredDone = false;
		this.shadowDone = false;
		this.nextPass = 0;
		VulkanImage screen = screen();
		if (this.targets == null || !this.targets.matches(screen.width, screen.height)) {
			vkDeviceWaitIdle(Vk.device());
			this.renderPasses.forgetFramebuffers();
			freeTargets();
			this.targets = new PackTargets(this.chain.targets(), screen.width, screen.height);
			int usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
					| VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
			this.depth = VulkanImage.createDepthImage(DEPTH_FORMAT, screen.width, screen.height, usage, false, true);
			this.depthNoTranslucent = VulkanImage.createDepthImage(DEPTH_FORMAT, screen.width, screen.height, usage, false, true);
			this.depthNoHand = VulkanImage.createDepthImage(DEPTH_FORMAT, screen.width, screen.height, usage, false, true);
		}
		int frame = Renderer.getCurrentFrame();
		// TODO-VERIFY: confirm frame-slot reuse and multiple level renders against live VulkanMod frames.
		if (this.meshArenaSerial != frameSerial) {
			this.meshArena.begin(frame);
			this.meshArenaSerial = frameSerial;
		}
		this.pool = descriptorPool(frame);
		vkResetDescriptorPool(Vk.device(), this.pool, 0);
		this.ubo = this.uniformBuffers.computeIfAbsent(frame,
				f -> new HostBuffer(UBO_BYTES, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, UBO_ALIGNMENT));
		this.ubo.rewind();

		clearTargets(cmd, stack);
		clearShadowMaps(cmd, stack);
		runPasses(cmd, stack, this.chain.chain().beginEnd());
	}

	private void runPasses(VkCommandBuffer cmd, MemoryStack stack, int until) {
		List<ChainPlan.Pass> passes = this.chain.chain().passes();
		VulkanImage screen = screen();
		for (; this.nextPass < Math.min(until, passes.size()); this.nextPass++) {
			drawFullscreen(cmd, stack, passes.get(this.nextPass), screen);
		}
	}

	/** The opaque world is done: the depth without translucents, and the deferred passes. */
	private void deferred(VkCommandBuffer cmd, MemoryStack stack) {
		if (this.deferredDone) return;
		this.deferredDone = true;
		copyDepth(cmd, stack, this.depth, this.depthNoTranslucent);
		runPasses(cmd, stack, this.chain.chain().deferredEnd());
	}

	private boolean terrainLayer(RenderType renderType, double camX, double camY, double camZ) {
		TerrainRenderType type = TerrainRenderType.get(renderType);
		Set<TerrainRenderType> drawn = Initializer.CONFIG.uniqueOpaqueLayer
				? TerrainRenderType.COMPACT_RENDER_TYPES : TerrainRenderType.SEMI_COMPACT_RENDER_TYPES;
		if (!drawn.contains(type)) return true; // VulkanMod folds this layer into another one.
		TerrainPass pass = switch (type) {
			case SOLID -> TerrainPass.SOLID;
			case TRANSLUCENT -> TerrainPass.TRANSLUCENT;
			default -> TerrainPass.CUTOUT;
		};
		PackPipeline pipeline = this.terrainPipelines.get(pass);
		PackProgram.Loaded loaded = this.terrain.programs().get(pass);
		Optional<ChainPlan.Pass> plan = this.chain.chain().geometry(pass);
		if (pipeline == null || loaded == null || plan.isEmpty()) return false;

		Renderer renderer = Renderer.getInstance();
		VkCommandBuffer cmd = Renderer.getCommandBuffer();
		try (Renderer.RenderTargetScope scope = renderer.temporaryRenderTarget(); MemoryStack stack = MemoryStack.stackPush()) {
			renderer.endRenderPass(cmd);
			openFrame(cmd, stack);
			runPasses(cmd, stack, this.chain.chain().prepareEnd());
			if (pass == TerrainPass.TRANSLUCENT) {
				deferred(cmd, stack);
				WorldRenderer world = WorldRenderer.getInstance();
				((WorldRendererAccessor) world).vitrailvk$sortTranslucentSections(camX, camY, camZ);
			}

			List<VulkanImage> outputs = new ArrayList<>();
			for (ChainPlan.Attachment attachment : plan.get().attachments()) {
				PackTargets.Target target = this.targets.target(attachment.target());
				if (target == null) return false;
				outputs.add(target.image(attachment.side()));
			}

			// The atlas and the light map this layer's render state binds, looked up through
			// VulkanMod's GL emulation (its own slots are only filled when its pipeline draws).
			renderType.setupRenderState();
			VulkanImage atlas = glImage(RenderSystem.getShaderTexture(0));
			VulkanImage lightmap = glImage(RenderSystem.getShaderTexture(2));
			renderType.clearRenderState();

			this.values.modelView(LevelCapture.modelView(), IDENTITY);
			this.values.projection(LevelCapture.projection());
			this.values.passAlphaTest(pass == TerrainPass.CUTOUT ? 0.1F : 0.0F);

			long set = allocateSet(stack, this.pool, pipeline.setLayout());
			writeDescriptors(stack, set, pipeline, loaded, this.terrainBlocks.get(pass), outputs, atlas, lightmap);

			for (VulkanImage output : outputs) {
				output.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
			}
			this.depth.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
			List<Integer> formats = outputs.stream().map(image -> image.format).toList();
			long renderPass = this.renderPasses.renderPass(formats, DEPTH_FORMAT, true);
			long[] views = new long[outputs.size() + 1];
			for (int i = 0; i < outputs.size(); i++) views[i] = attachmentView(outputs.get(i));
			views[outputs.size()] = this.depth.getImageView();
			int width = this.depth.width;
			int height = this.depth.height;
			long framebuffer = this.renderPasses.framebuffer(renderPass, views, width, height);

			String bareName = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
			String askedName = pass == TerrainPass.TRANSLUCENT ? "gbuffers_water" : bareName;
			List<PackPipeline.Blend> blends = blends(askedName, bareName, outputs.size(),
					pass == TerrainPass.TRANSLUCENT ? PackPipeline.Blend.TRANSLUCENT : PackPipeline.Blend.OFF);
			// Translucent culled as well, as vanilla's render type and Iris do: water emits its surface
			// a second time facing down, and drawn unculled that copy covers the upward one.
			boolean cull = Initializer.CONFIG.backFaceCulling;
			PackPipeline.Style style = new PackPipeline.Style(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, true, true,
					VK_COMPARE_OP_LESS_OR_EQUAL, cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE, 0xF, false, blends);

			beginPass(cmd, stack, renderPass, framebuffer, width, height, false);
			vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline(renderPass, outputs.size(), style));
			vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout(), 0, stack.longs(set), null);
			drawSections(cmd, stack, pipeline, type, camX, camY, camZ);
			vkCmdEndRenderPass(cmd);
		}
		return true;
	}

	/** Records a prepared immediate mesh into the pack targets once its program and outputs exist. */
	// TODO-VERIFY: validate mesh pipeline state, target transitions and draw transforms in a live frame.
	private boolean meshLayer(MeshData.DrawState state, ByteBuffer vertices, ByteBuffer indices, String shader,
			DrawIds[] identifiers, Matrix4fc view, Matrix4fc projection) {
		if (!this.frameOpen || this.targets == null || VRenderSystem.stencilTest || VRenderSystem.logicOp) return false;
		var blend = PipelineState.blendInfo;
		if (blend.enabled && (blend.blendOpRgb != VK_BLEND_OP_ADD || blend.blendOpAlpha != VK_BLEND_OP_ADD)) return false;
		Families.Piece piece = Families.resolve(shader, state.format(), phase(), blend.enabled);
		if (piece == null || phase() == Families.Phase.SHADOW) return false;
		Optional<MeshPrograms.Program> candidate = this.meshPrograms.resolve(piece, state.format(), this.deferredDone);
		if (candidate.isEmpty()) return false;
		MeshPrograms.Program program = candidate.get();
		List<VulkanImage> outputs = new ArrayList<>();
		for (ChainPlan.Attachment attachment : program.pass().attachments()) {
			PackTargets.Target target = this.targets.target(attachment.target());
			if (target == null) return false;
			outputs.add(target.image(attachment.side()));
		}
		if (outputs.isEmpty()) return false;
		MeshDraw draw = MeshDraw.copy(this.meshArena, state, vertices, indices);
		if (draw == null) return true;
		FrameArena.Slice constants = MeshDraw.constants(this.meshArena, identifiers, state.vertexCount(), RenderSystem.getShaderLineWidth());
		Renderer renderer = Renderer.getInstance();
		VkCommandBuffer cmd = Renderer.getCommandBuffer();
		try (Renderer.RenderTargetScope scope = renderer.temporaryRenderTarget(); MemoryStack stack = MemoryStack.stackPush()) {
			renderer.endRenderPass(cmd);
			this.values.modelView(view, IDENTITY);
			this.values.projection(projection);
			this.values.passAlphaTest(program.loaded().alphaTest().reference());
			long set = allocateSet(stack, this.pool, program.pipeline().setLayout());
			writeDescriptors(stack, set, program.pipeline(), program.loaded(), program.uniforms(), outputs,
					glImage(RenderSystem.getShaderTexture(0)), glImage(RenderSystem.getShaderTexture(2)));
			for (VulkanImage output : outputs) output.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
			this.depth.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
			List<Integer> formats = outputs.stream().map(image -> image.format).toList();
			long renderPass = this.renderPasses.renderPass(formats, DEPTH_FORMAT, true);
			long[] views = new long[outputs.size() + 1];
			for (int i = 0; i < outputs.size(); i++) views[i] = attachmentView(outputs.get(i));
			views[outputs.size()] = this.depth.getImageView();
			int width = this.depth.width, height = this.depth.height;
			long framebuffer = this.renderPasses.framebuffer(renderPass, views, width, height);
			PackPipeline.Blend fallback = blend.enabled
					? new PackPipeline.Blend(true, blend.srcRgbFactor, blend.dstRgbFactor, blend.srcAlphaFactor, blend.dstAlphaFactor)
					: PackPipeline.Blend.OFF;
			String path = program.loaded().path();
			String served = path.substring(path.lastIndexOf('/') + 1);
			List<PackPipeline.Blend> blends = blends(piece.program(), served, outputs.size(), fallback);
			int depthOp = VRenderSystem.depthFun - 512;
			if (depthOp < VK_COMPARE_OP_NEVER || depthOp > VK_COMPARE_OP_ALWAYS) throw new IllegalStateException("Unknown depth function");
			PackPipeline.Style style = new PackPipeline.Style(draw.topology(), VRenderSystem.depthTest, VRenderSystem.depthMask,
					depthOp, VRenderSystem.cull ? VRenderSystem.cullFace : VK_CULL_MODE_NONE, VRenderSystem.colorMask, false, blends);
			beginPass(cmd, stack, renderPass, framebuffer, width, height, false);
			vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, program.pipeline().pipeline(renderPass, outputs.size(), style));
			vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, program.pipeline().layout(), 0, stack.longs(set), null);
			draw.draw(cmd, stack, constants);
			vkCmdEndRenderPass(cmd);
		}
		return true;
	}
	/** What the pack's {@code blend.<program>} directives ask for, per attachment. */
	private List<PackPipeline.Blend> blends(String asked, String served, int attachments, PackPipeline.Blend fallback) {
		List<PackPipeline.Blend> blends = new ArrayList<>(Math.max(1, attachments));
		for (int rank = 0; rank < Math.max(1, attachments); rank++) {
			int at = rank;
			Optional<BlendMode> mode = this.chain.targets().blend(asked, at)
					.or(() -> this.chain.targets().blend(served, at));
			blends.add(mode.map(PackRenderer::toBlend).orElse(fallback));
		}
		return blends;
	}

	private static PackPipeline.Blend toBlend(BlendMode mode) {
		if (mode.off()) return PackPipeline.Blend.OFF;
		return new PackPipeline.Blend(true, PackPipeline.factor(mode.srcRgb()), PackPipeline.factor(mode.dstRgb()),
				PackPipeline.factor(mode.srcAlpha()), PackPipeline.factor(mode.dstAlpha()));
	}

	private static VulkanImage glImage(int glId) {
		GlTexture texture = GlTexture.getTexture(glId);
		return texture == null ? null : texture.getVulkanImage();
	}

	/** A target with a mip chain renders into its first level; a framebuffer takes a one-level view. */
	private static long attachmentView(VulkanImage image) {
		return image.mipLevels > 1 ? image.getLevelImageView(0) : image.getImageView();
	}

	/** VulkanMod's own draw loop over its chunk areas, with the pack's pipeline bound. */
	private static void drawSections(VkCommandBuffer cmd, MemoryStack stack, PackPipeline pipeline, TerrainRenderType type,
			double camX, double camY, double camZ) {
		boolean translucent = type == TerrainRenderType.TRANSLUCENT;
		if (!translucent) {
			Renderer.getDrawer().bindIndexBuffer(cmd, Renderer.getDrawer().getQuadsIndexBuffer().getIndexBuffer());
		}
		WorldRenderer world = WorldRenderer.getInstance();
		StaticQueue<ChunkArea> areas = ((WorldRendererAccessor) world).vitrailvk$sectionGraph().getChunkAreaQueue().queue();
		int areaCount = areas.size();
		for (int a = 0; a < areaCount; a++) {
			ChunkArea area = areas.get(translucent ? areaCount - 1 - a : a);
			StaticQueue<RenderSection> sections = area.sectionQueue;
			if (sections.size() == 0 || !bindArea(cmd, stack, pipeline, area, type, camX, camY, camZ)) continue;

			int count = sections.size();
			for (int s = 0; s < count; s++) {
				drawSection(cmd, sections.get(translucent ? count - 1 - s : s), type);
			}
		}
	}

	/** Binds what one chunk area's sections are drawn out of; false if it has nothing for this layer. */
	private static boolean bindArea(VkCommandBuffer cmd, MemoryStack stack, PackPipeline pipeline, ChunkArea area,
			TerrainRenderType type, double camX, double camY, double camZ) {
		DrawBuffers buffers = area.getDrawBuffers();
		AreaBuffer vertices = buffers.getAreaBuffer(type);
		if (vertices == null) return false;
		if (type == TerrainRenderType.TRANSLUCENT) {
			AreaBuffer indices = buffers.getIndexBuffer();
			if (indices == null) return false;
			vkCmdBindIndexBuffer(cmd, indices.getId(), 0, VK_INDEX_TYPE_UINT16);
		}
		vkCmdBindVertexBuffers(cmd, 0, stack.longs(vertices.getId()), stack.longs(0));
		Vector3i origin = ((DrawBuffersAccessor) buffers).vitrailvk$origin();
		ByteBuffer offset = stack.malloc(12);
		offset.putFloat(0, (float) (origin.x + DrawBuffers.POS_OFFSET - camX));
		offset.putFloat(4, (float) (origin.y + DrawBuffers.POS_OFFSET - camY));
		offset.putFloat(8, (float) (origin.z + DrawBuffers.POS_OFFSET - camZ));
		vkCmdPushConstants(cmd, pipeline.layout(), VK_SHADER_STAGE_VERTEX_BIT, 0, offset);
		return true;
	}

	private static void drawSection(VkCommandBuffer cmd, RenderSection section, TerrainRenderType type) {
		DrawParametersAccessor draw = (DrawParametersAccessor) section.getDrawParameters(type);
		if (draw.vitrailvk$indexCount() <= 0) return;
		int firstIndex = draw.vitrailvk$firstIndex() == -1 ? 0 : draw.vitrailvk$firstIndex();
		vkCmdDrawIndexed(cmd, draw.vitrailvk$indexCount(), 1, firstIndex, draw.vitrailvk$vertexOffset(),
				draw.vitrailvk$baseInstance());
	}

	// ----------------------------------------------------------------------------------------------
	// Shadow

	/**
	 * The frame opens here, and the map is drawn: solid and cut-out terrain, what the mesh families
	 * cast, the copy without translucents, translucent terrain. The prepare passes read the map of this
	 * frame, so they run behind it. Iris's order ({@code shadows/ShadowRenderer.java}).
	 */
	private void openAndShadow(double camX, double camY, double camZ) {
		Renderer renderer = Renderer.getInstance();
		VkCommandBuffer cmd = Renderer.getCommandBuffer();
		try (Renderer.RenderTargetScope scope = renderer.temporaryRenderTarget(); MemoryStack stack = MemoryStack.stackPush()) {
			renderer.endRenderPass(cmd);
			openFrame(cmd, stack);
			if (this.shadowDone) return;
			this.shadowDone = true;
			Collection<ShadowArea> areas = shadowWanted() ? shadowSections(camX, camY, camZ) : List.of();
			ShadowCasters casters = this.values.shadowCasters();
			if (!areas.isEmpty() && casters.terrain()) {
				shadowLayer(cmd, stack, areas, TerrainPass.SHADOW_SOLID, RenderType.solid(), TerrainRenderType.SOLID, camX, camY, camZ);
				shadowLayer(cmd, stack, areas, TerrainPass.SHADOW_CUTOUT, RenderType.cutoutMipped(), TerrainRenderType.CUTOUT_MIPPED, camX, camY, camZ);
				shadowLayer(cmd, stack, areas, TerrainPass.SHADOW_CUTOUT, RenderType.cutout(), TerrainRenderType.CUTOUT, camX, camY, camZ);
			}
			copyDepth(cmd, stack, this.shadow.depth(), this.shadow.depthNoTranslucent());
			if (!areas.isEmpty() && casters.translucent()) {
				shadowLayer(cmd, stack, areas, TerrainPass.SHADOW_TRANSLUCENT, RenderType.translucent(), TerrainRenderType.TRANSLUCENT, camX, camY, camZ);
			}
			if (!areas.isEmpty()) ShadowAmortisation.drawn();
			runPasses(cmd, stack, this.chain.chain().prepareEnd());
		}
	}

	/** Whether the pack has anything to draw the map with; the map then stays clear, as in Iris. */
	private boolean shadowWanted() {
		if (this.terrain == null) return false;
		for (TerrainPass pass : this.terrainPipelines.keySet()) {
			if (pass.shadow()) return true;
		}
		return false;
	}

	private record ShadowArea(ChunkArea area, List<RenderSection> sections) {
	}

	/**
	 * The compiled sections the light sees, by chunk area. VulkanMod's graph walks from the camera, so
	 * a section it has never reached has no mesh and casts nothing here, however close to the frustum.
	 * Voxelising programs keep what lies outside the frustum: their volume is not limited to the map.
	 */
	private Collection<ShadowArea> shadowSections(double camX, double camY, double camZ) {
		int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
		boolean voxelised = this.terrain.programs().entrySet().stream()
				.anyMatch(entry -> entry.getKey().shadow() && entry.getValue().voxelises());
		ShadowCullPlan plan = this.values.shadowCullPlan(chunks, chunks, new Vector3f(), new Matrix4f(), voxelised);
		float bound = plan.bound();
		if (bound == 0.0F) return List.of();
		boolean frustumCulled = !voxelised && plan.state() != ShadowCullState.DISTANCE;
		FrustumIntersection frustum = new FrustumIntersection(this.values.shadowFrustum(new Matrix4f()), false);
		float boundSquared = bound * bound;
		Map<ChunkArea, ShadowArea> areas = new LinkedHashMap<>();
		for (RenderSection section : WorldRenderer.getInstance().getSectionGrid().sections) {
			if (section == null || !section.isCompiled() || section.isCompletelyEmpty()) continue;
			ChunkArea area = section.getChunkArea();
			if (area == null) continue;
			float x = (float) (section.xOffset - camX);
			float y = (float) (section.yOffset - camY);
			float z = (float) (section.zOffset - camZ);
			if (bound > 0.0F) {
				float dx = Math.max(Math.max(x, -(x + 16.0F)), 0.0F);
				float dz = Math.max(Math.max(z, -(z + 16.0F)), 0.0F);
				if (dx * dx + dz * dz > boundSquared) continue;
			}
			if (frustumCulled && !frustum.testAab(x, y, z, x + 16.0F, y + 16.0F, z + 16.0F)) continue;
			areas.computeIfAbsent(area, a -> new ShadowArea(a, new ArrayList<>())).sections().add(section);
		}
		return areas.values();
	}

	private void shadowLayer(VkCommandBuffer cmd, MemoryStack stack, Collection<ShadowArea> areas, TerrainPass pass,
			RenderType renderType, TerrainRenderType type, double camX, double camY, double camZ) {
		Set<TerrainRenderType> drawn = Initializer.CONFIG.uniqueOpaqueLayer
				? TerrainRenderType.COMPACT_RENDER_TYPES : TerrainRenderType.SEMI_COMPACT_RENDER_TYPES;
		if (!drawn.contains(type)) return;
		PackPipeline pipeline = this.terrainPipelines.get(pass);
		PackProgram.Loaded loaded = this.terrain.programs().get(pass);
		if (pipeline == null || loaded == null) return;

		List<VulkanImage> outputs = new ArrayList<>();
		for (int index : shadowWrites(loaded)) outputs.add(this.shadow.colour(index));

		renderType.setupRenderState();
		VulkanImage atlas = glImage(RenderSystem.getShaderTexture(0));
		VulkanImage lightmap = glImage(RenderSystem.getShaderTexture(2));
		renderType.clearRenderState();

		this.values.passAlphaTest(pass == TerrainPass.SHADOW_CUTOUT ? 0.1F : 0.0F);
		long set = allocateSet(stack, this.pool, pipeline.setLayout());
		writeDescriptors(stack, set, pipeline, loaded, this.terrainBlocks.get(pass), outputs, atlas, lightmap);

		String bareName = loaded.path().substring(loaded.path().lastIndexOf('/') + 1);
		List<PackPipeline.Blend> blends = blends(pass.program(), bareName, outputs.size(), PackPipeline.Blend.OFF);
		PackPipeline.Style style = new PackPipeline.Style(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, true, true,
				VK_COMPARE_OP_LESS_OR_EQUAL, VK_CULL_MODE_NONE, 0xF, false, blends);
		long renderPass = beginShadowPass(cmd, stack, outputs);
		vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline(renderPass, outputs.size(), style));
		vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout(), 0, stack.longs(set), null);
		if (type != TerrainRenderType.TRANSLUCENT) {
			Renderer.getDrawer().bindIndexBuffer(cmd, Renderer.getDrawer().getQuadsIndexBuffer().getIndexBuffer());
		}
		for (ShadowArea area : areas) {
			if (!bindArea(cmd, stack, pipeline, area.area(), type, camX, camY, camZ)) continue;
			for (RenderSection section : area.sections()) drawSection(cmd, section, type);
		}
		vkCmdEndRenderPass(cmd);
	}

	/** Begins a pass over the given shadow colours and {@code shadowtex0}; returns the render pass. */
	private long beginShadowPass(VkCommandBuffer cmd, MemoryStack stack, List<VulkanImage> outputs) {
		for (VulkanImage output : outputs) {
			output.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
		}
		this.shadow.depth().transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
		List<Integer> formats = outputs.stream().map(image -> image.format).toList();
		long renderPass = this.renderPasses.renderPass(formats, this.shadow.depthFormat(), true);
		long[] views = new long[outputs.size() + 1];
		for (int i = 0; i < outputs.size(); i++) views[i] = outputs.get(i).getImageView();
		views[outputs.size()] = this.shadow.depth().getImageView();
		int size = this.shadow.resolution();
		long framebuffer = this.renderPasses.framebuffer(renderPass, views, size, size);
		beginPass(cmd, stack, renderPass, framebuffer, size, size, false);
		return renderPass;
	}

	private void clearShadowMaps(VkCommandBuffer cmd, MemoryStack stack) {
		clearDepth(cmd, stack, this.shadow.depth());
		clearDepth(cmd, stack, this.shadow.depthNoTranslucent());
		VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack)
				.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(VK_REMAINING_MIP_LEVELS).layerCount(VK_REMAINING_ARRAY_LAYERS);
		for (Map.Entry<Integer, VulkanImage> entry : this.shadow.colours().entrySet()) {
			PackDirectives.ShadowColour directive = this.shadow.directive(entry.getKey());
			if (directive != null && !directive.clear()) continue;
			TargetDirectives.Colour colour = directive == null ? new TargetDirectives.Colour(1.0F, 1.0F, 1.0F, 1.0F) : directive.clearColour();
			VkClearColorValue value = VkClearColorValue.calloc(stack);
			if (directive != null && directive.format().used().integer()) {
				value.int32(0, (int) colour.r()).int32(1, (int) colour.g()).int32(2, (int) colour.b()).int32(3, (int) colour.a());
			} else {
				value.float32(0, colour.r()).float32(1, colour.g()).float32(2, colour.b()).float32(3, colour.a());
			}
			VulkanImage image = entry.getValue();
			image.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
			vkCmdClearColorImage(cmd, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, value, range);
		}
	}

	private void finishFrame() {
		Renderer renderer = Renderer.getInstance();
		VkCommandBuffer cmd = Renderer.getCommandBuffer();
		VulkanImage screen = screen();
		if (screen == null) return;
		try (Renderer.RenderTargetScope scope = renderer.temporaryRenderTarget(); MemoryStack stack = MemoryStack.stackPush()) {
			renderer.endRenderPass(cmd);
			openFrame(cmd, stack);
			deferred(cmd, stack);
			copyDepth(cmd, stack, this.depth, this.depthNoHand);
			runPasses(cmd, stack, Integer.MAX_VALUE);
			Optional<ChainPlan.Pass> last = this.chain.chain().last();
			if (last.isPresent()) {
				drawFullscreen(cmd, stack, last.get(), screen);
			} else {
				present(cmd, stack, screen);
			}
			screen.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
		}
		if (!this.announced) {
			this.announced = true;
			VitrailVk.LOGGER.info("[pack] first frame drawn at {}x{}", screen.width, screen.height);
		}
	}

	private void clearTargets(VkCommandBuffer cmd, MemoryStack stack) {
		TargetDirectives directives = this.chain.targets().directives();
		VkImageSubresourceRange.Buffer colourRange = VkImageSubresourceRange.calloc(1, stack)
				.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(VK_REMAINING_MIP_LEVELS).layerCount(VK_REMAINING_ARRAY_LAYERS);
		for (PackTargets.Target target : this.targets.all()) {
			if (!directives.clears(target.index())) continue;
			TargetDirectives.Colour colour = directives.clearColour(target.index());
			VkClearColorValue value = VkClearColorValue.calloc(stack);
			if (target.index() == 0 && !directives.declaresClearColour(0)) {
				var world = this.values.world();
				value.float32(0, world.fogR()).float32(1, world.fogG()).float32(2, world.fogB()).float32(3, 1.0F);
			} else if (target.format().integer()) {
				value.int32(0, (int) colour.r()).int32(1, (int) colour.g()).int32(2, (int) colour.b()).int32(3, (int) colour.a());
			} else {
				value.float32(0, colour.r()).float32(1, colour.g()).float32(2, colour.b()).float32(3, colour.a());
			}
			for (VulkanImage image : target.alt() == null ? List.of(target.main()) : List.of(target.main(), target.alt())) {
				image.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
				vkCmdClearColorImage(cmd, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, value, colourRange);
			}
		}
		clearDepth(cmd, stack, this.depth);
	}

	private static void clearDepth(VkCommandBuffer cmd, MemoryStack stack, VulkanImage image) {
		VkImageSubresourceRange.Buffer depthRange = VkImageSubresourceRange.calloc(1, stack)
				.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
		VkClearDepthStencilValue far = VkClearDepthStencilValue.calloc(stack).depth(1.0F);
		image.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
		vkCmdClearDepthStencilImage(cmd, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, far, depthRange);
	}

	private static void copyDepth(VkCommandBuffer cmd, MemoryStack stack, VulkanImage from, VulkanImage to) {
		from.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
		to.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
		VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
		region.srcSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).layerCount(1);
		region.dstSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).layerCount(1);
		region.extent().set(from.width, from.height, 1);
		vkCmdCopyImage(cmd, from.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, to.getId(),
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
	}

	/** No {@code final}: the plan's last written target goes to the screen. */
	private void present(VkCommandBuffer cmd, MemoryStack stack, VulkanImage screen) {
		ChainPlan.Attachment attachment = this.chain.chain().present().orElse(null);
		PackTargets.Target target = this.targets.target(attachment == null ? 0 : attachment.target());
		if (target == null) return;
		blit(cmd, stack, attachment == null ? target.main() : target.image(attachment.side()), screen);
	}

	/** A copy turned over: the swapchain's row 0 is the top, the pack's targets' the bottom. */
	private static void blit(VkCommandBuffer cmd, MemoryStack stack, VulkanImage from, VulkanImage to) {
		from.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
		to.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
		VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
		region.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
		region.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
		region.srcOffsets(0).set(0, 0, 0);
		region.srcOffsets(1).set(from.width, from.height, 1);
		region.dstOffsets(0).set(0, to.height, 0);
		region.dstOffsets(1).set(to.width, 0, 1);
		vkCmdBlitImage(cmd, from.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, to.getId(),
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK_FILTER_NEAREST);
	}

	/**
	 * Fills the mip chain of each target this pass asked for ({@code colortexNMipmapEnabled}), on the
	 * half it is about to read, as Iris does before the pass.
	 */
	private void generateMips(VkCommandBuffer cmd, MemoryStack stack, ChainPlan.Pass pass, PackProgram.Loaded loaded) {
		Set<Integer> asked = this.chain.targets().directives().mipmapRequests().get(pass.program());
		if (asked == null || asked.isEmpty()) return;
		Set<VulkanImage> done = new HashSet<>();
		for (SamplerPlan.Binding binding : loaded.samplers().bindings()) {
			if (binding.kind() != SamplerPlan.Kind.COLORTEX || !asked.contains(binding.index())) continue;
			PackTargets.Target target = this.targets.target(binding.index());
			if (target == null) continue;
			VulkanImage image = target.image(binding.side());
			if (image.mipLevels > 1 && done.add(image)) mipChain(cmd, stack, image);
		}
	}

	private static void mipChain(VkCommandBuffer cmd, MemoryStack stack, VulkanImage image) {
		image.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
		VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
				.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.image(image.getId());
		barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
		int width = image.width;
		int height = image.height;
		for (int level = 1; level < image.mipLevels; level++) {
			int nextWidth = Math.max(1, width / 2);
			int nextHeight = Math.max(1, height / 2);
			barrier.subresourceRange().baseMipLevel(level);
			barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL).newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
					.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
			vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
			VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
			region.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level - 1).layerCount(1);
			region.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level).layerCount(1);
			region.srcOffsets(1).set(width, height, 1);
			region.dstOffsets(1).set(nextWidth, nextHeight, 1);
			vkCmdBlitImage(cmd, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image.getId(),
					VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK_FILTER_LINEAR);
			barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL).newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
					.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
			vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
			width = nextWidth;
			height = nextHeight;
		}
	}

	private void drawFullscreen(VkCommandBuffer cmd, MemoryStack stack, ChainPlan.Pass pass, VulkanImage screen) {
		PackPipeline pipeline = this.pipelines.get(pass.program());
		PackProgram.Loaded loaded = this.chain.programs().get(pass.program());
		if (pipeline == null || loaded == null) return;
		boolean toScreen = pass.attachments().isEmpty();

		generateMips(cmd, stack, pass, loaded);

		List<VulkanImage> outputs = new ArrayList<>();
		if (toScreen) {
			outputs.add(screen);
		} else {
			for (ChainPlan.Attachment attachment : pass.attachments()) {
				PackTargets.Target target = this.targets.target(attachment.target());
				if (target == null) return;
				outputs.add(target.image(attachment.side()));
			}
		}

		long set = allocateSet(stack, this.pool, pipeline.setLayout());
		writeDescriptors(stack, set, pipeline, loaded, this.blocks.get(pass.program()), outputs, null, null);

		for (VulkanImage output : outputs) {
			output.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
		}
		List<Integer> formats = outputs.stream().map(image -> image.format).toList();
		long renderPass = this.renderPasses.renderPass(formats, !toScreen);
		long[] views = outputs.stream().mapToLong(PackRenderer::attachmentView).toArray();
		int width = outputs.get(0).width;
		int height = outputs.get(0).height;
		long framebuffer = this.renderPasses.framebuffer(renderPass, views, width, height);

		List<PackPipeline.Blend> blends = blends(pass.program(), pass.program(), outputs.size(), PackPipeline.Blend.OFF);
		PackPipeline.Style style = new PackPipeline.Style(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, false, false,
				VK_COMPARE_OP_ALWAYS, VK_CULL_MODE_NONE, 0xF, false, blends);
		beginPass(cmd, stack, renderPass, framebuffer, width, height, toScreen);
		vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline(renderPass, outputs.size(), style));
		vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout(), 0, stack.longs(set), null);
		vkCmdBindVertexBuffers(cmd, 0, stack.longs(this.quad.handle()), stack.longs(0));
		vkCmdDraw(cmd, 6, 1, 0, 0);
		vkCmdEndRenderPass(cmd);
	}

	/** The swapchain is shown row 0 first; the pack's targets keep row 0 at the bottom. */
	private static void beginPass(VkCommandBuffer cmd, MemoryStack stack, long renderPass, long framebuffer,
			int width, int height, boolean toScreen) {
		VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
				.renderPass(renderPass).framebuffer(framebuffer);
		begin.renderArea().extent().set(width, height);
		vkCmdBeginRenderPass(cmd, begin, VK_SUBPASS_CONTENTS_INLINE);
		VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
				.x(0).y(toScreen ? height : 0).width(width).height(toScreen ? -height : height)
				.minDepth(0.0F).maxDepth(1.0F);
		vkCmdSetViewport(cmd, 0, viewport);
		VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
		scissor.extent().set(width, height);
		vkCmdSetScissor(cmd, 0, scissor);
	}

	private void writeDescriptors(MemoryStack stack, long set, PackPipeline pipeline, PackProgram.Loaded loaded,
			UniformBlock block, List<VulkanImage> outputs, VulkanImage atlas, VulkanImage lightmap) {
		VkCommandBuffer cmd = Renderer.getCommandBuffer();
		VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(pipeline.slots.size(), stack);
		int count = 0;
		for (PackPipeline.Slot slot : pipeline.slots) {
			VkWriteDescriptorSet write = writes.get(count).sType$Default().dstSet(set).dstBinding(slot.binding())
					.descriptorType(slot.type()).descriptorCount(slot.count());
			switch (slot.type()) {
				case SpirvModule.UNIFORM_BUFFER -> {
					UniformBlock written = "OfGlobals".equals(slot.name()) ? block : null;
					boolean transforms = LegacyGlsl.GAME_TRANSFORMS.equals(slot.name());
					int size = written != null ? Math.max(16, written.size()) : transforms ? 160 : 256;
					long offset = this.ubo.allocate(size);
					ByteBuffer view = this.ubo.view(offset, size);
					if (written != null) {
						written.write(this.std140.into(view), this.values.world());
					} else if (transforms) {
						// TODO-VERIFY: confirm the per-draw matrices and color against VulkanMod's live render state.
						float[] color = RenderSystem.getShaderColor();
						this.std140.into(view).putMat4(RenderSystem.getModelViewMatrix())
								.putVec4(color[0], color[1], color[2], color[3]).putVec3(0, 0, 0)
								.putMat4(RenderSystem.getTextureMatrix());
					} else {
						for (int i = 0; i < size; i++) view.put(i, (byte) 0);
					}
					write.pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(this.ubo.handle()).offset(offset).range(size));
				}
				case SpirvModule.COMBINED_IMAGE_SAMPLER -> {
					VulkanImage image = sampled(slot.name(), loaded, outputs, atlas, lightmap);
					// A lookup compiled to a depth-reference sample needs a depth map and a comparing sampler.
					if (slot.depth() && image != this.shadow.depth() && image != this.shadow.depthNoTranslucent()) {
						image = this.shadow.depth();
					}
					image.transitionImageLayout(stack, cmd, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
					long sampler = slot.depth() ? this.shadow.compare() : image.getSampler();
					VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(slot.count(), stack);
					for (int i = 0; i < slot.count(); i++) {
						info.get(i).imageView(image.getImageView()).sampler(sampler)
								.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
					}
					write.pImageInfo(info);
				}
				default -> {
					if (this.warned.add(pipeline.name + ":" + slot.name())) {
						VitrailVk.LOGGER.warn("[pack] {}: descriptor {} of type {} is not served yet", pipeline.name,
								slot.name(), slot.type());
					}
					continue;
				}
			}
			count++;
		}
		writes.limit(count);
		vkUpdateDescriptorSets(Vk.device(), writes, null);
	}

	private VulkanImage sampled(String sampler, PackProgram.Loaded loaded, List<VulkanImage> outputs,
			VulkanImage atlas, VulkanImage lightmap) {
		if (atlas != null && ATLAS.contains(sampler)) return atlas;
		if (lightmap != null && LIGHTMAP.equals(sampler)) return lightmap;
		if ("normals".equals(sampler)) return this.flatNormal;
		if ("specular".equals(sampler)) return this.noSpecular;
		SamplerPlan.Binding binding = loaded.samplers().binding(sampler);
		if (binding != null) {
			switch (binding.kind()) {
				case COLORTEX -> {
					PackTargets.Target target = this.targets.target(binding.index());
					if (target != null) {
						VulkanImage image = target.image(binding.side());
						if (!outputs.contains(image)) return image;
						if (this.warned.add("feedback:" + loaded.path() + ":" + sampler)) {
							VitrailVk.LOGGER.warn("[pack] {} reads {} from the half it writes", loaded.path(), sampler);
						}
					}
				}
				case DEPTH, CENTER_DEPTH -> {
					return switch (sampler) {
						case "depthtex1" -> this.depthNoTranslucent;
						case "depthtex2" -> this.depthNoHand;
						default -> this.depth;
					};
				}
				case NOISE -> {
					return this.noise;
				}
				case SHADOW_DEPTH -> {
					return loaded.samplers().withoutTranslucents(sampler) ? this.shadow.depthNoTranslucent() : this.shadow.depth();
				}
				case SHADOW_COLOUR -> {
					VulkanImage image = this.shadow.colour(binding.index());
					if (!outputs.contains(image)) return image;
				}
				default -> { }
			}
		}
		return VTextureSelector.getWhiteTexture();
	}

	private long descriptorPool(int frame) {
		return this.descriptorPools.computeIfAbsent(frame, f -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(8, stack);
				for (int type = 0; type < 8; type++) {
					sizes.get(type).type(type).descriptorCount(type == SpirvModule.COMBINED_IMAGE_SAMPLER ? 32768 : 8192);
				}
				VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
						.maxSets(SETS_PER_FRAME).pPoolSizes(sizes);
				LongBuffer handle = stack.mallocLong(1);
				Vk.check(vkCreateDescriptorPool(Vk.device(), info, null, handle), "vkCreateDescriptorPool");
				return handle.get(0);
			}
		});
	}

	private static long allocateSet(MemoryStack stack, long pool, long layout) {
		VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
				.descriptorPool(pool).pSetLayouts(stack.longs(layout));
		LongBuffer handle = stack.mallocLong(1);
		Vk.check(vkAllocateDescriptorSets(Vk.device(), info, handle), "vkAllocateDescriptorSets");
		return handle.get(0);
	}

	private void logPlan(long nanos) {
		VitrailVk.LOGGER.info("[pack] {} loaded in {} ms: {} chain programs, terrain {}, targets {} (doubled {})",
				this.packName, nanos / 1_000_000L, this.pipelines.size(),
				this.terrain == null ? "drawn by VulkanMod (mesh not extended)" : this.terrain.programs().keySet(),
				this.chain.targets().allocated(), this.chain.targets().schedule().doubled());
		if (this.terrain != null) {
			this.terrain.programs().forEach((pass, loaded) -> VitrailVk.LOGGER.info("[pack]   terrain {} <- {} -> {}",
					pass, loaded.path(), this.chain.chain().geometry(pass).map(ChainPlan.Pass::attachments).orElse(List.of())));
		}
		this.chain.chain().passes().forEach(this::logPass);
		this.chain.chain().last().ifPresent(this::logPass);
		VitrailVk.LOGGER.info("[pack]   stages: begin<{} prepare<{} deferred<{} of {}", this.chain.chain().beginEnd(),
				this.chain.chain().prepareEnd(), this.chain.chain().deferredEnd(), this.chain.chain().passes().size());
		this.chain.removed().forEach((program, refusal) -> VitrailVk.LOGGER.warn("[pack]   {} removed: {}",
				program, refusal.reason()));
		this.chain.chain().notes().forEach(note -> VitrailVk.LOGGER.info("[pack]   note: {}", note));
	}

	private void logPass(ChainPlan.Pass pass) {
		PackProgram.Loaded loaded = this.chain.programs().get(pass.program());
		String samplers = loaded == null ? "" : loaded.samplers().bindings().stream()
				.map(b -> b.sampler() + "=" + b.kind() + (b.index() >= 0 ? b.index() + "/" + b.side() : ""))
				.toList().toString();
		VitrailVk.LOGGER.info("[pack]   {} -> {} reads {}", pass.program(),
				pass.attachments().isEmpty() ? "screen" : pass.attachments(), samplers);
	}

	private void freeTargets() {
		if (this.targets != null) this.targets.close();
		this.targets = null;
		for (VulkanImage image : new VulkanImage[] {this.depth, this.depthNoTranslucent, this.depthNoHand}) {
			if (image != null) image.free();
		}
		this.depth = null;
		this.depthNoTranslucent = null;
		this.depthNoHand = null;
	}

	@Override
	public void close() {
		this.pipelines.values().forEach(PackPipeline::close);
		this.pipelines.clear();
		this.terrainPipelines.values().forEach(PackPipeline::close);
		this.terrainPipelines.clear();
		this.renderPasses.close();
		this.descriptorPools.values().forEach(pool -> vkDestroyDescriptorPool(Vk.device(), pool, null));
		this.descriptorPools.clear();
		this.uniformBuffers.values().forEach(HostBuffer::close);
		this.meshArena.close();
		if (this.meshPrograms != null) this.meshPrograms.close();
		this.uniformBuffers.clear();
		this.quad.close();
		this.noise.free();
		this.flatNormal.free();
		this.noSpecular.free();
		this.shadow.close();
		freeTargets();
		closeOpened();
	}
}
