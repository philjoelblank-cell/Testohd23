package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clear weather: only on your screen (client side), the server still has rain. */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void packmixer$clearWeather(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (PackMixerConfig.settings().clearWeather && (Object) this instanceof ClientLevel) {
			cir.setReturnValue(0.0F);
		}
	}
}
