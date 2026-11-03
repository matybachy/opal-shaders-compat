package dev.vitrailvk.mixin;

import dev.vitrailvk.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.VOptionScreen;
import net.vulkanmod.config.gui.util.VGuiConstants;
import net.vulkanmod.config.gui.widget.VButtonWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** VulkanMod Reforged's video settings stand in for vanilla's, so the way to the packs is on them: bottom left, under its pages. */
@Mixin(value = VOptionScreen.class, remap = false)
public abstract class VOptionScreenMixin extends Screen {

	@Shadow @Final private List<VButtonWidget> buttons;

	private VOptionScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "addButtonsWithSearchBar", at = @At("TAIL"))
	private void vitrailvk$shaderPacks(CallbackInfo ci) {
		Screen self = this;
		VButtonWidget button = new VButtonWidget(VOptionScreen.MARGIN, this.height - VGuiConstants.WIDGET_HEIGHT - 7,
				VGuiConstants.PAGE_BUTTON_WIDTH, VGuiConstants.WIDGET_HEIGHT, Component.translatable("vitrailvk.screen.packs"),
				widget -> Minecraft.getInstance().setScreen(new ShaderPackScreen(self)));
		this.buttons.add(button);
		addWidget(button);
	}
}
