package dev.vitrailvk.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * The matrices the level is drawn with this frame, captured from the arguments of
 * {@code LevelRenderer.renderLevel}. On VulkanMod Reforged the projection is forward Z over 0..1
 * ({@code Matrix4fM} forces {@code zZeroToOne}); the model view is the camera rotation only.
 */
public final class LevelCapture {

	private static final Matrix4f MODEL_VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();

	private LevelCapture() {
	}

	public static void capture(Matrix4fc modelView, Matrix4fc projection) {
		MODEL_VIEW.set(modelView);
		PROJECTION.set(projection);
	}

	public static Matrix4fc modelView() {
		return MODEL_VIEW;
	}

	public static Matrix4fc projection() {
		return PROJECTION;
	}

	/**
	 * Rewrites a forward 0..1 matrix as the reversed 0..1 one Vitrail's {@code ViewMatrices} expects:
	 * the z row becomes {@code rowW - rowZ}. Vitrail's {@code ClipSpace.toLegacyDepth} then yields
	 * {@code 2 * rowZ - rowW}, the OpenGL form, exactly.
	 */
	public static Matrix4f forwardToReversed(Matrix4fc forward, Matrix4f dest) {
		float m02 = forward.m03() - forward.m02();
		float m12 = forward.m13() - forward.m12();
		float m22 = forward.m23() - forward.m22();
		float m32 = forward.m33() - forward.m32();
		dest.set(forward);
		dest.m02(m02);
		dest.m12(m12);
		dest.m22(m22);
		dest.m32(m32);
		return dest;
	}
}
