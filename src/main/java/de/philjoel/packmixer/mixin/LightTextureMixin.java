package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: maximum brightness + night vision light, only on your screen. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightTextureMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void packmixer$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (PackMixerConfig.settings().fullbright) {
			state.brightness = 16.0F;
			state.nightVisionEffectIntensity = 1.0F;
		}
	}
}
