package dev.opalcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.opalcompat.perf.LeanFrame;
import net.vulkanmod.compat.render.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * For GUI items what {@link GuiEntityDeferredStateMixin} is for GUI entities: whether a draw left render state to put
 * back is answered from a field on the render thread, and VulkanMod Reforged's thread-local is asked only while the
 * field says there may be something in it.
 */
@Mixin(value = GuiItemRenderState.class, remap = false)
public abstract class GuiItemDeferredStateMixin {

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
