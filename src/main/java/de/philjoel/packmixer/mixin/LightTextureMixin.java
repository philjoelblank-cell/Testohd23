package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fullbright: makes the light map use a very high brightness value. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@Redirect(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object packmixer$fullbright(OptionInstance<?> option) {
		Object value = option.get();
		if (PackMixerConfig.settings().fullbright && option == Minecraft.getInstance().options.gamma()) {
			return 16.0D;
		}
		return value;
	}
}
