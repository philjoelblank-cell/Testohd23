package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Kleine Partikel": every particle drawn smaller (crits, explosions, smoke, ...), size from the settings. */
@Mixin(SingleQuadParticle.class)
public abstract class ParticleSizeMixin {
	@Inject(method = "getQuadSize", at = @At("RETURN"), cancellable = true)
	private void testohd$smallParticles(float partialTick, CallbackInfoReturnable<Float> cir) {
		double s = PackMixerConfig.settings().particleSize;
		if (s < 0.999) cir.setReturnValue((float) (cir.getReturnValueF() * Math.max(0.1, s)));
	}
}
