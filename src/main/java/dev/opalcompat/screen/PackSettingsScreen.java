package dev.opalcompat.screen;

import com.google.common.collect.ImmutableList;
import dev.vitrail.pack.menu.MenuOption;
import dev.vitrail.pack.menu.MenuPage;
import dev.vitrail.pack.menu.MenuSlot;
import dev.vitrail.pack.menu.MenuValues;
import dev.vitrail.pack.menu.PackMenu;
import dev.vitrail.pack.source.PackLang;
import dev.vitrail.settings.PackSession;
import dev.vitrail.settings.SettingsFile;
import dev.opalcompat.OpalCompat;
import dev.opalcompat.pack.ShaderPacks;
import dev.opalcompat.render.PackRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A pack's own pages, {@code screen.*}, {@code sliders} and {@code profile.*} as {@link PackMenu} lays them out,
 * written to the same {@code shaderpacks/<pack>.txt} Iris reads. Cancel leaves, Apply writes and reloads, Done
 * does both. Left click steps forward, right click back, shift and a click returns a setting to the pack's value.
 */
public final class PackSettingsScreen extends Screen {

	private static final int ROW = 24;
	private static final int GAP = 4;

	private final Screen parent;
	private final PackSession session;
	private final PackMenu menu;
	private final PackLang lang;
	private final MenuValues values;
	private final Deque<String> walked = new ArrayDeque<>();
	private final List<Refreshed> live = new ArrayList<>();
	private Button apply;
	private Button back;
	private String error;

	PackSettingsScreen(Screen parent, PackSession session) {
		super(Component.literal(session.menu().packName()));
		this.parent = parent;
		this.session = session;
		this.menu = session.menu();
		this.lang = this.menu.lang();
		this.values = MenuValues.of(this.menu, session.saved().values(), session.forcedText());
		this.walked.push("");
	}

	@Override
	protected void init() {
		this.live.clear();
		PageList list = new PageList(this.minecraft, this.width, this.height - 72, 32);
		this.menu.page(this.walked.peek()).ifPresent(list::fill);
		addRenderableWidget(list);

		int width = Math.min(100, (this.width - 20 - 3 * GAP) / 4);
		int x = (this.width - 4 * width - 3 * GAP) / 2;
		int y = this.height - 30;
		addRenderableWidget(Button.builder(Component.translatable("opalcompat.screen.reset"), b -> resetAll()).bounds(x, y, width, 20).build());
		x += width + GAP;
		this.back = addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> goBack()).bounds(x, y, width, 20).build());
		x += width + GAP;
		this.apply = addRenderableWidget(Button.builder(Component.translatable("opalcompat.screen.apply"), b -> applySettings()).bounds(x, y, width, 20).build());
		x += width + GAP;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
			applySettings();
			if (this.error == null) this.minecraft.setScreen(this.parent);
		}).bounds(x, y, width, 20).build());
		refresh();
	}

	private void refresh() {
		this.live.forEach(Refreshed::refresh);
		this.apply.active = this.values.pendingCount() > 0;
		this.back.setMessage(this.walked.size() > 1 ? CommonComponents.GUI_BACK : CommonComponents.GUI_CANCEL);
	}

	private void goBack() {
		if (this.walked.size() > 1) {
			this.walked.pop();
			rebuildWidgets();
		} else {
			this.minecraft.setScreen(this.parent);
		}
	}

	private void open(String page) {
		this.walked.push(page);
		rebuildWidgets();
	}

	private void resetAll() {
		this.menu.optionNames().forEach(this.values::reset);
		refresh();
	}

	private void applySettings() {
		if (this.values.pendingCount() == 0) return;
		var stored = new SettingsFile.Stored(this.values.toSave());
		try {
			SettingsFile.write(this.session.settingsFile(), stored);
			this.error = null;
		} catch (IOException e) {
			OpalCompat.LOGGER.error("Cannot write {}", this.session.settingsFile(), e);
			this.error = e.toString();
			return;
		}
		this.values.rebase(stored.values(), this.session.forcedText());
		this.values.clearPending();
		if (this.session.packFileName().equals(ShaderPacks.selectedName())) PackRenderer.reload();
		refresh();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		String page = this.walked.peek();
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		if (!page.isEmpty()) graphics.drawCenteredString(this.font, pack(this.lang.page(page)), this.width / 2, 20, 0xA0A0A0);
		if (this.error != null) {
			graphics.drawCenteredString(this.font, Component.literal(this.error).withStyle(ChatFormatting.RED), this.width / 2, this.height - 42, 0xFFFFFF);
		} else if (this.values.pendingCount() > 0) {
			graphics.drawCenteredString(this.font, Component.translatable("opalcompat.screen.pending_changes", this.values.pendingCount())
					.withStyle(ChatFormatting.YELLOW), this.width / 2, this.height - 42, 0xFFFFFF);
		}
	}

	@Override
	public void onClose() {
		goBack();
	}

	/** Pack text keeps its own section signs. */
	private static MutableComponent pack(String text) {
		return Component.literal(text);
	}

	private interface Refreshed {
		void refresh();
	}

	/** A setting's name, its value, and yellow while what is shown is not what is drawn. */
	private Component label(MenuOption option, String value) {
		MutableComponent shown = option.form() == MenuOption.Form.TOGGLE
				? ("on".equals(value) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).copy()
				: pack(this.lang.value(option.name(), value));
		if (this.values.modified(option.name())) shown = shown.withStyle(ChatFormatting.YELLOW);
		return pack(this.lang.option(option.name())).append(": ").append(shown);
	}

	private Tooltip optionTooltip(MenuOption option) {
		return this.lang.optionComment(option.name()).map(text -> Tooltip.create(pack(text))).orElse(null);
	}

	private AbstractWidget widget(MenuSlot slot, int width) {
		return switch (slot) {
			case MenuSlot.Blank blank -> null;
			case MenuSlot.Option option -> optionWidget(option.option(), width);
			case MenuSlot.Link link -> {
				Button button = Button.builder(pack(this.lang.page(link.page())).append(" >"), b -> open(link.page()))
						.bounds(0, 0, width, 20).build();
				button.active = link.resolved();
				this.lang.pageComment(link.page()).ifPresent(text -> button.setTooltip(Tooltip.create(pack(text))));
				yield button;
			}
			case MenuSlot.Profiles profiles -> new ProfileButton(width);
		};
	}

	private AbstractWidget optionWidget(MenuOption option, int width) {
		boolean forced = this.values.forced(option.name());
		AbstractWidget widget = option.slider() ? new SliderButton(option, width) : new OptionButton(option, width);
		widget.active = !forced && option.form() != MenuOption.Form.FIXED;
		widget.setTooltip(optionTooltip(option));
		return widget;
	}

	private final class OptionButton extends AbstractButton implements Refreshed {
		private final MenuOption option;

		OptionButton(MenuOption option, int width) {
			super(0, 0, width, 20, Component.empty());
			this.option = option;
			PackSettingsScreen.this.live.add(this);
			refresh();
		}

		@Override
		public void refresh() {
			setMessage(label(this.option, PackSettingsScreen.this.values.pending(this.option.name())));
		}

		private void step(int by) {
			var values = PackSettingsScreen.this.values;
			String name = this.option.name();
			if (Screen.hasShiftDown()) {
				values.reset(name);
			} else {
				values.queue(name, this.option.at(this.option.indexOf(values.pending(name)) + by));
			}
			PackSettingsScreen.this.refresh();
		}

		@Override
		public void onPress() {
			step(1);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 1 && this.active && this.visible && isHovered()) {
				playDownSound(Minecraft.getInstance().getSoundManager());
				step(-1);
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private final class SliderButton extends AbstractSliderButton implements Refreshed {
		private final MenuOption option;

		SliderButton(MenuOption option, int width) {
			super(0, 0, width, 20, Component.empty(), position(option, PackSettingsScreen.this.values.pending(option.name())));
			this.option = option;
			PackSettingsScreen.this.live.add(this);
			updateMessage();
		}

		private static double position(MenuOption option, String value) {
			return option.size() < 2 ? 0.0 : option.indexOf(value) / (double) (option.size() - 1);
		}

		@Override
		public void refresh() {
			this.value = position(this.option, PackSettingsScreen.this.values.pending(this.option.name()));
			updateMessage();
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (Screen.hasShiftDown() && button == 0 && this.active && this.visible && isHovered()) {
				PackSettingsScreen.this.values.reset(this.option.name());
				PackSettingsScreen.this.refresh();
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		protected void updateMessage() {
			setMessage(label(this.option, PackSettingsScreen.this.values.pending(this.option.name())));
		}

		@Override
		protected void applyValue() {
			String value = this.option.at((int) Math.round(this.value * (this.option.size() - 1)));
			if (value.equals(PackSettingsScreen.this.values.pending(this.option.name()))) return;
			PackSettingsScreen.this.values.queue(this.option.name(), value);
			PackSettingsScreen.this.refresh();
		}
	}

	/** Walks the pack's profiles from the most constrained; what the values amount to is read back from them. */
	private final class ProfileButton extends AbstractButton implements Refreshed {

		ProfileButton(int width) {
			super(0, 0, width, 20, Component.empty());
			PackSettingsScreen.this.live.add(this);
			PackSettingsScreen.this.lang.profileComment().ifPresent(text -> setTooltip(Tooltip.create(pack(text))));
			refresh();
		}

		@Override
		public void refresh() {
			String current = PackSettingsScreen.this.values.matchedProfile();
			Component shown = current.isEmpty() ? Component.translatable("opalcompat.screen.profile_custom")
					: pack(PackSettingsScreen.this.lang.profile(current));
			setMessage(Component.translatable("opalcompat.screen.profile").append(": ").append(shown));
		}

		private void step(int by) {
			List<String> names = PackSettingsScreen.this.menu.profileNames();
			if (names.isEmpty()) return;
			int at = names.indexOf(PackSettingsScreen.this.values.matchedProfile());
			int next = at < 0 ? (by > 0 ? 0 : names.size() - 1) : Math.floorMod(at + by, names.size());
			PackSettingsScreen.this.values.queueProfile(names.get(next));
			PackSettingsScreen.this.refresh();
		}

		@Override
		public void onPress() {
			step(1);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 1 && this.active && this.visible && isHovered()) {
				playDownSound(Minecraft.getInstance().getSoundManager());
				step(-1);
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private final class PageList extends ContainerObjectSelectionList<PageList.Line> {

		PageList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW);
		}

		@Override
		public int getRowWidth() {
			return Math.min(this.width - 40, 840);
		}

		@Override
		protected int getScrollbarPosition() {
			return (this.width + getRowWidth()) / 2 + 6;
		}

		void fill(MenuPage page) {
			int columns = Math.max(1, page.columns());
			int width = (getRowWidth() - (columns - 1) * GAP) / columns;
			List<AbstractWidget> widgets = new ArrayList<>(columns);
			int column = 0;
			for (MenuSlot slot : page.slots()) {
				widgets.add(PackSettingsScreen.this.widget(slot, width));
				if (++column == columns) {
					addEntry(new Line(widgets, width));
					widgets = new ArrayList<>(columns);
					column = 0;
				}
			}
			if (!widgets.isEmpty()) addEntry(new Line(widgets, width));
		}

		/** One row of a page by position: a blank is a slot too, and a pack aligns its columns by them. */
		final class Line extends ContainerObjectSelectionList.Entry<Line> {
			private final List<AbstractWidget> slots;
			private final List<AbstractWidget> widgets;
			private final int width;

			Line(List<AbstractWidget> slots, int width) {
				this.slots = slots;
				this.widgets = slots.stream().filter(java.util.Objects::nonNull).toList();
				this.width = width;
			}

			@Override
			public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
					boolean hovering, float partialTick) {
				int x = left;
				for (AbstractWidget widget : this.slots) {
					if (widget != null) {
						widget.setPosition(x, top);
						widget.render(graphics, mouseX, mouseY, partialTick);
					}
					x += this.width + GAP;
				}
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return this.widgets;
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return ImmutableList.copyOf(this.widgets);
			}
		}
	}
}
