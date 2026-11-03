package dev.vitrailbridge.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.render.GuiRenderer;
import net.vulkanmod.config.gui.widget.OptionWidget;
import net.vulkanmod.config.option.Option;
import net.vulkanmod.vulkan.util.ColorUtil;

/** A row of VulkanMod Reforged's settings lists with a button in it. It holds no value, so nothing waits for Apply. */
final class ActionOption extends Option<Boolean> {

	private final Runnable action;

	ActionOption(Component name, Component button, Runnable action) {
		super(name, value -> {}, () -> Boolean.FALSE, value -> button);
		this.action = action;
	}

	@Override
	protected OptionWidget<?> createWidget() {
		return new Widget(this, this.name);
	}

	private void run() {
		this.action.run();
	}

	private static final class Widget extends OptionWidget<ActionOption> {

		private final ActionOption action;

		Widget(ActionOption action, Component name) {
			super(action, name);
			this.action = action;
		}

		@Override
		protected void renderControls(double mouseX, double mouseY) {
			int top = this.y + 2;
			int height = this.height - 4;
			float fill = !this.active ? 0.1F : this.controlHovered ? 0.35F : 0.2F;
			GuiRenderer.fillBox(this.controlX, top, this.controlWidth, height, ColorUtil.ARGB.pack(fill, fill, fill, 0.9F));
			GuiRenderer.renderBoxBorder(this.controlX, top, this.controlWidth, height, 1, ColorUtil.ARGB.pack(0.6F, 0.6F, 0.6F, 1.0F));
			GuiRenderer.drawScrollingString(Minecraft.getInstance().font, getDisplayedValue(), this.controlX + this.controlWidth / 2,
					this.y + (this.height - 9) / 2, this.controlWidth - 8, this.active ? 0xFFFFFFFF : 0xFFA0A0A0);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			this.action.run();
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
		}

		@Override
		protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
		}
	}
}
