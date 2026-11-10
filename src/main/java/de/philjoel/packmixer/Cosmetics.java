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
 * Client-side cosmetics. Your own come from the settings; other players who also use the mod are shown with
 * theirs (shared through {@link CosmeticSync}).
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
			Map.entry("mask", Rarity.COMMON),
			Map.entry("wizard_hat", Rarity.EPIC), Map.entry("headphones", Rarity.RARE), Map.entry("viking_helmet", Rarity.EPIC),
			Map.entry("phoenix_wings", Rarity.LEGENDARY),
			Map.entry("fx_rainbow", Rarity.LEGENDARY), Map.entry("fx_galaxy", Rarity.LEGENDARY), Map.entry("fx_totem", Rarity.LEGENDARY),
			Map.entry("fx_lightwings", Rarity.LEGENDARY), Map.entry("fx_lightning", Rarity.EPIC), Map.entry("fx_void", Rarity.EPIC),
			Map.entry("plasma_wings", Rarity.LEGENDARY), Map.entry("inferno_wings", Rarity.LEGENDARY), Map.entry("void_wings", Rarity.LEGENDARY),
			Map.entry("energy_wings", Rarity.LEGENDARY), Map.entry("frost_wings", Rarity.LEGENDARY),
			Map.entry("fx_inferno", Rarity.LEGENDARY), Map.entry("fx_plasma", Rarity.LEGENDARY), Map.entry("fx_frost", Rarity.EPIC),
			Map.entry("fx_energy", Rarity.EPIC),
			Map.entry("inferno_crown", Rarity.LEGENDARY), Map.entry("plasma_crown", Rarity.LEGENDARY), Map.entry("frost_crown", Rarity.LEGENDARY),
			Map.entry("void_crown", Rarity.LEGENDARY), Map.entry("energy_halo", Rarity.LEGENDARY), Map.entry("void_halo", Rarity.LEGENDARY),
			Map.entry("divine_halo", Rarity.LEGENDARY),
			Map.entry("cape_plasma", Rarity.LEGENDARY), Map.entry("cape_inferno", Rarity.LEGENDARY), Map.entry("cape_void", Rarity.LEGENDARY),
			Map.entry("cape_galaxy", Rarity.LEGENDARY), Map.entry("cape_rainbow", Rarity.LEGENDARY), Map.entry("cape_thunder", Rarity.LEGENDARY),
			Map.entry("cape_holy", Rarity.EPIC), Map.entry("cape_energy", Rarity.EPIC), Map.entry("cape_frost", Rarity.EPIC),
			Map.entry("cape_matrix", Rarity.EPIC), Map.entry("cape_sakura", Rarity.EPIC), Map.entry("cape_ocean", Rarity.EPIC),
			Map.entry("fx_thunder", Rarity.LEGENDARY), Map.entry("fx_blackhole", Rarity.LEGENDARY), Map.entry("fx_holo", Rarity.LEGENDARY),
			Map.entry("thunder_wings", Rarity.LEGENDARY), Map.entry("galaxy_wings", Rarity.LEGENDARY), Map.entry("holo_wings", Rarity.LEGENDARY),
			Map.entry("toxic_wings", Rarity.LEGENDARY),
			Map.entry("space_helmet", Rarity.LEGENDARY), Map.entry("dragon_horns", Rarity.LEGENDARY), Map.entry("fox_beanie", Rarity.EPIC),
			Map.entry("propeller_cap", Rarity.EPIC), Map.entry("cowboy_hat", Rarity.RARE), Map.entry("chef_hat", Rarity.RARE),
			Map.entry("neon_visor", Rarity.EPIC),
			Map.entry("trail_plasma", Rarity.LEGENDARY), Map.entry("trail_inferno", Rarity.LEGENDARY), Map.entry("trail_frost", Rarity.LEGENDARY),
			Map.entry("trail_void", Rarity.LEGENDARY), Map.entry("trail_yinyang", Rarity.LEGENDARY), Map.entry("trail_rainbow", Rarity.LEGENDARY),
			Map.entry("orb_ghosts", Rarity.LEGENDARY), Map.entry("orb_crystals", Rarity.LEGENDARY), Map.entry("orb_rings", Rarity.LEGENDARY),
			Map.entry("orb_wisps", Rarity.EPIC), Map.entry("orb_hearts", Rarity.EPIC),
			Map.entry("fx_toxic", Rarity.EPIC), Map.entry("fx_matrix", Rarity.EPIC), Map.entry("fx_heartbeat", Rarity.EPIC));

	/** Textured, glowing cosmetics: no enchantment shimmer on top. */
	static final java.util.Set<String> TEXTURED = java.util.Set.of("inferno_crown", "plasma_crown", "frost_crown", "void_crown",
			"energy_halo", "void_halo", "divine_halo");

	/** Big textured wings (left + right model, beating for real). */
	static final java.util.Set<String> WINGS = java.util.Set.of("angel_wings", "devil_wings", "phoenix_wings", "plasma_wings",
			"inferno_wings", "void_wings", "energy_wings", "frost_wings", "thunder_wings", "galaxy_wings", "toxic_wings", "holo_wings");

	public static Rarity rarity(String id) {
		return RARITY.getOrDefault(id, Rarity.COMMON);
	}

	public enum Slot {
		HEAD(List.of("space_helmet", "dragon_horns", "fox_beanie", "propeller_cap", "cowboy_hat", "chef_hat", "inferno_crown", "plasma_crown", "frost_crown", "void_crown", "energy_halo", "void_halo", "divine_halo", "crown", "halo", "wizard_hat", "viking_helmet", "top_hat", "santa_hat", "horns", "headphones", "straw_hat", "cat_ears")),
		FACE(List.of("neon_visor", "sunglasses", "monocle", "mask")),
		BACK(List.of("thunder_wings", "galaxy_wings", "holo_wings", "toxic_wings", "plasma_wings", "inferno_wings", "void_wings", "energy_wings", "frost_wings", "phoenix_wings", "angel_wings",
				"devil_wings", "backpack")),
		EFFECT(List.of("trail_plasma", "trail_inferno", "trail_frost", "trail_void", "trail_yinyang", "trail_rainbow", "orb_ghosts", "orb_crystals", "orb_rings", "orb_wisps", "orb_hearts", "fx_thunder", "fx_blackhole", "fx_holo", "fx_toxic", "fx_matrix", "fx_heartbeat", "fx_inferno", "fx_plasma", "fx_frost", "fx_energy", "fx_lightwings", "fx_rainbow", "fx_galaxy", "fx_totem", "fx_sparkle", "fx_souls", "fx_lightning", "fx_void",
				"fx_flames", "fx_snow", "fx_cherry", "fx_enchant", "fx_hearts", "fx_notes")),
		CAPE(List.of("cape_plasma", "cape_inferno", "cape_void", "cape_energy", "cape_frost", "cape_holy", "cape_galaxy", "cape_rainbow", "cape_thunder", "cape_matrix", "cape_sakura", "cape_ocean"));

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
				case CAPE -> s.cape;
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
				case CAPE -> s.cape = id;
			}
			PackMixerConfig.saveSettings();
			CosmeticSync.publishSoon();
		}
	}

	/** What one player wears. */
	public record Loadout(String head, String face, String back, String effect, String cape) {
		public static final Loadout NONE = new Loadout("", "", "", "", "");

		public String get(Slot slot) {
			return switch (slot) {
				case HEAD -> head;
				case FACE -> face;
				case BACK -> back;
				case EFFECT -> effect;
				case CAPE -> cape;
			};
		}

		public boolean isEmpty() {
			return head.isEmpty() && face.isEmpty() && back.isEmpty() && effect.isEmpty() && cape.isEmpty();
		}
	}

	/** Your own cosmetics. */
	public static Loadout own() {
		if (!PackMixerConfig.settings().cosmeticsEnabled) return Loadout.NONE;
		return new Loadout(Slot.HEAD.get(), Slot.FACE.get(), Slot.BACK.get(), Slot.EFFECT.get(), Slot.CAPE.get());
	}

	/** Cosmetics of any player: yours from the settings, other mod users' from {@link CosmeticSync}. */
	public static Loadout of(java.util.UUID uuid) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getUUID().equals(uuid)) return own();
		if (!PackMixerConfig.settings().showOthersCosmetics) return Loadout.NONE;
		return CosmeticSync.get(uuid);
	}

	public static boolean valid(Slot slot, String id) {
		return id.isEmpty() || slot.items.contains(id);
	}

	/** Kept for older code: the head items. */
	public static final List<String> HATS = Slot.HEAD.items;

	/** Item used to render a cosmetic; legendary ones get the enchantment shimmer. */
	public static ItemStack hatStack(String id) {
		if (id.startsWith("cape_")) return ItemStack.EMPTY; // capes: texture on the player, picture in menus
		ItemStack stack = Ui.stack(Items.PAPER);
		if (stack.isEmpty()) return stack;
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "hat_" + id));
		if (rarity(id) == Rarity.LEGENDARY && !TEXTURED.contains(id) && !WINGS.contains(id.replace("_l", "").replace("_r", "")))
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Card preview: the item on a small dark bust (models/item/card_*.json). */
	public static ItemStack cardStack(String id) {
		if (id.startsWith("cape_")) return ItemStack.EMPTY;
		ItemStack stack = Ui.stack(Items.PAPER);
		if (stack.isEmpty()) return stack;
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "card_" + id));
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
			if (state.isInvisible) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			java.util.UUID uuid;
			if (Emotes.preview(state.id) != null) { // doll in the emote wheel = you
				if (mc.player == null) return;
				uuid = mc.player.getUUID();
			} else {
				net.minecraft.world.entity.Entity entity = mc.level.getEntity(state.id);
				if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return;
				uuid = player.getUUID();
			}
			PlayerModel model = this.getParentModel();
			float[] ball = Emotes.ball(state.id, uuid);
			if (ball != null) { // football / basketball emote: a big ball
				ItemStackRenderState item = prop(mc, ball[3] == 0 ? "ball_football" : "ball_basketball");
				if (item != null) {
					pose.pushPose();
					model.root().translateAndRotate(pose);
					pose.translate(ball[0], ball[1], ball[2]);
					pose.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * 25));
					pose.scale(1.4F, 1.4F, 1.4F);
					item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
					pose.popPose();
				}
			}
			String held = Emotes.prop(state.id, uuid);
			if (held != null) { // referee card in the right hand
				ItemStackRenderState item = prop(mc, held);
				if (item != null) {
					pose.pushPose();
					model.root().translateAndRotate(pose);
					model.rightArm.translateAndRotate(pose);
					pose.translate(-0.0625F, 0.72F, -0.02F);
					pose.scale(0.9F, 0.9F, 0.9F);
					item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
					pose.popPose();
				}
			}
			Loadout loadout = of(uuid);
			if (loadout.isEmpty()) return;
			float t = state.ageInTicks;
			if (Auras3D.handles(loadout.effect())) Auras3D.render(pose, collector, state, model, mc, loadout.effect(), t);

			String back = loadout.back();
			if (WINGS.contains(back)) wings(pose, collector, light, state, model, mc, back, t);
			for (Slot slot : new Slot[]{Slot.HEAD, Slot.FACE, Slot.BACK}) {
				String id = loadout.get(slot);
				if (id.isEmpty() || WINGS.contains(id)) continue;
				ItemStackRenderState item = new ItemStackRenderState();
				mc.getItemModelResolver().updateForTopItem(item, hatStack(id), ItemDisplayContext.HEAD, mc.level, null, 0);
				if (item.isEmpty()) continue;

				pose.pushPose();
				model.root().translateAndRotate(pose);
				if (slot == Slot.BACK) {
					model.body.translateAndRotate(pose);
					// a bit behind the back, so a chestplate never covers the wings
					pose.translate(0.0F, 0.375F, id.endsWith("wings") ? 0.11F : 0.0F);
					pose.mulPose(Axis.YP.rotationDegrees(180.0F));
					pose.scale(0.625F, -0.625F, -0.625F);
					if (id.endsWith("wings")) {
						// wing flap (phoenix wings beat faster)
						float speed = id.equals("phoenix_wings") ? 0.4F : 0.25F;
						float flap = 0.86F + 0.14F * (float) Math.sin(t * speed);
						pose.scale(flap, 1.0F, 1.0F);
					}
				} else {
					model.getHead().translateAndRotate(pose);
					CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
					if (id.endsWith("_halo")) { // V1 halos: float higher and turn faster
						pose.translate(0.0F, (float) Math.sin(t * 0.14F) * 0.04F + 0.03F, 0.0F);
						pose.mulPose(Axis.YP.rotationDegrees(t * 4.5F));
					} else if (id.endsWith("_crown")) {
						pose.translate(0.0F, (float) Math.sin(t * 0.1F) * 0.012F, 0.0F);
					} else if (id.equals("halo")) {
						// floating, slowly spinning halo
						pose.translate(0.0F, (float) Math.sin(t * 0.12F) * 0.03F, 0.0F);
						pose.mulPose(Axis.YP.rotationDegrees(t * 3.0F));
					} else if (id.equals("crown")) {
						pose.translate(0.0F, (float) Math.sin(t * 0.1F) * 0.008F, 0.0F);
					} else if (id.equals("wizard_hat")) {
						pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.08F) * 2.5F));
					}
				}
				item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				pose.popPose();
			}
		}
	}

	/** Item model of a prop (ball, card), or null. */
	private static ItemStackRenderState prop(Minecraft mc, String model) {
		ItemStack bs = Ui.stack(Items.PAPER);
		if (bs.isEmpty()) return null;
		bs.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, model));
		ItemStackRenderState item = new ItemStackRenderState();
		mc.getItemModelResolver().updateForTopItem(item, bs, ItemDisplayContext.FIXED, mc.level, null, 0);
		return item.isEmpty() ? null : item;
	}

	/** Left and right wing, each turning around the spine: they beat open and closed. */
	private static void wings(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel model,
			Minecraft mc, String id, float t) {
		float speed = id.equals("phoenix_wings") || id.equals("inferno_wings") || id.equals("plasma_wings") ? 0.32F : 0.22F;
		float beat = (float) Math.sin(t * speed);
		boolean flying = state.isFallFlying || state.isCrouching;
		float open = flying ? 8F : 24F + 16F * beat;   // degrees backwards from flat
		for (int side = -1; side <= 1; side += 2) {
			ItemStackRenderState item = new ItemStackRenderState();
			mc.getItemModelResolver().updateForTopItem(item, hatStack(id + (side < 0 ? "_l" : "_r")), ItemDisplayContext.HEAD, mc.level, null, 0);
			if (item.isEmpty()) continue;
			pose.pushPose();
			model.root().translateAndRotate(pose);
			model.body.translateAndRotate(pose);
			// wing roots on the shoulder blades, just behind the back (also behind a chestplate)
			pose.translate(0.0F, 0.6F, 0.2F);
			pose.mulPose(Axis.YP.rotationDegrees(180.0F));
			pose.scale(0.62F, -0.62F, -0.62F);
			// open backwards (away from the body): with the flips above the angle has to be negative,
			// otherwise the wings fold forward through the body
			pose.mulPose(Axis.YP.rotationDegrees(-side * open));
			item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			pose.popPose();
		}
	}

	/** Animated HD capes: 32 frames (textures/cape/<name>_<frame>.png), one per tick. */
	static final int CAPE_FRAMES = 32;

	public static Identifier capeTexture(String id, long gameTime) {
		int frame = (int) Math.floorMod(gameTime, CAPE_FRAMES);
		return Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "textures/cape/" + id.substring(5) + "_" + frame + ".png");
	}

	private Cosmetics() {
	}
}
