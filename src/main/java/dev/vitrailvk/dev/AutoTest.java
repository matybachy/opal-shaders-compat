package dev.vitrailvk.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vitrailvk.VitrailVk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.device.DeviceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unattended test run for development: opens a fixed world, waits until the terrain has been built,
 * writes a screenshot and a result file, and quits the game.
 * <p>
 * Enabled with {@code -Dvitrailvk.autotest=true} (the {@code runAutotest} Gradle task). Properties:
 * {@code vitrailvk.autotest.frames} (frames to render once the world is set up),
 * {@code vitrailvk.autotest.out} (output directory), {@code vitrailvk.autotest.camera}
 * ({@code x,z,yaw,pitch,heightAboveGround}), {@code vitrailvk.autotest.time} (world time) and
 * {@code vitrailvk.autotest.timeout} (seconds).
 */
public final class AutoTest {

	public static final boolean ENABLED = Boolean.getBoolean("vitrailvk.autotest");
	public static final AutoTest INSTANCE = new AutoTest();

	/** Frames VulkanMod Reforged has presented, counted by {@code RendererMixin}. */
	public static volatile long presentedFrames;

	private static final String WORLD = "vitrailvk-autotest";
	private static final long SEED = 20261027L;
	/** Ticks the chunk builder must stay idle before the picture counts as settled. */
	private static final int IDLE_TICKS = 40;

	private enum State { START, LOADING, SETTLING, CAPTURE, DONE }

	private final int frames = Integer.getInteger("vitrailvk.autotest.frames", 120);
	private final int timeoutSeconds = Integer.getInteger("vitrailvk.autotest.timeout", 300);
	private final Path out = Path.of(System.getProperty("vitrailvk.autotest.out", "autotest"));
	private final double[] camera = parseCamera(System.getProperty("vitrailvk.autotest.camera", "0,0,135,20,14"));
	private final int time = Integer.getInteger("vitrailvk.autotest.time", 6000);
	private final long startNanos = System.nanoTime();
	private final List<String> notes = new ArrayList<>();

	private State state = State.START;
	private long settleStartFrame;
	private int idleTicks;
	private int settleTicks;

	private AutoTest() {
		if (!ENABLED) return;
		try {
			Files.createDirectories(out);
			Files.deleteIfExists(out.resolve("screenshot.png"));
			Files.deleteIfExists(out.resolve("result.txt"));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot prepare " + out, e);
		}
		Thread watchdog = new Thread(this::watchdog, "VitrailVK autotest watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	@SubscribeEvent
	public void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		switch (state) {
			case START -> {
				boolean menu = mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen;
				if (mc.getOverlay() == null && menu && mc.level == null) {
					configureOptions(mc);
					openWorld(mc);
					state = State.LOADING;
				}
			}
			case LOADING -> {
				IntegratedServer server = mc.getSingleplayerServer();
				if (mc.level != null && mc.player != null && server != null) {
					server.execute(() -> setUpWorld(server));
					settleStartFrame = presentedFrames;
					state = State.SETTLING;
					VitrailVk.LOGGER.info("[autotest] world joined, settling");
				}
			}
			case SETTLING -> {
				settleTicks++;
				boolean busy = WorldRenderer.getInstance().hasPendingSectionWork();
				idleTicks = busy ? 0 : idleTicks + 1;
				if (mc.screen != null) {
					mc.setScreen(null);
				}
				long rendered = presentedFrames - settleStartFrame;
				boolean settled = rendered >= frames && idleTicks >= IDLE_TICKS;
				// Give up waiting for an idle builder after a while, but still take the picture.
				if (settled || settleTicks > 20 * 60) {
					if (!settled) notes.add("chunk builder never went idle");
					state = State.CAPTURE;
				}
			}
			case DONE -> mc.stop();
			default -> { }
		}
	}

	@SubscribeEvent
	public void onFrame(RenderFrameEvent.Post event) {
		if (state != State.CAPTURE) return;
		Minecraft mc = Minecraft.getInstance();
		String status = "OK";
		try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
			image.writeToFile(out.resolve("screenshot.png"));
		} catch (Exception e) {
			VitrailVk.LOGGER.error("[autotest] screenshot failed", e);
			notes.add("screenshot failed: " + e);
			status = "SCREENSHOT_FAILED";
		}
		writeResult(status);
		state = State.DONE;
	}

	private void configureOptions(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		mc.options.onboardAccessibility = false;
		mc.options.hideGui = true;
		mc.options.renderDistance().set(8);
	}

	private void openWorld(Minecraft mc) {
		if (mc.getLevelSource().levelExists(WORLD)) {
			VitrailVk.LOGGER.info("[autotest] opening world {}", WORLD);
			mc.createWorldOpenFlows().openWorld(WORLD, () -> fail("could not open " + WORLD));
			return;
		}
		VitrailVk.LOGGER.info("[autotest] creating world {} with seed {}", WORLD, SEED);
		GameRules rules = new GameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
		LevelSettings settings = new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL,
				true, rules, WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false),
				WorldPresets::createNormalWorldDimensions, mc.screen);
	}

	/** Runs on the server thread: fixed time, weather and camera. */
	private void setUpWorld(IntegratedServer server) {
		ServerLevel level = server.overworld();
		int x = (int) camera[0];
		int z = (int) camera[1];
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + (int) camera[4];
		var source = server.createCommandSourceStack();
		for (String command : List.of(
				"gamemode spectator @a",
				"time set " + time,
				"weather clear",
				"gamerule doDaylightCycle false",
				"gamerule doWeatherCycle false",
				String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f %.1f", x, y, z, camera[2], camera[3]))) {
			server.getCommands().performPrefixedCommand(source, command);
		}
	}

	private void writeResult(String status) {
		double seconds = (System.nanoTime() - startNanos) / 1e9;
		StringBuilder text = new StringBuilder();
		text.append("status=").append(status).append('\n');
		text.append("frames=").append(presentedFrames).append('\n');
		text.append("settle_frames=").append(presentedFrames - settleStartFrame).append('\n');
		text.append(String.format(Locale.ROOT, "elapsed_s=%.1f%n", seconds));
		text.append("device=").append(DeviceManager.device != null ? DeviceManager.device.deviceName : "?").append('\n');
		text.append("pack=").append(System.getProperty("vitrailvk.pack", "")).append('\n');
		for (String note : notes) text.append("note=").append(note).append('\n');
		try {
			Files.writeString(out.resolve("result.txt"), text);
		} catch (IOException e) {
			VitrailVk.LOGGER.error("[autotest] cannot write result", e);
		}
		VitrailVk.LOGGER.info("[autotest] {}", text.toString().replace('\n', ' '));
	}

	private void fail(String reason) {
		notes.add(reason);
		writeResult("FAILED");
		state = State.DONE;
	}

	private void watchdog() {
		try {
			Thread.sleep(timeoutSeconds * 1000L);
		} catch (InterruptedException e) {
			return;
		}
		if (state == State.DONE) return;
		notes.add("timed out in state " + state);
		writeResult("TIMEOUT");
		Runtime.getRuntime().halt(3);
	}

	private static double[] parseCamera(String spec) {
		String[] parts = spec.split(",");
		double[] values = new double[5];
		for (int i = 0; i < values.length; i++) values[i] = Double.parseDouble(parts[i].trim());
		return values;
	}
}
