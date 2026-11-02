package dev.vitrailvk.render;

/** Per-vertex identifiers captured while a shared game buffer is being built. */
public interface MeshIds {
	void vitrailvk$setIds(DrawIds[] ids);
	DrawIds[] vitrailvk$ids();
}
