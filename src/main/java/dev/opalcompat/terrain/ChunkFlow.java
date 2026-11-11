package dev.opalcompat.terrain;

import dev.opalcompat.OpalCompat;
import dev.opalcompat.pack.ShaderPacks;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * How built chunk sections get from VulkanMod Reforged's builder threads onto the device, and in which order.
 * <p>
 * VulkanMod Reforged keeps the meshes its builders finish in one queue of 64 and uploads at most "Chunk Upload Ceiling" of
 * them a frame ({@code TaskDispatcher.getMaxUploadsPerFrame}). A builder that finishes while the queue is full waits for room
 * ({@code scheduleSectionUpdate}), so with the ceiling at 1 every builder ends up waiting and the world appears a section a
 * frame. In the log of 2026-11-10 20:35 the queue stood at 64 after 160 of the 1121 slow frames, one section was uploaded a
 * frame, and a build took 164 ms on average and 95 s at most, the waiting counted in. A mesh that waits is also cancelled by
 * the next change to its section, so near a machine that keeps changing blocks a section can stay as it was for as long as
 * the changes go on. That is how blocks show up late, or stay invisible.
 * <p>
 * Two changes, both inside the frame budgets VulkanMod Reforged keeps on its own (2 ms and 6 MB of uploads a frame, less
 * under pressure, {@code AdaptiveChunkUploadBudget}):
 * <ul>
 *     <li>While meshes are waiting, the ceiling rises to as many as are waiting, up to 64. The time and byte budgets still end
 *     the frame's uploads; the ceiling no longer does at one.</li>
 *     <li>A section VulkanMod Reforged itself calls urgent ({@code SectionGraph.isUrgentRebuild}: changed by the player, or
 *     within 27 blocks of the camera) is built before the others, and its mesh is uploaded before the others without waiting
 *     for room in the queue.</li>
 * </ul>
 * Off with {@code chunkFlow=false} in {@code config/opalcompat.properties}.
 */
public final class ChunkFlow {

	public static final boolean ENABLED = ShaderPacks.flag("chunkFlow", true);

	private static final int MOST_UPLOADS = 64;
	private static final int QUEUE = 64;
	private static final long LOG_EVERY_NANOS = TimeUnit.SECONDS.toNanos(10);

	/** Render thread: the build task being made now is for an urgent section. */
	private static boolean nextUrgent;

	private static final LongAdder URGENT = new LongAdder();
	private static final LongAdder URGENT_NANOS = new LongAdder();
	private static final AtomicLong URGENT_MOST = new AtomicLong();
	private static final LongAdder WAITED_NANOS = new LongAdder();

	private static long uploaded;
	private static long bytes;
	private static long frames;
	private static long fullFrames;
	private static long loggedAt = System.nanoTime();

	private ChunkFlow() {
	}

	/** How many sections may be uploaded this frame, from VulkanMod Reforged's own number and how many are waiting. */
	public static int uploads(int budget, int waiting) {
		if (!ENABLED || waiting <= budget) return budget;
		return Math.max(budget, Math.min(waiting, MOST_UPLOADS));
	}

	public static void markNext(boolean urgent) {
		nextUrgent = urgent;
	}

	public static boolean takeNext() {
		return nextUrgent;
	}

	public static boolean urgent(Object task) {
		return ENABLED && task instanceof UrgentTask urgent && urgent.opalcompat$urgent();
	}

	/** A builder thread: an urgent section's mesh is ready for upload. */
	public static void handedOver(UrgentTask task) {
		long nanos = System.nanoTime() - task.opalcompat$created();
		URGENT.increment();
		URGENT_NANOS.add(nanos);
		URGENT_MOST.accumulateAndGet(nanos, Math::max);
	}

	/** A builder thread spent this long handing a mesh to VulkanMod Reforged's queue. */
	public static void waited(long nanos) {
		if (nanos > 0L) WAITED_NANOS.add(nanos);
	}

	/** Render thread, once a frame, from VulkanMod Reforged's own count of what it uploaded. */
	public static void uploaded(int sections, long staged, int waitingAfter) {
		frames++;
		uploaded += sections;
		bytes += staged;
		if (waitingAfter >= QUEUE) fullFrames++;
		long now = System.nanoTime();
		if (now - loggedAt < LOG_EVERY_NANOS) return;
		long urgent = URGENT.sumThenReset();
		long urgentNanos = URGENT_NANOS.sumThenReset();
		long urgentMost = URGENT_MOST.getAndSet(0L);
		long waited = WAITED_NANOS.sumThenReset();
		if (uploaded > 0 || urgent > 0 || waited > 0) {
			OpalCompat.LOGGER.info("[chunks] {} s: {} sections ({} MB) uploaded in {} frames, the queue of 64 full after {} of them; {} near the player or changed by them, ready {} ms on average and {} ms at most after they were scheduled; builders spent {} s in all handing meshes to the queue",
					TimeUnit.NANOSECONDS.toSeconds(now - loggedAt), uploaded, bytes >> 20, frames, fullFrames, urgent,
					urgent == 0 ? "-" : String.format(Locale.ROOT, "%.0f", urgentNanos / 1.0e6 / urgent),
					TimeUnit.NANOSECONDS.toMillis(urgentMost), String.format(Locale.ROOT, "%.1f", waited / 1.0e9));
		}
		uploaded = bytes = frames = fullFrames = 0;
		loggedAt = now;
	}
}
