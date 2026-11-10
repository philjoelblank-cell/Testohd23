package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.Capes;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Testohd capes: replaces the cape (and elytra) texture of players who wear one. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarCapeMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void packmixer$cape(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (avatar instanceof Entity e) Capes.apply(e, state);
	}
}
