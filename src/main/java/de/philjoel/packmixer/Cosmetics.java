package de.philjoel.packmixer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Client-side cosmetics for your own player (only you can see them).
 * HEAD / FACE / BACK are 3D item models (assets/testohd_styles/items/hat_*.json),
 * EFFECT is a particle aura (see {@link CosmeticEffects}).
 */
public final class Cosmetics {
	public enum Rarity {
		COMMON(ChatFormatting.GRAY, 0xFFAAAAAA), RARE(ChatFormatting.AQUA, 0xFF55C8FF),
		EPIC(ChatFormatting.LIGHT_PURPLE, 0xFFC77DFF), LEGENDARY(ChatFormatting.GOLD, 0xFFFFC23A);

		public final ChatFormatting format;
		public final int color;

		Rarity(ChatFormatting format, int color) {
			this.format = format;
			this.color = color;
		}
	}

	private static final Map<String, Rarity> RARITY = Map.ofEntries(
			Map.entry("crown", Rarity.LEGENDARY), Map.entry("halo", Rarity.LEGENDARY), Map.entry("angel_wings", Rarity.LEGENDARY),
			Map.entry("devil_wings", Rarity.LEGENDARY), Map.entry("fx_sparkle", Rarity.LEGENDARY), Map.entry("fx_souls", Rarity.LEGENDARY),
			Map.entry("horns", Rarity.EPIC), Map.entry("top_hat", Rarity.EPIC), Map.entry("santa_hat", Rarity.EPIC),
			Map.entry("sunglasses", Rarity.EPIC), Map.entry("fx_flames", Rarity.EPIC), Map.entry("fx_snow", Rarity.EPIC),
			Map.entry("fx_cherry", Rarity.EPIC), Map.entry("fx_enchant", Rarity.EPIC),
			Map.entry("cat_ears", Rarity.RARE), Map.entry("straw_hat", Rarity.RARE), Map.entry("monocle", Rarity.RARE),
			Map.entry("backpack", Rarity.RARE), Map.entry("fx_hearts", Rarity.RARE), Map.entry("fx_notes", Rarity.RARE),
			Map.entry("mask", Rarity.COMMON));

	public static Rarity rarity(String id) {
		return RARITY.getOrDefault(id, Rarity.COMMON);
	}

	public enum Slot {
		HEAD(List.of("crown", "halo", "top_hat", "santa_hat", "horns", "straw_hat", "cat_ears")),
		FACE(List.of("sunglasses", "monocle", "mask")),
		BACK(List.of("angel_wings", "devil_wings", "backpack")),
		EFFECT(List.of("fx_sparkle", "fx_souls", "fx_flames", "fx_snow", "fx_cherry", "fx_enchant", "fx_hearts", "fx_notes"));

		public final List<String> items;

		Slot(List<String> items) {
			this.items = items;
		}

		public String get() {
			PackMixerConfig.Settings s = PackMixerConfig.settings();
			String v = switch (this) {
				case HEAD -> s.hat;
				case FACE -> s.face;
				case BACK -> s.back;
				case EFFECT -> s.effect;
			};
			return v == null ? "" : v;
		}

		public void set(String id) {
			PackMixerConfig.Settings s = PackMixerConfig.settings();
			switch (this) {
				case HEAD -> s.hat = id;
				case FACE -> s.face = id;
				case BACK -> s.back = id;
				case EFFECT -> s.effect = id;
			}
			PackMixerConfig.saveSettings();
		}
	}

	/** Kept for older code: the head items. */
	public static final List<String> HATS = Slot.HEAD.items;

	/** Item used to render a cosmetic; legendary ones get the enchantment shimmer. */
	public static ItemStack hatStack(String id) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "hat_" + id));
		if (rarity(id) == Rarity.LEGENDARY) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	static void init() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticLayer(avatar));
			}
		});
	}

	static final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			PackMixerConfig.Settings s = PackMixerConfig.settings();
			if (!s.cosmeticsEnabled || state.isInvisible) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || state.id != mc.player.getId()) return;
			PlayerModel model = this.getParentModel();
			float t = state.ageInTicks;

			for (Slot slot : new Slot[]{Slot.HEAD, Slot.FACE, Slot.BACK}) {
				String id = slot.get();
				if (id.isEmpty()) continue;
				ItemStackRenderState item = new ItemStackRenderState();
				mc.getItemModelResolver().updateForTopItem(item, hatStack(id), ItemDisplayContext.HEAD, mc.level, null, 0);
				if (item.isEmpty()) continue;

				pose.pushPose();
				model.root().translateAndRotate(pose);
				if (slot == Slot.BACK) {
					model.body.translateAndRotate(pose);
					pose.translate(0.0F, 0.375F, 0.0F);
					pose.mulPose(Axis.YP.rotationDegrees(180.0F));
					pose.scale(0.625F, -0.625F, -0.625F);
					if (id.endsWith("wings")) {
						// gentle wing flap
						float flap = 0.88F + 0.12F * (float) Math.sin(t * 0.25F);
						pose.scale(flap, 1.0F, 1.0F);
					}
				} else {
					model.getHead().translateAndRotate(pose);
					CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
					if (id.equals("halo")) {
						// floating, slowly spinning halo
						pose.translate(0.0F, (float) Math.sin(t * 0.12F) * 0.03F, 0.0F);
						pose.mulPose(Axis.YP.rotationDegrees(t * 3.0F));
					} else if (id.equals("crown")) {
						pose.translate(0.0F, (float) Math.sin(t * 0.1F) * 0.008F, 0.0F);
					}
				}
				item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				pose.popPose();
			}
		}
	}

	private Cosmetics() {
	}
}
