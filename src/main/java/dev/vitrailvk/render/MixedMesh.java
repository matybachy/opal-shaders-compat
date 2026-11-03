package dev.vitrailvk.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Partitions triangles into consecutive entity and block-entity runs without reordering them. */
final class MixedMesh {
	private static final int[] QUAD = {0, 1, 2, 0, 2, 3};
	record Run(DrawIds.Kind kind, ByteBuffer indices, int count) {
	}

	private MixedMesh() {
	}

	// TODO-VERIFY: verify sorted index streams and primitive boundaries with live batched geometry.
	static List<Run> split(MeshData mesh, DrawIds[] ids) {
		MeshData.DrawState state = mesh.drawState();
		if (ids == null || ids.length != state.vertexCount()) return List.of();
		if (state.mode() != VertexFormat.Mode.QUADS && state.mode() != VertexFormat.Mode.TRIANGLES) return List.of();
		ByteBuffer input = mesh.indexBuffer();
		int count;
		if (input == null) {
			if (state.mode() == VertexFormat.Mode.QUADS && state.vertexCount() % 4 != 0) return List.of();
			count = state.mode() == VertexFormat.Mode.QUADS ? Math.multiplyExact(state.vertexCount() / 4, 6) : state.vertexCount();
		} else {
			count = state.indexCount();
			input = input.duplicate().order(ByteOrder.nativeOrder());
			if (count < 0 || input.remaining() < (long) count * state.indexType().bytes) return List.of();
		}
		if (count == 0 || count % 3 != 0) return List.of();
		int[] all = new int[count];
		for (int i = 0; i < count; i++) {
			int index;
			if (input != null) index = state.indexType() == VertexFormat.IndexType.SHORT
					? Short.toUnsignedInt(input.getShort()) : input.getInt();
			else if (state.mode() == VertexFormat.Mode.QUADS) {
				index = i / 6 * 4 + QUAD[i % 6];
			} else index = i;
			if (index < 0 || index >= ids.length) return List.of();
			all[i] = index;
		}
		List<Run> runs = new ArrayList<>();
		int start = 0;
		DrawIds.Kind kind = null;
		for (int i = 0; i < count; i += 3) {
			DrawIds.Kind triangle = ids[all[i]].kind();
			if (triangle == DrawIds.Kind.NONE || ids[all[i + 1]].kind() != triangle
					|| ids[all[i + 2]].kind() != triangle) return List.of();
			if (kind != null && triangle != kind) {
				runs.add(run(kind, all, start, i));
				start = i;
			}
			kind = triangle;
		}
		runs.add(run(kind, all, start, count));
		return List.copyOf(runs);
	}

	private static Run run(DrawIds.Kind kind, int[] indices, int start, int end) {
		ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(end - start, Integer.BYTES)).order(ByteOrder.nativeOrder());
		for (int i = start; i < end; i++) bytes.putInt(indices[i]);
		bytes.flip();
		return new Run(kind, bytes, end - start);
	}
}
