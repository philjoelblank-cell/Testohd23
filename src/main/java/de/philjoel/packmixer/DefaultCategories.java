package de.philjoel.packmixer;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in categories (same order as the icon bar). Written to categories.json on first
 * start so they can be edited; new built-in categories are added to existing files.
 */
final class DefaultCategories {
	static final String FULL_PACK = "full_pack";

	static List<Category> create() {
		List<Category> l = new ArrayList<>();
		// whole Testohd packs at once (only the V1 packs are listed there)
		l.add(new Category(FULL_PACK, "Komplette Packs",
				List.of("textures/item/end_crystal.png", "textures/item/diamond_sword.png"),
				List.of("textures/item/end_crystal.png")));
		l.add(block("obsidian", "Obsidian", "obsidian"));
		l.add(new Category("end_crystal", "Endkristalle",
				List.of("textures/item/end_crystal.png", "textures/entity/end_crystal/end_crystal.png"),
				List.of("textures/item/end_crystal.png", "textures/entity/end_crystal/*.png",
						"models/item/end_crystal.json", "items/end_crystal.json")));
		l.add(new Category("explosion", "Explosionen",
				List.of("textures/particle/explosion_6.png", "textures/particle/explosion_3.png"),
				List.of("textures/particle/explosion_*.png", "particles/explosion.json")));
		l.add(new Category("respawn_anchor", "Seelenanker",
				List.of("textures/block/respawn_anchor_top.png", "textures/block/respawn_anchor_side4.png"),
				List.of("textures/block/respawn_anchor_*.png", "models/block/respawn_anchor_*.json",
						"items/respawn_anchor.json"))
				.cube("textures/block/respawn_anchor_top.png", "textures/block/respawn_anchor_side4.png", null));
		l.add(block("glowstone", "Glowstone", "glowstone"));
		l.add(new Category("grass_block", "Grasblöcke",
				List.of("textures/block/grass_block_side.png"),
				List.of("textures/block/grass_block_top.png", "textures/block/grass_block_side.png",
						"textures/block/grass_block_side_overlay.png", "textures/block/grass_block_snow.png",
						"textures/block/dirt.png", "models/block/grass_block*.json", "items/grass_block.json"))
				.cube("textures/block/grass_block_top.png", "textures/block/grass_block_side.png", "#7CBD6B"));
		l.add(item("totem", "Totems", "totem_of_undying"));
		l.add(item("ender_pearl", "Enderperlen", "ender_pearl"));
		l.add(new Category("potions", "Tränke",
				List.of("textures/item/splash_potion.png", "textures/item/potion.png"),
				List.of("textures/item/potion.png", "textures/item/potion_overlay.png",
						"textures/item/splash_potion.png", "textures/item/lingering_potion.png")));
		l.add(new Category("golden_apple", "Goldene Äpfel",
				List.of("textures/item/golden_apple.png"),
				List.of("textures/item/golden_apple.png",
						"models/item/golden_apple.json", "models/item/enchanted_golden_apple.json",
						"items/golden_apple.json", "items/enchanted_golden_apple.json")));
		l.add(tool("swords", "Schwerter", "sword"));
		l.add(tool("axes", "Äxte", "axe"));
		l.add(tool("pickaxes", "Spitzhacken", "pickaxe"));
		l.add(tool("shovels", "Schaufeln", "shovel"));
		l.add(new Category("mace", "Streitkolben",
				List.of("textures/item/mace.png"),
				List.of("textures/item/mace.png", "models/item/mace.json", "models/item/handheld_mace.json",
						"items/mace.json")));
		l.add(new Category("spear", "Speere",
				List.of("textures/item/netherite_spear.png", "textures/item/diamond_spear.png"),
				List.of("textures/item/*_spear.png", "textures/item/*_spear_in_hand.png", "models/item/*_spear*.json",
						"items/*_spear.json")));
		l.add(new Category("bow", "Bögen",
				List.of("textures/item/bow.png"),
				List.of("textures/item/bow.png", "textures/item/bow_pulling_*.png",
						"models/item/bow.json", "models/item/bow_pulling_*.json", "items/bow.json")));
		l.add(new Category("wind_charge", "Windkugeln",
				List.of("textures/item/wind_charge.png"),
				List.of("textures/item/wind_charge.png", "textures/entity/projectiles/wind_charge.png",
						"models/item/wind_charge.json", "items/wind_charge.json")));
		l.add(new Category("cobweb", "Spinnweben",
				List.of("textures/block/cobweb.png"),
				List.of("textures/block/cobweb.png", "models/block/cobweb.json", "models/item/cobweb.json",
						"items/cobweb.json")));
		l.add(new Category("armor", "Rüstung",
				List.of("textures/item/netherite_chestplate.png", "textures/item/diamond_chestplate.png"),
				List.of("textures/item/*_helmet.png", "textures/item/*_chestplate.png",
						"textures/item/*_leggings.png", "textures/item/*_boots.png",
						"textures/entity/equipment/humanoid/*.png",
						"textures/entity/equipment/humanoid_leggings/*.png")));
		l.add(new Category("elytra", "Elytren",
				List.of("textures/item/elytra.png"),
				List.of("textures/item/elytra*.png", "textures/entity/equipment/wings/elytra.png",
						"models/item/elytra*.json", "items/elytra.json")));
		l.add(new Category("enchant_glint", "Verzauberungsglanz",
				List.of("textures/misc/enchanted_glint_item.png"),
				List.of("textures/misc/enchanted_glint_item.png", "textures/misc/enchanted_glint_armor.png")));
		l.add(new Category("chest", "Truhen",
				List.of("textures/entity/chest/normal.png"),
				List.of("textures/entity/chest/*.png")));
		l.add(new Category("inventory", "Inventar",
				List.of("textures/gui/container/inventory.png"),
				List.of("textures/gui/container/inventory.png")));
		l.add(new Category("sky", "Himmel",
				List.of("optifine/sky/world0/sky1.png", "optifine/sky/world0/sky2.png", "textures/environment/celestial/sun.png",
						"textures/environment/clouds.png"),
				List.of("textures/environment/**.png", "optifine/sky/**", "mcpatcher/sky/**")));
		l.add(new Category("crosshair", "Fadenkreuz",
				List.of("textures/gui/sprites/hud/crosshair.png"),
				List.of("textures/gui/sprites/hud/crosshair.png",
						"textures/gui/sprites/hud/crosshair_attack_indicator_*.png")));
		// ---- extra categories (not in the video)
		l.add(new Category("shield", "Schilde",
				List.of("textures/entity/shield_base_nopattern.png", "textures/entity/shield/base_nopattern.png",
						"textures/entity/shield_base.png", "textures/entity/shield/base.png"),
				List.of("textures/entity/shield/**.png", "textures/entity/shield_base*.png",
						"models/item/shield*.json", "items/shield.json")));
		l.add(new Category("crossbow", "Armbrüste",
				List.of("textures/item/crossbow_standby.png"),
				List.of("textures/item/crossbow_*.png", "models/item/crossbow*.json", "items/crossbow.json")));
		l.add(new Category("trident", "Dreizacke",
				List.of("textures/item/trident.png"),
				List.of("textures/item/trident.png", "textures/entity/trident*.png",
						"models/item/trident*.json", "items/trident.json")));
		l.add(item("snowball", "Schneebälle", "snowball"));
		l.add(item("experience_bottle", "XP-Flaschen", "experience_bottle"));
		l.add(new Category("fire", "Feuer",
				List.of("textures/block/fire_0.png"),
				List.of("textures/block/fire_0.png", "textures/block/fire_1.png")));
		l.add(new Category("hotbar", "Hotbar",
				List.of("textures/gui/sprites/hud/hotbar_selection.png", "textures/gui/sprites/hud/hotbar.png"),
				List.of("textures/gui/sprites/hud/hotbar*.png")));
		l.add(new Category("hearts", "Herzen",
				List.of("textures/gui/sprites/hud/heart/full.png"),
				List.of("textures/gui/sprites/hud/heart/*.png")));
		l.add(new Category("hunger", "Hungerleiste",
				List.of("textures/gui/sprites/hud/food_full.png"),
				List.of("textures/gui/sprites/hud/food_*.png")));
		return l;
	}

	private static Category item(String id, String name, String item) {
		return new Category(id, name,
				List.of("textures/item/" + item + ".png"),
				List.of("textures/item/" + item + ".png", "models/item/" + item + ".json", "items/" + item + ".json"));
	}

	private static Category block(String id, String name, String block) {
		String tex = "textures/block/" + block + ".png";
		return new Category(id, name,
				List.of(tex),
				List.of(tex, "models/block/" + block + ".json", "models/item/" + block + ".json", "items/" + block + ".json"))
				.cube(tex, tex, null);
	}

	private static Category tool(String id, String name, String type) {
		return new Category(id, name,
				List.of("textures/item/netherite_" + type + ".png", "textures/item/diamond_" + type + ".png"),
				List.of("textures/item/*_" + type + ".png", "models/item/*_" + type + ".json", "items/*_" + type + ".json"));
	}

	private DefaultCategories() {
	}
}
