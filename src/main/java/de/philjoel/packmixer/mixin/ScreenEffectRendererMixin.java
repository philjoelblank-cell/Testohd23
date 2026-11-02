package de.philjoel.packmixer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: moves the burning overlay down. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void packmixer$lowFireStart(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.pushPose();
		if (PackMixerConfig.settings().lowFire) {
			pose.translate(0.0F, (float) -PackMixerConfig.settings().fireOffset, 0.0F);
		}
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void packmixer$lowFireEnd(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.popPose();
	}
}
