package dev.opalcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.opalcompat.texture.TextureRows;
import dev.opalcompat.vk.ImageLayouts;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = VulkanImage.class, remap = false)
public abstract class VulkanImageMixin {

	@Shadow
	@Final
	public int formatSize;

	// An upload is handed the whole image and reads a rectangle of it; only the rows of the rectangle are copied
	// to the staging buffer, and the device is told to begin at the first of them: TextureRows.
	@WrapMethod(method = "uploadSubTextureAsync")
	private void opalcompat$onlyTheRowsRead(int mipLevel, int width, int height, int xOffset, int yOffset, int skipRows,
			int skipPixels, int rowLength, ByteBuffer image, Operation<Void> original) {
		ByteBuffer rows = TextureRows.read(image, width, height, skipRows, skipPixels, rowLength, this.formatSize);
		if (rows == null) {
			original.call(mipLevel, width, height, xOffset, yOffset, skipRows, skipPixels, rowLength, image);
		} else {
			original.call(mipLevel, width, height, xOffset, yOffset, 0, 0, rowLength, rows);
		}
	}

	// VulkanMod has no case for GENERAL and throws, and its read barrier only covers the fragment stage,
	// while packs sample in vertex stages as well. Its own images go through here too, which costs a wider
	// barrier and nothing else: the layout it tracks stays true (ImageLayouts sets it).
	@Inject(method = "transitionImageLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/texture/VulkanImage;I)V",
			at = @At("HEAD"), cancellable = true)
	private static void opalcompat$storageLayout(MemoryStack stack, VkCommandBuffer cmd, VulkanImage image,
			int layout, CallbackInfo ci) {
		if (layout == VK_IMAGE_LAYOUT_GENERAL || image.getCurrentLayout() == VK_IMAGE_LAYOUT_GENERAL
				|| layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL || image.getCurrentLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
			ImageLayouts.transition(stack, cmd, image, layout);
			ci.cancel();
		}
	}
}
