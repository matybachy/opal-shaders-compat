package dev.vitrailvk.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.vitrailvk.render.PackRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(value = VertexBuffer.class, priority = 1200)
public abstract class VertexBufferMixin {
	@Shadow @Final private VertexBuffer.Usage usage;
	@Unique private MeshData.DrawState vitrailvk$state;
	@Unique private byte[] vitrailvk$vertices;
	@Unique private byte[] vitrailvk$indices;

	// TODO-VERIFY: VulkanMod's upload closes MeshData; this higher-priority copy must run first.
	@Inject(method = "upload", at = @At("HEAD"))
	private void vitrailvk$capture(MeshData mesh, CallbackInfo ci) {
		if (this.usage != VertexBuffer.Usage.STATIC) return;
		this.vitrailvk$state = mesh.drawState();
		this.vitrailvk$vertices = vitrailvk$copy(mesh.vertexBuffer());
		ByteBuffer indices = mesh.indexBuffer();
		this.vitrailvk$indices = indices == null ? null : vitrailvk$copy(indices);
	}

	// TODO-VERIFY: confirm sorted index reuploads and their ownership under VulkanMod.
	@Inject(method = "uploadIndexBuffer", at = @At("HEAD"))
	private void vitrailvk$captureIndices(ByteBufferBuilder.Result result, CallbackInfo ci) {
		if (this.vitrailvk$state != null) this.vitrailvk$indices = vitrailvk$copy(result.byteBuffer());
	}

	// TODO-VERIFY: this precedes VulkanMod's cancellable drawWithShader injection.
	@Inject(method = "drawWithShader", at = @At("HEAD"), cancellable = true)
	private void vitrailvk$draw(Matrix4f view, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
		if (this.vitrailvk$state == null || this.vitrailvk$vertices == null || shader == null) return;
		ByteBuffer indices = this.vitrailvk$indices == null ? null : ByteBuffer.wrap(this.vitrailvk$indices);
		if (PackRenderer.drawStaticMesh(this.vitrailvk$state, ByteBuffer.wrap(this.vitrailvk$vertices), indices,
				shader.getName(), view, projection)) ci.cancel();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void vitrailvk$release(CallbackInfo ci) {
		this.vitrailvk$state = null;
		this.vitrailvk$vertices = null;
		this.vitrailvk$indices = null;
	}

	@Unique
	private static byte[] vitrailvk$copy(ByteBuffer source) {
		ByteBuffer copy = source.duplicate();
		byte[] bytes = new byte[copy.remaining()];
		copy.get(bytes);
		return bytes;
	}
}
