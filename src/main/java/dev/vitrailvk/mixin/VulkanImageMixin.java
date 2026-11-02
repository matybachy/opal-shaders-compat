package dev.vitrailvk.mixin;

import dev.vitrailvk.vk.ImageLayouts;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = VulkanImage.class, remap = false)
public abstract class VulkanImageMixin {

	// Target verified in refs/VulkanMod-Reforged VulkanImage; VMR only accounts for fragment reads.
	// TODO-VERIFY: verify mixin application and shared layout tracking with VMR during rendering.
	@Inject(method = "transitionImageLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/texture/VulkanImage;I)V",
			at = @At("HEAD"), cancellable = true)
	private static void vitrailvk$storageLayout(MemoryStack stack, VkCommandBuffer cmd, VulkanImage image,
			int layout, CallbackInfo ci) {
		if (layout == VK_IMAGE_LAYOUT_GENERAL || image.getCurrentLayout() == VK_IMAGE_LAYOUT_GENERAL
				|| layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL || image.getCurrentLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
			ImageLayouts.transition(stack, cmd, image, layout);
			ci.cancel();
		}
	}
}
