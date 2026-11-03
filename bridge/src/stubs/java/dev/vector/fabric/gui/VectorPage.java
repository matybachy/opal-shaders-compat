package dev.vector.fabric.gui;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** What Vitrail Bridge calls of Vectors, to compile without its jar. Not in the built jar. */
public record VectorPage(String id, Component title, String icon, int order, Consumer<VectorPageBuilder> builder) {
}
