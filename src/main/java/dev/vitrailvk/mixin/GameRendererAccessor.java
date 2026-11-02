package dev.vitrailvk.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	// TODO-VERIFY: verify private hand setup accessors against the running NeoForge client.
	@Accessor("renderHand") boolean vitrailvk$renderHand();
	@Invoker("getFov") double vitrailvk$getFov(Camera camera, float tick, boolean useSetting);
	@Invoker("bobHurt") void vitrailvk$bobHurt(PoseStack poses, float tick);
	@Invoker("bobView") void vitrailvk$bobView(PoseStack poses, float tick);
}
