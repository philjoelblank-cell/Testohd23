package de.philjoel.packmixer.mixin;

import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enchantment glint only on tools, weapons and armor (everything with durability), not on books, apples ... */
@Mixin(ItemStack.class)
public abstract class ItemStackFoilMixin {
	@Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
	private void testohd$glintOnlyOnGear(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || !PackMixerConfig.settings().glintOnlyGear) return;
		ItemStack self = (ItemStack) (Object) this;
		if (!self.has(DataComponents.MAX_DAMAGE)) cir.setReturnValue(false);
	}
}
