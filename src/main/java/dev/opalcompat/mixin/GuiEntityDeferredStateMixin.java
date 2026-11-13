package dev.opalcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.opalcompat.perf.LeanFrame;
import net.vulkanmod.compat.render.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * VulkanMod Reforged asks, at both ends of every batch a buffer source ends, whether a GUI entity draw left render
 * state to put back, and the answer is kept in a thread-local: asking creates the thread's entry and putting back
 * removes it, for every render type of every flush of the GUI. On the render thread the answer is kept in a field
 * beside it. The field is set before the snapshot is and cleared only when the snapshot is taken out, so it is never
 * false while there is one; while it is true the thread-local is asked as before.
 */
@Mixin(value = GuiEntityRenderState.class, remap = false)
public abstract class GuiEntityDeferredStateMixin {

	@Unique
	private static boolean opalcompat$pending;

	@WrapMethod(method = "prepareDeferredDraw")
	private static void opalcompat$markPending(Operation<Void> original) {
		if (RenderSystem.isOnRenderThread()) opalcompat$pending = true;
		original.call();
	}

	@WrapMethod(method = "hasDeferredDrawState")
	private static boolean opalcompat$nonePending(Operation<Boolean> original) {
		if (LeanFrame.ENABLED && !opalcompat$pending && RenderSystem.isOnRenderThread()) {
			LeanFrame.guiLookups++;
			return false;
		}
		return original.call();
	}

	@WrapMethod(method = "restoreDeferredDrawState")
	private static void opalcompat$nothingToRestore(Operation<Void> original) {
		if (LeanFrame.ENABLED && RenderSystem.isOnRenderThread()) {
			if (!opalcompat$pending) {
				LeanFrame.guiLookups++;
				return;
			}
			opalcompat$pending = false;
		}
		original.call();
	}
}
