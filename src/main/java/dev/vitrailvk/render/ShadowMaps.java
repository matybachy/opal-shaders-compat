package dev.vitrailvk.render;

import dev.vitrail.pack.model.TargetFormat;
import dev.vitrail.pack.target.PackDirectives;
import dev.vitrailvk.vk.Vk;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The maps drawn from the light: {@code shadowtex0} with everything in it, {@code shadowtex1} copied
 * before the translucents, and the {@code shadowcolor} targets the pack names. All of one square size.
 * Row 0 is the bottom, as in the pack's other targets.
 */
final class ShadowMaps implements AutoCloseable {

	private static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;

	private final int resolution;
	private final VulkanImage depth;
	private final VulkanImage depthNoTranslucent;
	private final Map<Integer, VulkanImage> colours = new TreeMap<>();
	private final List<PackDirectives.ShadowColour> directives;
	private final long compareSampler;

	ShadowMaps(int resolution, Set<Integer> named, List<PackDirectives.ShadowColour> directives,
			List<PackDirectives.ShadowDepth> depths) {
		this.resolution = resolution;
		this.directives = directives;
		int usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
				| VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
		this.depth = VulkanImage.createDepthImage(DEPTH_FORMAT, resolution, resolution, usage, linear(depths, 0), true);
		this.depthNoTranslucent = VulkanImage.createDepthImage(DEPTH_FORMAT, resolution, resolution, usage,
				linear(depths, 1), true);
		for (int index : named) colour(index);
		this.compareSampler = compareSampler();
	}

	private static boolean linear(List<PackDirectives.ShadowDepth> depths, int index) {
		return index >= depths.size() || !depths.get(index).nearest();
	}

	/** {@code GL_LINEAR} plus {@code GL_COMPARE_REF_TO_TEXTURE}, LEQUAL: what a {@code sampler2DShadow} is written against. */
	private static long compareSampler() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack).sType$Default()
					.magFilter(VK_FILTER_LINEAR).minFilter(VK_FILTER_LINEAR)
					.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
					.addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
					.addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
					.addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
					.compareEnable(true).compareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
					.minLod(0.0F).maxLod(0.0F);
			LongBuffer handle = stack.mallocLong(1);
			Vk.check(vkCreateSampler(Vk.device(), info, null, handle), "vkCreateSampler");
			return handle.get(0);
		}
	}

	int resolution() {
		return this.resolution;
	}

	int depthFormat() {
		return DEPTH_FORMAT;
	}

	VulkanImage depth() {
		return this.depth;
	}

	VulkanImage depthNoTranslucent() {
		return this.depthNoTranslucent;
	}

	long compare() {
		return this.compareSampler;
	}

	Map<Integer, VulkanImage> colours() {
		return this.colours;
	}

	/** The target, made the first time anything names it: a program's draw buffer may be the only mention. */
	VulkanImage colour(int index) {
		return this.colours.computeIfAbsent(index, i -> {
			TargetFormat format = i < this.directives.size()
					? this.directives.get(i).format().used() : TargetFormat.RGBA8_UNORM;
			return VulkanImage.builder(this.resolution, this.resolution)
					.setFormat(PackTargets.vkFormat(format))
					.setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
							| VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
							| VK_IMAGE_USAGE_STORAGE_BIT)
					.setLinearFiltering(!format.integer())
					.setClamp(true)
					.createVulkanImage();
		});
	}

	PackDirectives.ShadowColour directive(int index) {
		return index < this.directives.size() ? this.directives.get(index) : null;
	}

	@Override
	public void close() {
		this.depth.free();
		this.depthNoTranslucent.free();
		this.colours.values().forEach(VulkanImage::free);
		this.colours.clear();
		vkDestroySampler(Vk.device(), this.compareSampler, null);
	}
}
