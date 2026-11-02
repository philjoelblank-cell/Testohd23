package de.philjoel.packmixer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.philjoel.packmixer.SkyboxRenderer;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom sky boxes (OptiFine format) on top of the sky colour, below sun, moon and stars. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	private void testohd$customSky(RenderPass pass, PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
			float rainBrightness, float starBrightness, CallbackInfo ci) {
		SkyboxRenderer.renderOverworld(pass, sunAngle, rainBrightness);
	}

	@Inject(method = "renderEndSky", at = @At("TAIL"))
	private void testohd$customEndSky(RenderPass pass, CallbackInfo ci) {
		SkyboxRenderer.renderEnd(pass);
	}
}
