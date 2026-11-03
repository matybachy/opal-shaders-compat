package dev.vitrailbridge.mixin;

import dev.opal.pack.ImageSpec;
import dev.vitrailbridge.opal.CustomImageUnits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** One custom image of Opal's, a private record, as {@link CustomImageUnits} reads it. */
@Pseudo
@Mixin(targets = "dev.opal.client.CustomImages$Image", remap = false)
public abstract class OpalImageMixin implements CustomImageUnits.Image {

	@Shadow
	public abstract ImageSpec spec();

	@Shadow
	public abstract int texture();

	@Shadow
	public abstract int target();

	@Shadow
	public abstract int format();

	@Override
	public String vitrailbridge$uniform() {
		return spec().uniform();
	}

	@Override
	public String vitrailbridge$sampler() {
		return spec().sampler();
	}

	@Override
	public int vitrailbridge$texture() {
		return texture();
	}

	@Override
	public int vitrailbridge$target() {
		return target();
	}

	@Override
	public int vitrailbridge$format() {
		return format();
	}
}
