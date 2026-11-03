package dev.opal.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** What Vitrail Bridge calls of Opal, to compile without its jar. Not in the built jar. */
public final class ShaderPackScreen extends Screen {
	public ShaderPackScreen(Screen parent) {
		super(Component.empty());
	}
}
