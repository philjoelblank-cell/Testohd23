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

/** Testohd capes (cape + elytra texture) and the "T" badge in the name tag of mod users. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarCapeMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void packmixer$cape(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (avatar instanceof Entity e) {
			Capes.apply(e, state);
			de.philjoel.packmixer.Emotes.fixCape(state, e.getUUID());
			try {
				if (state.nameTag != null && de.philjoel.packmixer.Badge.has(e.getUUID())) state.nameTag = de.philjoel.packmixer.Badge.prefix(state.nameTag);
			} catch (Throwable ignored) {
			}
		}
	}
}
