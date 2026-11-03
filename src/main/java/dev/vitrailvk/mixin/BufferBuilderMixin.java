package dev.vitrailvk.mixin;

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

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {
	@Shadow private int vertices;
	@Unique private List<DrawIds> vitrailvk$ids;

	// TODO-VERIFY: confirm builder vertex counts and duplicated line vertices at runtime.
	@Inject(method = "beginVertex", at = @At("RETURN"))
	private void vitrailvk$captureVertex(CallbackInfoReturnable<Long> cir) {
		if (this.vitrailvk$ids == null) {
			if (!PackRenderer.capturesDrawIds()) return;
			this.vitrailvk$ids = new ArrayList<>();
		}
		while (this.vitrailvk$ids.size() < this.vertices - 1) {
			int size = this.vitrailvk$ids.size();
			this.vitrailvk$ids.add(size == 0 ? DrawIds.NONE : this.vitrailvk$ids.get(size - 1));
		}
		this.vitrailvk$ids.add(PackRenderer.currentDrawIds());
	}

	// TODO-VERIFY: confirm the mesh retains its vertex metadata through NeoForge and VulkanMod batching.
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
