package dev.vector.core;

/** What Vitrail Bridge calls of Vectors, to compile without its jar. Not in the built jar. */
public final class VectorConfig {
	public String profile;

	public void validate() {
		throw new AssertionError();
	}

	public void applyProfile(String name) {
		throw new AssertionError();
	}
}
