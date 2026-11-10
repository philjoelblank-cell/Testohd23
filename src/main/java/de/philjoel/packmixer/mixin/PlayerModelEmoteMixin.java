package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: bends the player model after the normal animation. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelEmoteMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void packmixer$emote(AvatarRenderState state, CallbackInfo ci) {
		Emotes.pose((PlayerModel) (Object) this, state);
	}
}
