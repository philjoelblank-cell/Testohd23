package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.Features;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: divides the field of view while the zoom key is held (26.x: the camera computes the FOV). */
@Mixin(Camera.class)
public abstract class GameRendererMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void packmixer$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		float factor = Features.zoomFactor(partialTick);
		if (factor != 1f) {
			cir.setReturnValue(cir.getReturnValue() / factor);
		}
	}
}
