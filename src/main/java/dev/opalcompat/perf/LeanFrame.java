package dev.opalcompat.perf;

import dev.opalcompat.OpalCompat;
import dev.opalcompat.pack.ShaderPacks;

import java.util.Locale;

/**
 * Work the render thread did every frame for nothing, taken out, and the count of what that came to.
 * <p>
 * Each of these leaves what is drawn exactly as it was: a lookup is answered from a field where it was answered from a
 * thread-local, the rows of an image an upload reads are copied where the whole image was, a descriptor set written
 * with the same contents is bound again where a new one was written, mesh draws into the same targets are recorded in
 * one render pass where each had its own. They are measured, not run, here: the
 * {@code [lean]} line says every ten seconds how much of each there was, next to the {@code [pack]} line.
 * <p>
 * Off with {@code leanFrame=false} in {@code config/opalcompat.properties}, all of them at once.
 */
public final class LeanFrame {

	public static final boolean ENABLED = ShaderPacks.flag("leanFrame", true);

	/** Render thread: lookups of VulkanMod Reforged's deferred GUI state answered without its thread-locals. */
	public static long guiLookups;
	/** Render thread: the pack's mesh draws, and the render passes they were recorded in. */
	public static long meshDraws;
	public static long meshPasses;
	/** Render thread: mesh draws that bound a descriptor set an earlier draw of the frame had written. */
	public static long setsReused;
	public static long setsWritten;
	/** Bytes of images handed to an upload, and those of them the upload did not read and so did not copy. */
	public static long uploadBytes;
	public static long uploadBytesLeftOut;
	public static long submitNanos;
	public static long presentNanos;

	private LeanFrame() {
	}

	/** With the {@code [pack]} line, on the render thread. */
	public static void report(int frames) {
		if (frames > 0) {
			OpalCompat.LOGGER.info("[lean] {}a frame: {} GUI state lookups answered from a field, {} mesh draws in {} passes, {} of their descriptor sets reused and {} written, submit {} ms, present {} ms; texture uploads: {} KB copied, {} KB left out",
					ENABLED ? "" : "off; ", guiLookups / frames, meshDraws / frames, meshPasses / frames,
					setsReused / frames, setsWritten / frames,
					String.format(Locale.ROOT, "%.2f", submitNanos / 1.0e6 / frames),
					String.format(Locale.ROOT, "%.2f", presentNanos / 1.0e6 / frames),
					(uploadBytes - uploadBytesLeftOut) / 1024, uploadBytesLeftOut / 1024);
		}
		reset();
	}

	public static void reset() {
		guiLookups = 0;
		meshDraws = 0;
		meshPasses = 0;
		setsReused = 0;
		setsWritten = 0;
		uploadBytes = 0;
		uploadBytesLeftOut = 0;
		submitNanos = 0;
		presentNanos = 0;
	}
}
