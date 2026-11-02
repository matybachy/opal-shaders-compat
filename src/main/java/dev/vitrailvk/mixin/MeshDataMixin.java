package dev.vitrailvk.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import dev.vitrailvk.render.DrawIds;
import dev.vitrailvk.render.MeshIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MeshData.class)
public abstract class MeshDataMixin implements MeshIds {
	@Unique private DrawIds[] vitrailvk$ids;

	@Override
	public void vitrailvk$setIds(DrawIds[] ids) {
		this.vitrailvk$ids = ids;
	}

	@Override
	public DrawIds[] vitrailvk$ids() {
		return this.vitrailvk$ids;
	}
}
