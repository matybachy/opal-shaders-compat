package dev.opalcompat.world;

import dev.opalcompat.OpalCompat;
import dev.opalcompat.pack.ShaderPacks;

import java.io.IOException;

/**
 * The height clouds are drawn at, {@code cloudHeight} in {@code config/opalcompat.properties}; 0 leaves the dimension's
 * own (192 in the overworld). Set on the Shader Packs page of VulkanMod Reforged's video settings.
 * <p>
 * It goes in where the game asks the dimension for it ({@code DimensionSpecialEffects.getCloudHeight}), which is the one
 * place both clouds read it: the game's own, drawn by {@code LevelRenderer.renderClouds}, and a shader pack's, which this
 * engine hands the same number as the {@code cloudHeight} uniform. Complementary puts its cloud layers at its altitude
 * setting moved by {@code cloudHeight - 192} ({@code lib/common.glsl}), so they move with it. A dimension without clouds
 * (the Nether, the End) answers NaN, and keeps it.
 */
public final class CloudHeight {

	public static final String KEY = "cloudHeight";
	/** The highest the setting goes. */
	public static final int MAX = 512;

	private static volatile int height = Math.max(0, Math.min(MAX, ShaderPacks.number(KEY, 0)));

	private CloudHeight() {
	}

	/** The set height, or 0 for the dimension's own. */
	public static int height() {
		return height;
	}

	/** {@code DimensionSpecialEffects.getCloudHeight}: the set height in a dimension that has clouds. */
	public static float apply(float dimension) {
		int set = height;
		return set > 0 && !Float.isNaN(dimension) ? set : dimension;
	}

	public static void set(int value) {
		int clamped = Math.max(0, Math.min(MAX, value));
		height = clamped;
		try {
			ShaderPacks.setNumber(KEY, clamped);
		} catch (IOException e) {
			OpalCompat.LOGGER.warn("Cannot save the cloud height", e);
		}
	}
}
