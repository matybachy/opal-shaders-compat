package dev.vitrailvk.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.vitrailvk.render.DrawIds;
import dev.vitrailvk.render.MeshIds;
import dev.vitrailvk.render.PackRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * The owner of each vertex, as it is written: a batch mixes owners and is drawn long after they are gone.
 * Every way to write a vertex goes through {@code beginVertex}, VulkanMod's {@code BufferBuilderM} too;
 * only the copies a line makes of its vertices do not, which is what the filling below is for.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {
	@Shadow private int vertices;
	@Unique private List<DrawIds> vitrailvk$ids;

	// A return value handler and not a callback: this runs for every vertex of the game, GUI included,
	// and a callback would box the pointer into a new object each time.
	@ModifyReturnValue(method = "beginVertex", at = @At("RETURN"))
	private long vitrailvk$captureVertex(long pointer) {
		if (this.vitrailvk$ids == null) {
			if (!PackRenderer.capturesDrawIds()) return pointer;
			this.vitrailvk$ids = new ArrayList<>();
		}
		while (this.vitrailvk$ids.size() < this.vertices - 1) {
			int size = this.vitrailvk$ids.size();
			this.vitrailvk$ids.add(size == 0 ? DrawIds.NONE : this.vitrailvk$ids.get(size - 1));
		}
		this.vitrailvk$ids.add(PackRenderer.currentDrawIds());
		return pointer;
	}

	@Inject(method = "build", at = @At("RETURN"))
	private void vitrailvk$finish(CallbackInfoReturnable<MeshData> cir) {
		MeshData mesh = cir.getReturnValue();
		if (mesh == null || this.vitrailvk$ids == null) return;
		while (this.vitrailvk$ids.size() < mesh.drawState().vertexCount()) {
			int size = this.vitrailvk$ids.size();
			this.vitrailvk$ids.add(size == 0 ? DrawIds.NONE : this.vitrailvk$ids.get(size - 1));
		}
		((MeshIds) (Object) mesh).vitrailvk$setIds(this.vitrailvk$ids.toArray(DrawIds[]::new));
		this.vitrailvk$ids = null;
	}
}
