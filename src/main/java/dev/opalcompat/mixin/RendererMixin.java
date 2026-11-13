package dev.opalcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.opalcompat.dev.AutoTest;
import dev.opalcompat.perf.LeanFrame;
import dev.opalcompat.render.PackRenderer;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts the frames VulkanMod Reforged actually presents, and times what handing one to the device takes. */
@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {

	@Inject(method = "endFrame", at = @At("RETURN"))
	private void opalcompat$countFrame(CallbackInfoReturnable<Boolean> cir) {
		PackRenderer.frameEnded();
		if (cir.getReturnValueZ()) {
			AutoTest.presentedFrames++;
		}
	}

	// How long the two calls that hand a frame over take, for the [lean] line: the render thread spends a sixth of
	// its time in submitFrame, and which of the two it is decides what can be done about it.
	@WrapOperation(method = "submitFrame", at = @At(value = "INVOKE",
			target = "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
	private int opalcompat$timeSubmit(VkQueue queue, VkSubmitInfo submit, long fence, Operation<Integer> original) {
		long started = System.nanoTime();
		int result = original.call(queue, submit, fence);
		LeanFrame.submitNanos += System.nanoTime() - started;
		return result;
	}

	@WrapOperation(method = "submitFrame", at = @At(value = "INVOKE",
			target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"))
	private int opalcompat$timePresent(VkQueue queue, VkPresentInfoKHR present, Operation<Integer> original) {
		long started = System.nanoTime();
		int result = original.call(queue, present);
		LeanFrame.presentNanos += System.nanoTime() - started;
		return result;
	}
}
