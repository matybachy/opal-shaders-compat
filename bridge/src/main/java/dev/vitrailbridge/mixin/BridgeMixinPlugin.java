package dev.vitrailbridge.mixin;

import dev.vitrailbridge.Mods;
import net.neoforged.fml.loading.FMLPaths;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/**
 * Each mixin applies where what it is for is in the game: {@code OpalOnVulkan} with Opal on VulkanMod Reforged, the rest of
 * {@code Opal} and {@code Flywheel} with Opal as the renderer, on OpenGL.
 */
public final class BridgeMixinPlugin implements IMixinConfigPlugin {

	@Override
	public void onLoad(String mixinPackage) {
		if (!Mods.VULKAN) return;
		// Vectors' terrain mixins draw through OpenGL buffers that VulkanMod does not use. All plugins load before the first asks.
		System.setProperty("vectors.disable1211Terrain", "true");
		if (Mods.OPAL) {
			// Opal's mark of a shader startup that did not finish. Here it starts none, so a mark left from OpenGL says nothing.
			try {
				Files.deleteIfExists(FMLPaths.CONFIGDIR.get().resolve("opal-startup-failed.properties"));
			} catch (IOException e) {
				// It then turns its own shaders off once, which here changes nothing.
			}
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String mixin = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (mixin.startsWith("OpalOnVulkan")) return Mods.OPAL && Mods.VULKAN;
		return Mods.OPAL && !Mods.VULKAN;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
