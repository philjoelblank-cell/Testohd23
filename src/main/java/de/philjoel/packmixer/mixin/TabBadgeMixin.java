package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.Badge;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Testohd "T" in front of the names of mod users in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void packmixer$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		try {
			if (Badge.has(info.getProfile().id())) cir.setReturnValue(Badge.prefix(cir.getReturnValue()));
		} catch (Throwable ignored) { // other client mods (NoRisk, tier taggers ...) must keep working
		}
	}
}
