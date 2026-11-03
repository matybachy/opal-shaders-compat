package dev.vector.fabric.gui;

/** What Vitrail Bridge calls of Vectors, to compile without its jar. Not in the built jar. */
public interface VectorPageBuilder {
	VectorPageBuilder addAction(String title, String hint, String button, boolean primary, Runnable action);
}
