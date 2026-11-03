package dev.vitrailvk.render;

import dev.vitrail.pack.model.PackTexture;
import dev.vitrail.pack.model.PixelType;
import dev.vitrail.pack.model.TextureStage;
import dev.vitrail.pack.source.OpenedPack;
import dev.vitrail.pack.target.SamplerPlan;
import dev.vitrail.pack.texture.PackTextures;
import dev.vitrail.pack.texture.RawImage;
import dev.vitrail.pack.texture.VolumeAtlas;
import dev.vitrail.uniform.NoiseTexture;
import dev.vitrailvk.vk.PackImage;
import dev.vitrailvk.vk.Vk;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFormatProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/** Files named by texture.* and customTexture.*, with the same override rules as translation. */
final class TextureImages implements AutoCloseable {

	private final PackTextures declared;
	private final Map<PackTexture, PackImage> images = new HashMap<>();
	private final Map<String, PackImage> volumes = new HashMap<>();
	private final PackImage black;

	// TODO-VERIFY: verify pack textures, atlas rewrites, 1D lookup sampling and resource reloads in game.
	TextureImages(OpenedPack pack) throws IOException {
		this.declared = PackTextures.read(pack.properties(), pack.settings().globalDefines(pack.options()), pack.source());
		this.black = upload(2, 1, 1, VK_FORMAT_R8G8B8A8_UNORM, new byte[4], false, true);
		try {
			Map<String, VolumeAtlas> atlases = this.declared.volumes();
			for (PackTexture texture : this.declared.supplied()) {
				if (texture.gameResource()) continue;
				Path file = pack.source().file(texture.path()).orElseThrow(() -> new IOException("Missing texture " + texture.path()));
				PackImage image;
				if (texture.png()) {
					NoiseTexture.Image decoded = NoiseTexture.decode(pack.source().bytes(file));
					image = upload(2, decoded.width(), decoded.height(), VK_FORMAT_R8G8B8A8_UNORM,
							decoded.rgba(), texture.blur(), texture.clamp());
				} else {
					PackTexture.Raw raw = texture.raw().orElseThrow();
					VolumeAtlas atlas = atlases.get(texture.sampler());
					if (raw.shape() == PackTexture.Shape.TEXTURE_3D && atlas != null) {
						// Translation moves these onto a forged sampler2D name; use precisely its layout.
						if (this.volumes.containsKey(SamplerPlan.forged(texture.sampler()))) continue;
						image = upload(2, atlas.atlasWidth(), atlas.atlasHeight(), format(atlas.type()),
								atlas.spread(pack.source().head(file, raw.bytes())), texture.blur(), true);
						this.volumes.put(SamplerPlan.forged(texture.sampler()), image);
					} else {
						boolean line = raw.shape() == PackTexture.Shape.TEXTURE_1D;
						PackTexture.Raw flat = line ? new PackTexture.Raw(PackTexture.Shape.TEXTURE_2D,
								raw.internalFormat(), raw.sizeX(), 1, 0, raw.pixelFormat(), raw.pixelType()) : raw;
						// TODO-VERIFY: add packed/BGR/integer conversion and rectangle coordinates; fail instead of misinterpreting bytes.
						if (!RawImage.serves(flat)) throw new IOException("Unsupported raw texture: " + texture.describe());
						RawImage data = RawImage.of(flat);
						if (!data.fits()) throw new IOException("Raw texture too large: " + texture.path());
						image = upload(line ? 1 : 2, data.width(), data.height(), format(data.type()),
								data.widen(pack.source().head(file, raw.bytes())), texture.blur(), texture.clamp());
					}
				}
				this.images.put(texture, image);
			}
		} catch (IOException | RuntimeException | Error failure) {
			close();
			throw failure;
		}
	}

	private static int format(PixelType type) {
		return switch (type) {
			case UNSIGNED_BYTE -> VK_FORMAT_R8G8B8A8_UNORM;
			case UNSIGNED_SHORT -> VK_FORMAT_R16G16B16A16_UNORM;
			case HALF_FLOAT -> VK_FORMAT_R16G16B16A16_SFLOAT;
			case FLOAT -> VK_FORMAT_R32G32B32A32_SFLOAT;
			default -> throw new IllegalArgumentException("Unsupported raw channel " + type);
		};
	}

	private static PackImage upload(int dimensions, int width, int height, int format, byte[] pixels, boolean linear, boolean clamp) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkFormatProperties properties = VkFormatProperties.malloc(stack);
			vkGetPhysicalDeviceFormatProperties(Vk.device().getPhysicalDevice(), format, properties);
			linear &= (properties.optimalTilingFeatures() & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) != 0;
		}
		PackImage image = PackImage.create(dimensions, width, height, 1, format,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, linear, clamp);
		try { image.upload(pixels); }
		catch (RuntimeException | Error failure) { image.doFree(); throw failure; }
		return image;
	}

	VulkanImage resolve(String program, String sampler) {
		PackImage volume = this.volumes.get(sampler);
		if (volume != null) return volume;
		TextureStage stage = TextureStage.of(program).orElse(TextureStage.GBUFFERS);
		PackTexture texture = this.declared.resolve(stage, sampler).orElse(null);
		if (texture == null) return this.declared.suppliedTo(stage).contains(sampler) ? this.black : null;
		if (!texture.gameResource()) return this.images.getOrDefault(texture, this.black);
		// Iris CustomTextureManager re-fetches game textures at each bind, including stitched atlases.
		ResourceLocation location = ResourceLocation.parse(texture.path());
		var held = Minecraft.getInstance().getTextureManager().getTexture(location);
		GlTexture gl = held == null ? null : GlTexture.getTexture(held.getId());
		return gl == null || gl.getVulkanImage() == null ? this.black : gl.getVulkanImage();
	}

	@Override
	public void close() {
		this.images.values().forEach(PackImage::free);
		this.images.clear();
		this.volumes.clear();
		this.black.free();
	}
}
