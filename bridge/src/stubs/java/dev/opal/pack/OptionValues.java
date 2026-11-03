package dev.opal.pack;

import java.io.IOException;
import java.nio.file.Path;

/** What Vitrail Bridge calls of Opal, to compile without its jar. Not in the built jar. */
public final class OptionValues {
	public static OptionValues load(Path file) throws IOException {
		throw new AssertionError();
	}
}
