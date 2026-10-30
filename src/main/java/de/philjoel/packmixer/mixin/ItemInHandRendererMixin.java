package de.philjoel.packmixer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.philjoel.packmixer.PackMixerConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item position: moves / scales items held in first person. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void packmixer$itemStart(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		pose.pushPose();
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		if (s.itemPositionEnabled && !stack.isEmpty()) {
			// mirror X for the off hand so both items move "outwards"
			float side = hand == InteractionHand.MAIN_HAND ? 1f : -1f;
			pose.translate((float) s.itemX * 0.5f * side, (float) s.itemY * 0.5f, (float) s.itemZ * 0.5f);
			float sc = (float) s.itemScale;
			pose.scale(sc, sc, sc);
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void packmixer$itemEnd(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		pose.popPose();
	}
}
