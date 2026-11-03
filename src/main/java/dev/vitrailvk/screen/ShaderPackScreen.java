package dev.vitrailvk.screen;

import dev.vitrail.pack.load.PackLoader;
import dev.vitrail.settings.PackSession;
import dev.vitrailvk.VitrailVk;
import dev.vitrailvk.pack.ShaderPacks;
import dev.vitrailvk.render.PackRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Which pack is drawn. Off is a row like any other, and what it leaves is VulkanMod Reforged alone. What went
 * wrong with the last pack is written here in full, since a pack that failed draws nothing to look at.
 */
public final class ShaderPackScreen extends Screen {

	private static final int ROW = 16;
	private static final int FOOTER = 40;
	private static final int STATUS_LINES = 5;

	private final Screen parent;
	private PackList list;
	private Button settings;
	private Button apply;
	private Button copy;
	private String chosen = ShaderPacks.selectedName();
	private String problem;
	private MultiLineLabel statusLabel = MultiLineLabel.EMPTY;
	private String shown;

	public ShaderPackScreen(Screen parent) {
		super(Component.translatable("vitrailvk.screen.packs"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int statusTop = this.height - FOOTER - STATUS_LINES * 10 - 4;
		this.list = new PackList(this.minecraft, this.width, statusTop - 32, 32);
		addRenderableWidget(this.list);

		int gap = 4;
		int count = 5;
		int width = Math.min(120, (this.width - 20 - (count - 1) * gap) / count);
		int x = (this.width - count * width - (count - 1) * gap) / 2;
		int y = this.height - FOOTER + 10;
		this.settings = addRenderableWidget(Button.builder(Component.translatable("vitrailvk.screen.settings"), b -> openSettings())
				.bounds(x, y, width, 20).build());
		x += width + gap;
		addRenderableWidget(Button.builder(Component.translatable("vitrailvk.screen.folder"), b -> openFolder())
				.bounds(x, y, width, 20).build());
		x += width + gap;
		this.copy = addRenderableWidget(Button.builder(Component.translatable("vitrailvk.screen.copy"), b -> copyError())
				.bounds(x, y, width, 20).build());
		x += width + gap;
		this.apply = addRenderableWidget(Button.builder(Component.translatable("vitrailvk.screen.apply"), b -> applyChoice())
				.bounds(x, y, width, 20).build());
		x += width + gap;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
			applyChoice();
			onClose();
		}).bounds(x, y, width, 20).build());
		refresh();
	}

	/** The state of the buttons, and the words under the list, for what is chosen and what is running. */
	private void refresh() {
		this.settings.active = !this.chosen.isEmpty();
		this.apply.active = !this.chosen.equals(ShaderPacks.selectedName());
		String error = this.problem != null ? this.problem : PackRenderer.lastError().orElse(null);
		this.copy.active = error != null;
		Component text;
		if (error != null) {
			text = Component.literal(error).withStyle(ChatFormatting.RED);
		} else if (PackRenderer.needsRestart(this.chosen)) {
			text = Component.translatable("vitrailvk.screen.restart").withStyle(ChatFormatting.YELLOW);
		} else if (ShaderPacks.selectedName().isEmpty()) {
			text = Component.translatable("vitrailvk.screen.off");
		} else if (PackRenderer.runningPack().isPresent()) {
			text = Component.translatable("vitrailvk.screen.running", PackRenderer.runningPack().get());
		} else {
			text = Component.translatable("vitrailvk.screen.pending", ShaderPacks.selectedName());
		}
		String key = text.getString();
		if (!key.equals(this.shown)) {
			this.shown = key;
			this.statusLabel = MultiLineLabel.create(this.font, this.width - 40, STATUS_LINES, text);
		}
	}

	private void applyChoice() {
		if (this.chosen.equals(ShaderPacks.selectedName())) return;
		try {
			ShaderPacks.select(this.chosen);
			this.problem = null;
			PackRenderer.reload();
		} catch (IOException e) {
			VitrailVk.LOGGER.error("Cannot save the chosen shader pack", e);
			this.problem = e.toString();
		}
		refresh();
	}

	private void openSettings() {
		openSettings(this.minecraft, this.chosen, this);
	}

	/** The settings of a pack, coming back to the screen that is showing now. */
	void openSettings(Minecraft minecraft, String pack) {
		openSettings(minecraft, pack, this.parent);
	}

	private void openSettings(Minecraft minecraft, String pack, Screen back) {
		Path path = ShaderPacks.directory().resolve(pack);
		try {
			String language = minecraft.getLanguageManager().getSelected();
			PackSession session = PackSession.read(minecraft.gameDirectory.toPath(), path, language);
			minecraft.setScreen(new PackSettingsScreen(back, session));
		} catch (IOException | RuntimeException e) {
			VitrailVk.LOGGER.error("Cannot read the settings of {}", pack, e);
			this.problem = pack + ": " + e;
			if (back == this) {
				refresh();
			} else {
				minecraft.setScreen(this);
			}
		}
	}

	private void openFolder() {
		Path directory = ShaderPacks.directory();
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			VitrailVk.LOGGER.warn("Cannot create {}", directory, e);
		}
		Util.getPlatform().openFile(directory.toFile());
	}

	private void copyError() {
		String error = this.problem != null ? this.problem : PackRenderer.lastError().orElse(null);
		if (error != null) this.minecraft.keyboardHandler.setClipboard(error);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// A pack that fails does so a frame or more after Apply, while this is on screen.
		refresh();
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
		this.statusLabel.renderCentered(graphics, this.width / 2, this.height - FOOTER - STATUS_LINES * 10);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private final class PackList extends ObjectSelectionList<PackList.Row> {

		PackList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW);
			Row off = new Row("", Component.translatable("vitrailvk.screen.none").withStyle(ChatFormatting.GRAY));
			addEntry(off);
			if (ShaderPackScreen.this.chosen.isEmpty()) setSelected(off);
			List<Path> found;
			try {
				found = PackLoader.candidates(minecraft.gameDirectory.toPath());
			} catch (IOException e) {
				VitrailVk.LOGGER.warn("Cannot list the shader packs", e);
				found = List.of();
			}
			for (Path path : found) {
				String name = path.getFileName().toString();
				Row row = new Row(name, Component.literal(name));
				addEntry(row);
				if (name.equals(ShaderPackScreen.this.chosen)) setSelected(row);
			}
			if (getSelected() != null) centerScrollOn(getSelected());
		}

		@Override
		public int getRowWidth() {
			return Math.min(400, this.width - 40);
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			private final String pack;
			private final Component label;

			Row(String pack, Component label) {
				this.pack = pack;
				this.label = label;
			}

			@Override
			public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
					boolean hovering, float partialTick) {
				boolean live = this.pack.equals(ShaderPacks.selectedName());
				graphics.drawCenteredString(ShaderPackScreen.this.font, this.label, left + width / 2, top + (height - 8) / 2,
						live ? 0xFFFF55 : 0xFFFFFF);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				PackList.this.setSelected(this);
				ShaderPackScreen.this.chosen = this.pack;
				ShaderPackScreen.this.problem = null;
				ShaderPackScreen.this.refresh();
				return true;
			}

			@Override
			public Component getNarration() {
				return this.label;
			}
		}
	}
}
