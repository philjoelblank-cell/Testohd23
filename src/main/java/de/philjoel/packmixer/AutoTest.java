package de.philjoel.packmixer;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Automatic screenshot test, only active with {@code -Dtestohd.autotest=true} (used by the GitHub build to check
 * the menus at every GUI scale and the packs / skies / cosmetics in a real world). Does nothing for players.
 */
final class AutoTest {
	static final boolean ENABLED = Boolean.getBoolean("testohd.autotest");

	private record Step(String name, BooleanSupplier ready, Runnable action, int delay) {
	}

	private static final Deque<Step> steps = new ArrayDeque<>();
	private static int waitTicks;
	private static int timeout;
	private static boolean started;

	private static void step(String name, int wait, Runnable action) {
		steps.add(new Step(name, () -> true, action, wait));
	}

	private static void when(String name, BooleanSupplier ready, Runnable action) {
		steps.add(new Step(name, ready, action, 5));
	}

	private static void shot(String name) {
		step("shot " + name, 3, () -> Screenshot.grab(Minecraft.getInstance().gameDirectory, name + ".png",
				Minecraft.getInstance().getMainRenderTarget(), 1, c -> { }));
	}

	private static void command(String cmd) {
		step("cmd " + cmd, 2, () -> {
			MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
			if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
		});
	}

	static void memNow(String label) {
		System.gc();
		System.gc();
		Runtime rt = Runtime.getRuntime();
		long rss = -1;
		try {
			for (String l : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/status")))
				if (l.startsWith("VmRSS:")) rss = Long.parseLong(l.replaceAll("[^0-9]", "")) / 1024;
		} catch (Exception ignored) {
		}
		PackMixerClient.LOGGER.info("autotest: MEM {} heap={}MB rss={}MB previews={}/{}MB", label,
				(rt.totalMemory() - rt.freeMemory()) >> 20, rss, PreviewCache.liveTextures, PreviewCache.liveBytes >> 20);
	}

	private static void mem(String label) {
		step("mem " + label, 20, () -> memNow(label));
	}

	private static void usePack(Minecraft mc, String pk) {
		step("pack " + pk, 5, () -> {
			Map<String, String> sel = new LinkedHashMap<>();
			if (pk != null) for (Category c : PackMixerConfig.loadCategories()) sel.put(c.id, pk);
			PackMixerConfig.saveSelection(sel);
			PackBuilder.rebuildAndApply();
		});
		step("wait " + pk, 40, () -> { });
		when("reloaded " + pk, () -> mc.getOverlay() == null, () -> { });
		step("look " + pk, 100, () -> { });
		mem("world pack " + pk);
	}

	private static void menuTour(Minecraft mc, String label) {
		step("open menu", 30, () -> mc.setScreen(new PackMixerScreen(null)));
		int n = PackMixerConfig.loadCategories().size();
		for (int i = 0; i < n; i++) {
			int index = i;
			step("category " + i, 12, () -> {
				if (mc.screen instanceof PackMixerScreen s) s.testSelect(index);
			});
		}
		mem(label + " menu open, all categories seen");
		step("close menu", 20, () -> mc.setScreen(null));
		mem(label + " menu closed");
	}

	private static void memPlan(Minecraft mc) {
		when("title", () -> mc.getOverlay() == null && mc.screen != null, () -> { });
		step("settle title", 200, () -> { });
		mem("title, nothing generated yet");
		step("settle title", 200, () -> { });
		mem("title, nothing generated yet (again)");
		step("generate", 5, () -> BuiltinPacks.generateIfNeeded(() -> { }));
		when("generated", () -> PackMixerConfig.settings().builtinPacksVersion >= BuiltinPacks.VERSION, () -> { });
		mem("title, packs generated");
		step("settle title", 400, () -> { });
		mem("title, 20s after generation");
		step("create world", 40, () -> CreateWorldScreen.openFresh(mc, () -> { }));
		when("create screen", () -> mc.screen instanceof CreateWorldScreen, () -> {
			try {
				var m = CreateWorldScreen.class.getDeclaredMethod("onCreate");
				m.setAccessible(true);
				m.invoke(mc.screen);
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("autotest: could not create world", e);
			}
		});
		when("in world", () -> mc.player != null && mc.level != null && mc.screen == null, () -> mc.options.renderDistance().set(4));
		step("settle", 300, () -> { });
		mem("world, no pack");
		step("settle", 200, () -> { });
		mem("world, no pack (again)");
		for (int i = 1; i <= 5; i++) { // control: plain reloads without any pack
			step("plain reload " + i, 5, () -> mc.reloadResourcePacks());
			step("wait", 40, () -> { });
			when("reloaded", () -> mc.getOverlay() == null, () -> { });
			step("look", 100, () -> { });
			mem("plain reload " + i + " (no pack)");
		}
		menuTour(mc, "1st");
		for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
			step("cosmetics " + slot, 20, () -> {
				CosmeticsScreen.lastSlot = slot;
				mc.setScreen(new CosmeticsScreen(null));
			});
		}
		mem("cosmetics menu, all tabs seen");
		step("close", 20, () -> mc.setScreen(null));
		mem("cosmetics menu closed");
		usePack(mc, "Testohd23 V3 Aurora");
		usePack(mc, "Testohd23 V1 Purple");
		usePack(mc, "Testohd23 V2 Navy");
		usePack(mc, "Galaxie");
		usePack(mc, "Testohd23 V3 Aurora");
		usePack(mc, "Testohd23 V1 Purple");
		usePack(mc, "Testohd23 V3 Aurora");
		usePack(mc, "Testohd23 V1 Purple");
		usePack(mc, null);
		step("cosmetics on", 5, () -> {
			Cosmetics.Slot.HEAD.set("crown");
			Cosmetics.Slot.BACK.set("galaxy_wings");
			Cosmetics.Slot.CAPE.set("cape_galaxy");
			Cosmetics.Slot.EFFECT.set("trail_plasma");
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		step("wear", 200, () -> { });
		mem("world, cosmetics worn");
		for (String cape : new String[]{"cape_plasma", "cape_inferno", "cape_void", "cape_energy", "cape_frost", "cape_holy"}) step("cape " + cape, 30, () -> Cosmetics.Slot.CAPE.set(cape));
		mem("world, 7 capes worn one after another");
		step("idle", 1200, () -> { });
		mem("world, 1 minute later");
		step("quit", 40, () -> {
			mc.setScreen(null);
			mc.stop();
		});
	}

	private static void plan(Minecraft mc) {
		if (true) {
			memPlan(mc);
			return;
		}
		when("game ready", () -> mc.getOverlay() == null && mc.screen != null
				&& PackMixerConfig.settings().builtinPacksVersion >= BuiltinPacks.VERSION, () -> { });
		for (int scale : new int[]{2, 3, 4}) {
			step("gui scale " + scale, 5, () -> {
				mc.options.guiScale().set(scale);
				mc.resizeDisplay();
			});
			step("open menu", 30, () -> mc.setScreen(new PackMixerScreen(null)));
			int n = PackMixerConfig.loadCategories().size();
			for (int i = 0; i < n; i++) {
				int index = i;
				step("category " + i, 25, () -> {
					if (mc.screen instanceof PackMixerScreen s) s.testSelect(index);
				});
				shot("menu_s" + scale + "_" + String.format("%02d", i));
				if (scale != 3) break; // all categories once, the other scales only the first one
			}
			for (String entry : new String[]{"item_position", "packmixer", "tt", "background"}) {
				step("settings " + entry, 20, () -> {
					PackMixerSettingsScreen.lastSelected = entry;
					mc.setScreen(new PackMixerSettingsScreen(null));
				});
				shot("settings_s" + scale + "_" + entry);
				if (entry.equals("background") && scale == 3) {
					step("bg sunset", 10, () -> {
						PackMixerConfig.settings().menuBackground = "aurora";
						mc.setScreen(new PackMixerScreen(null));
					});
					shot("bg_aurora_menu");
					step("bg back", 2, () -> PackMixerConfig.settings().menuBackground = "purple");
				}
				if (scale != 3) break;
			}
			if (scale == 3) {
				step("changelog", 20, () -> mc.setScreen(new ChangelogScreen(null, "1.6.0")));
				shot("changelog_s3");
			}
			for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
				step("cosmetics " + slot, 20, () -> {
					CosmeticsScreen.lastSlot = slot;
					mc.setScreen(new CosmeticsScreen(null));
				});
				shot("cosmetics_s" + scale + "_" + slot.name().toLowerCase());
				if (scale != 3) break;
			}
		}
		// ---- in a world: one built-in pack for everything + cosmetics
		step("gui scale back", 5, () -> {
			mc.options.guiScale().set(0);
			mc.resizeDisplay();
			mc.setScreen(null);
		});
		step("select pack", 5, () -> {
			Map<String, String> sel = new LinkedHashMap<>();
			for (Category c : PackMixerConfig.loadCategories()) sel.put(c.id, c.id.equals("sky") ? "Verlauf Sonnenuntergang"
					: c.id.equals("mace") || c.id.equals("spear") || c.id.equals("armor") ? "Energie Schwarz" : "Galaxie");
			PackMixerConfig.saveSelection(sel);
			PackBuilder.rebuildAndApply();
		});
		when("reload done", () -> mc.getOverlay() == null, () -> { });
		step("create world", 40, () -> CreateWorldScreen.openFresh(mc, () -> { }));
		when("create screen", () -> mc.screen instanceof CreateWorldScreen, () -> {
			try {
				var m = CreateWorldScreen.class.getDeclaredMethod("onCreate");
				m.setAccessible(true);
				m.invoke(mc.screen);
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("autotest: could not create world", e);
			}
		});
		when("in world", () -> mc.player != null && mc.level != null && mc.screen == null, () -> {
			mc.options.renderDistance().set(4);
			Cosmetics.Slot.HEAD.set("crown");
			Cosmetics.Slot.BACK.set("phoenix_wings");
			Cosmetics.Slot.EFFECT.set("fx_lightwings");
		});
		step("settle", 200, () -> { });
		// regression: left click on a pack card must choose it (a player could only add favourites)
		step("click test open", 20, () -> {
			mc.setScreen(new PackMixerScreen(null));
		});
		step("click test cat", 20, () -> {
			if (mc.screen instanceof PackMixerScreen s) s.testSelect(2);
		});
		step("click left", 10, () -> {
			if (mc.screen instanceof PackMixerScreen s) PackMixerClient.LOGGER.info("autotest: LEFT {}", s.testClick(2, 0));
		});
		step("click right", 10, () -> {
			if (mc.screen instanceof PackMixerScreen s) PackMixerClient.LOGGER.info("autotest: RIGHT {}", s.testClick(3, 1));
		});
		shot("click_test");
		step("click close", 5, () -> mc.setScreen(null));
		// regression: switching whole packs (White -> Purple crashed for a player)
		for (String pk : new String[]{"Testohd23 V1 White", "Testohd23 V1 Purple", "Testohd23 V1 White", "Testohd23 V1 Purple"}) {
			step("switch " + pk, 5, () -> {
				Map<String, String> sel = new LinkedHashMap<>();
				for (Category c : PackMixerConfig.loadCategories()) sel.put(c.id, pk);
				PackMixerConfig.saveSelection(sel);
				mc.setScreen(new PackMixerScreen(null));
			});
			step("close " + pk, 10, () -> {
				if (mc.screen instanceof PackMixerScreen s) s.testApply();
			});
			when("reloaded " + pk, () -> mc.getOverlay() == null, () -> { });
			step("look " + pk, 60, () -> { });
			shot("switch_" + pk.replace(" ", "_"));
		}
		// ---- new cosmetics: wings from fixed angles (with chestplate), capes, emotes, auras, low shield
		command("item replace entity @p armor.chest with netherite_chestplate");
		for (String wing : new String[]{"thunder_wings", "galaxy_wings", "toxic_wings", "holo_wings", "angel_wings"}) {
			step("wear " + wing, 10, () -> {
				Cosmetics.Slot.HEAD.set("");
				Cosmetics.Slot.CAPE.set("");
				Cosmetics.Slot.EFFECT.set("");
				Cosmetics.Slot.BACK.set(wing);
				mc.setScreen(new TestViewScreen(0, 60, 120, 180));
			});
			shot("views_" + wing);
		}
		for (String cape : Cosmetics.Slot.CAPE.items) {
			step("wear " + cape, 10, () -> {
				Cosmetics.Slot.BACK.set("");
				Cosmetics.Slot.CAPE.set(cape);
				mc.setScreen(new TestViewScreen(0, 60, 120, 180));
			});
			step("cape load " + cape, 10, () -> { });
			shot("views_" + cape);
		}
		for (String hat : new String[]{"space_helmet", "dragon_horns", "fox_beanie", "propeller_cap", "cowboy_hat", "chef_hat"}) {
			step("wear " + hat, 10, () -> {
				Cosmetics.Slot.CAPE.set("");
				Cosmetics.Slot.HEAD.set(hat);
				Cosmetics.Slot.FACE.set(hat.equals("space_helmet") ? "" : "neon_visor");
				mc.setScreen(new TestViewScreen(0, 45, 90, 180));
			});
			shot("views_" + hat);
		}
		step("hats off", 2, () -> {
			Cosmetics.Slot.HEAD.set("");
			Cosmetics.Slot.FACE.set("");
		});
		step("cape + wings", 10, () -> {
			Cosmetics.Slot.CAPE.set("cape_galaxy");
			Cosmetics.Slot.BACK.set("galaxy_wings");
			mc.setScreen(new TestViewScreen(0, 60, 120, 180));
		});
		shot("views_cape_wings");
		command("item replace entity @p armor.head with diamond_helmet");
		for (String hat : new String[]{"cowboy_hat", "crown", "cat_ears"}) {
			step("helmet + " + hat, 10, () -> {
				Cosmetics.Slot.BACK.set("");
				Cosmetics.Slot.HEAD.set(hat);
				Cosmetics.Slot.FACE.set("neon_visor");
				mc.setScreen(new TestViewScreen(0, 45, 90, 180));
			});
			shot("views_helmet_" + hat);
		}
		command("item replace entity @p armor.head with air");
		step("helmet hats off", 2, () -> {
			Cosmetics.Slot.HEAD.set("");
			Cosmetics.Slot.FACE.set("");
		});
		step("emotes start", 5, () -> {
			Cosmetics.Slot.BACK.set("");
			Cosmetics.Slot.CAPE.set("cape_rainbow");
			mc.setScreen(new EmoteWheelScreen());
		});
		shot("emote_wheel");
		for (int pg = 1; pg < 4; pg++) {
			int page = pg;
			step("emote wheel page " + page, 30, () -> {
				EmoteWheelScreen.testPage(page);
				mc.setScreen(new EmoteWheelScreen());
			});
			shot("emote_wheel_" + (page + 1));
		}
		for (Emotes.Emote e : Emotes.ALL) {
			step("emote " + e.id(), 2, () -> {
				mc.setScreen(null);
				Emotes.play(e.id());
			});
			step("emote view " + e.id(), e.id().equals("basketball") ? 12 : e.id().equals("faint") || e.id().equals("ascend") ? 40 : 14, () -> mc.setScreen(new TestViewScreen(0, 30, 90)));
			shot("emote_" + e.id());
			if (e.id().equals("basketball")) {
				step("basketball throw", 24, () -> { });
				shot("emote_basketball_throw");
			}
		}
		step("tab list", 10, () -> {
			mc.setScreen(null);
			mc.options.keyPlayerList.setDown(true);
		});
		shot("badge_tab");
		step("tab list off", 2, () -> mc.options.keyPlayerList.setDown(false));
		step("emote football ingame", 5, () -> {
			mc.setScreen(null);
			Emotes.play("football");
		});
		step("emote football wait", 25, () -> { });
		shot("emote_ingame_football");
		step("emote ingame", 5, () -> {
			mc.setScreen(null);
			Emotes.play("dance");
		});
		step("emote ingame wait", 30, () -> { });
		shot("emote_ingame_dance");
		// open platform high up, so the effects are not hidden by trees / grass
		command("fill ~-8 199 ~-8 ~8 199 ~8 white_concrete");
		command("tp @p ~ 200 ~");
		step("aura blackhole", 60, () -> {
			mc.setScreen(null);
			Cosmetics.Slot.EFFECT.set("fx_blackhole");
			Cosmetics.Slot.CAPE.set("");
			mc.player.setXRot(15);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		shot("aura_blackhole");
		step("aura thunder", 60, () -> Cosmetics.Slot.EFFECT.set("fx_thunder"));
		shot("aura_thunder");
		step("aura holo", 60, () -> Cosmetics.Slot.EFFECT.set("fx_holo"));
		shot("aura_holo");
		for (String fx : new String[]{"trail_plasma", "trail_inferno", "trail_frost", "trail_void", "trail_yinyang", "trail_rainbow",
				"orb_ghosts", "orb_crystals", "orb_rings", "orb_wisps", "orb_hearts"}) {
			step("aura " + fx, 60, () -> Cosmetics.Slot.EFFECT.set(fx));
			shot("aura_" + fx.substring(fx.indexOf('_') + 1));
		}
		command("item replace entity @p weapon.offhand with shield");
		step("low shield off", 20, () -> {
			Cosmetics.Slot.EFFECT.set("");
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.player.setXRot(0);
			PackMixerConfig.settings().lowShield = false;
		});
		shot("lowshield_off");
		step("low shield on", 10, () -> PackMixerConfig.settings().lowShield = true);
		shot("lowshield_on");
		step("low shield reset", 2, () -> {
			PackMixerConfig.settings().lowShield = false;
			Cosmetics.Slot.CAPE.set("");
		});
		// sky by day, looking at a corner of the sky box (seams between the faces)
		command("time set 6000");
		command("weather clear");
		step("sky corner", 40, () -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.player.setYRot(45);
			mc.player.setXRot(-35);
		});
		shot("sky_day_corner");
		step("sky up", 20, () -> mc.player.setXRot(-89));
		shot("sky_day_up");
		// live cosmetics: a line sent by "another player" must arrive without relog
		final java.util.UUID fake = java.util.UUID.randomUUID();
		final long[] sentAt = {0};
		step("cosm live send", 200, () -> {
			sentAt[0] = System.currentTimeMillis();
			new Thread(() -> {
				try {
					java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
							java.net.URI.create("https://ntfy.sh/testohd-styles-cosmetics-7f3k9q")).POST(
							java.net.http.HttpRequest.BodyPublishers.ofString("ts1;" + fake + ";crown;;;")).build(),
							java.net.http.HttpResponse.BodyHandlers.discarding());
				} catch (Exception e) {
					PackMixerClient.LOGGER.info("autotest: COSMLIVE send failed {}", e.toString());
				}
			}).start();
		});
		step("cosm live check", 5, () -> {
			PackMixerClient.LOGGER.info("autotest: COSMLIVE received={} after<={}ms", CosmeticSync.get(fake).head(),
					System.currentTimeMillis() - sentAt[0]);
			new Thread(() -> {
				try {
					java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
							java.net.URI.create("https://ntfy.sh/testohd-styles-cosmetics-7f3k9q")).POST(
							java.net.http.HttpRequest.BodyPublishers.ofString("ts1;" + fake + ";;;;")).build(),
							java.net.http.HttpResponse.BodyHandlers.discarding());
				} catch (Exception ignored) {
				}
			}).start();
		});
		// OP gaps and normal gaps from different packs
		step("opgap select", 5, () -> {
			Map<String, String> sel = new LinkedHashMap<>();
			sel.put("golden_apple", "Testohd23 V1 Purple");
			sel.put(DefaultCategories.ENCHANTED_APPLE, "Testohd23 V1 Orange");
			PackMixerConfig.saveSelection(sel);
			mc.setScreen(new PackMixerScreen(null));
		});
		step("opgap tab", 20, () -> {
			if (mc.screen instanceof PackMixerScreen s) {
				java.util.List<Category> cats = PackMixerConfig.loadCategories();
				for (int i = 0; i < cats.size(); i++) if (cats.get(i).id.equals(DefaultCategories.ENCHANTED_APPLE)) s.testSelect(i);
			}
		});
		shot("opgap_menu");
		step("opgap apply", 10, () -> {
			if (mc.screen instanceof PackMixerScreen s) s.testApply();
		});
		when("opgap reloaded", () -> mc.getOverlay() == null, () -> {
			java.nio.file.Path a = mc.getResourcePackDirectory().resolve(PackMixerConfig.OUTPUT_PACK_NAME).resolve("assets/minecraft");
			PackMixerClient.LOGGER.info("autotest: OPGAP texture={} model={} gap={}",
					java.nio.file.Files.exists(a.resolve("textures/item/enchanted_golden_apple.png")),
					java.nio.file.Files.exists(a.resolve("models/item/enchanted_golden_apple.json")),
					java.nio.file.Files.exists(a.resolve("textures/item/golden_apple.png")));
		});
		command("item replace entity @p weapon.mainhand with enchanted_golden_apple");
		command("item replace entity @p weapon.offhand with golden_apple");
		step("opgap look", 40, () -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.player.setXRot(10);
		});
		shot("opgap_ingame");
		step("opgap inv", 20, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
		shot("opgap_inventory");
		step("opgap inv close", 5, () -> mc.setScreen(null));
		command("gamemode creative @p");
		command("item replace entity @p weapon.mainhand with diamond_sword[enchantments={sharpness:1}]");
		command("item replace entity @p weapon.offhand with shield");
		command("item replace entity @p armor.chest with diamond_chestplate");
		command("item replace entity @p armor.head with netherite_helmet");
		command("summon end_crystal ^2 ^ ^3");
		command("time set 1000");
		command("weather clear");
		step("look", 40, () -> {
			mc.player.setXRot(-20);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		shot("world_day_first");
		step("third back", 30, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		shot("world_day_back");
		step("third front", 30, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		shot("world_day_front");
		command("time set 18000");
		step("night", 60, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		shot("world_night_first");
		step("night up", 20, () -> mc.player.setXRot(-60));
		shot("world_night_up");
		step("night back", 30, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		shot("world_night_back");
		command("time set 23200");
		step("sunrise", 40, () -> mc.player.setXRot(-15));
		shot("world_sunrise");
		command("time set 12500");
		step("sunset", 40, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		shot("world_sunset");
		step("effects", 10, () -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			Cosmetics.Slot.EFFECT.set("fx_galaxy");
			Cosmetics.Slot.HEAD.set("wizard_hat");
		});
		command("time set 6000");
		step("wait fx", 60, () -> { });
		shot("world_fx_galaxy");
		command("item replace entity @p armor.chest with air");
		command("item replace entity @p armor.head with air");
		command("setblock ^-1 ^ ^2 chest");
		step("wings test", 40, () -> {
			Cosmetics.Slot.BACK.set("angel_wings");
			Cosmetics.Slot.EFFECT.set("fx_rainbow");
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			mc.player.setXRot(10);
		});
		shot("world_wings_back");
		step("wings front", 40, () -> {
			Cosmetics.Slot.BACK.set("phoenix_wings");
			Cosmetics.Slot.EFFECT.set("fx_lightning");
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		shot("world_wings_front");
		step("totem", 40, () -> Cosmetics.Slot.EFFECT.set("fx_totem"));
		shot("world_fx_totem");
		for (String hat : new String[]{"inferno_crown", "plasma_crown", "frost_crown", "void_crown", "energy_halo", "void_halo", "divine_halo"}) {
			step("hat " + hat, 20, () -> {
				Cosmetics.Slot.HEAD.set(hat);
				Cosmetics.Slot.BACK.set("");
				Cosmetics.Slot.EFFECT.set("");
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.options.fov().set(30);
				mc.player.setXRot(20);
			});
			shot("hat_" + hat);
		}
		step("fov back", 2, () -> mc.options.fov().set(70));
		for (String w : new String[]{"plasma_wings", "inferno_wings", "void_wings", "energy_wings", "frost_wings", "angel_wings", "devil_wings"}) {
			step("wing " + w, 30, () -> {
				Cosmetics.Slot.BACK.set(w);
				Cosmetics.Slot.EFFECT.set("");
				Cosmetics.Slot.HEAD.set("");
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(12);
			});
			shot("wing_" + w + "_back");
			step("wing side " + w, 12, () -> mc.player.setYRot(mc.player.getYRot() + 60));
			shot("wing_" + w + "_side");
			step("wing back " + w, 2, () -> mc.player.setYRot(mc.player.getYRot() - 60));
		}
		command("item replace entity @p weapon.mainhand with netherite_spear[enchantments={sharpness:1}]");
		command("item replace entity @p weapon.offhand with mace");
		command("item replace entity @p armor.chest with netherite_chestplate");
		for (String pack : new String[]{"Energie Schwarz", "Energie Tuerkis"}) {
			step("pack " + pack, 10, () -> {
				Map<String, String> sel = PackMixerConfig.loadSelection();
				sel.put("mace", pack);
				sel.put("spear", pack);
				sel.put("armor", pack);
				PackMixerConfig.saveSelection(sel);
				PackBuilder.rebuildAndApply();
			});
			when("reload " + pack, () -> mc.getOverlay() == null, () -> { });
			String tag = pack.endsWith("Schwarz") ? "schwarz" : "tuerkis";
			step("close " + pack, 40, () -> {
				Cosmetics.Slot.HEAD.set("");
				Cosmetics.Slot.BACK.set("");
				Cosmetics.Slot.EFFECT.set("");
				mc.options.fov().set(30);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(5);
			});
			shot("sample_" + tag + "_front");
			step("fp " + pack, 25, () -> {
				mc.options.fov().set(70);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			shot("sample_" + tag + "_first");
			step("fp2 " + pack, 6, () -> { });
			shot("sample_" + tag + "_first_b");
		}
		// ---- animated crystals + their explosion
		command("item replace entity @p weapon.mainhand with air");
		command("item replace entity @p weapon.offhand with end_crystal");
		command("time set 6000");
		// ---- video: every V1 pack, all items in the hand, then crystal + anchor placed
		command("gamemode creative @p");
		command("time set 6000");
		command("weather clear");
		command("gamerule announceAdvancements false");
		command("recipe give @p *");
		command("advancement grant @p everything");
		step("video start", 40, () -> {
			try {
				mc.getToastManager().clear();
			} catch (Throwable ignored) {
			}
			startVideo(mc, "v1_video");
		});
		for (V1Packs.V1 v : V1Packs.PACKS) {
			String tag = v.name().replace("Testohd23 V1 ", "v1_").replace("Testohd23 V2 ", "v2_").replace("Testohd23 V3 ", "v3_").replace("Testohd Christmas V1 ", "xmas_").toLowerCase().replace(' ', '_');
			step("v1 " + tag, 2, () -> {
				Map<String, String> sel = new LinkedHashMap<>();
				for (Category c : PackMixerConfig.loadCategories()) sel.put(c.id, v.name());
				PackMixerConfig.saveSelection(sel);
				PackBuilder.rebuildAndApply();
			});
			when("reload " + tag, () -> mc.getOverlay() == null, () -> { });
			command("kill @e[type=end_crystal]");
			command("kill @e[type=item]");
			command("fill -12 199 -4 12 199 18 smooth_quartz");
			command("fill -12 200 -4 12 214 18 air");
			command("tp @p 0.5 200 0.5 0 0");
			command("clear @p");
			command("item replace entity @p weapon.offhand with shield");
			step("look " + tag, 10, () -> {
				mc.options.fov().set(70);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(18);
			});
			for (String item : new String[]{"netherite_sword", "diamond_sword", "netherite_pickaxe", "netherite_axe", "mace",
					"netherite_spear", "bow", "totem_of_undying", "ender_pearl", "golden_apple", "splash_potion[potion_contents={potion:\"minecraft:strength\"}]",
					"obsidian"}) {
				command("item replace entity @p weapon.mainhand with " + item);
				step("hold " + item, 24, () -> { });
				if (item.equals("netherite_sword") || item.equals("mace")) {
					step("swing " + item, 14, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
				}
			}
			// place: obsidian + crystal, anchor charged with glowstone
			step("place obsidian " + tag, 8, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			command("setblock 0 200 4 obsidian");
			command("item replace entity @p weapon.mainhand with end_crystal");
			step("hold crystal " + tag, 18, () -> { });
			step("place crystal " + tag, 2, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			command("summon end_crystal 0.5 201 4.5 {ShowBottom:0b}");
			step("look crystal " + tag, 22, () -> { });
			command("item replace entity @p weapon.mainhand with respawn_anchor");
			step("hold anchor " + tag, 18, () -> { });
			step("place anchor " + tag, 2, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			command("setblock 2 200 4 respawn_anchor");
			command("item replace entity @p weapon.mainhand with glowstone");
			step("hold glowstone " + tag, 18, () -> { });
			for (int ch = 1; ch <= 4; ch++) {
				int charge = ch;
				step("charge " + ch + " " + tag, 2, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
				command("setblock 2 200 4 respawn_anchor[charges=" + charge + "]");
				step("charged " + ch + " " + tag, 8, () -> { });
			}
			step("look both " + tag, 6, () -> mc.player.setYRot(8));
			shot("v1_" + tag + "_placed");
			step("admire " + tag, 30, () -> { });
			command("item replace entity @p weapon.mainhand with netherite_sword");
			step("hit " + tag, 8, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			command("execute at @p run damage @e[type=end_crystal,limit=1,sort=nearest] 1");
			step("boom " + tag, 3, () -> { });
			shot("v1_" + tag + "_boom");
			step("after " + tag, 40, () -> { });
		}
		step("video stop", 5, AutoTest::stopVideo);
		step("hud", 10, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		command("gamemode survival @p");
		step("wait hud", 30, () -> { });
		shot("world_hud");
		step("small particles", 5, () -> PackMixerConfig.settings().particleSize = 0.4);
		command("particle minecraft:crit ~ ~1 ~2 0.5 0.5 0.5 0.1 80");
		command("particle minecraft:explosion ~1 ~1 ~3 0 0 0 0 1");
		step("wait particles", 3, () -> { });
		shot("particles_small");
		step("big particles", 5, () -> PackMixerConfig.settings().particleSize = 1.0);
		command("particle minecraft:crit ~ ~1 ~2 0.5 0.5 0.5 0.1 80");
		command("particle minecraft:explosion ~1 ~1 ~3 0 0 0 0 1");
		step("wait particles 2", 3, () -> { });
		shot("particles_normal");
		command("setblock ~ ~ ~ fire");
		step("burn", 25, () -> { });
		shot("world_fire");
		command("setblock ~ ~ ~ air");
		command("effect give @p fire_resistance 60");
		step("menu in world", 30, () -> mc.setScreen(new PackMixerScreen(null)));
		shot("world_menu");
		step("my packs", 20, () -> {
			Map<String, String> sel = PackMixerConfig.loadSelection();
			Mixes.Mix mix = new Mixes.Mix("Mein PvP Pack", sel);
			Mixes.Mix back = Mixes.decode(Mixes.code(mix));
			PackMixerClient.LOGGER.info("autotest: code {} chars, roundtrip ok = {}", Mixes.code(mix).length(),
					back != null && back.selection.equals(mix.selection));
			Mixes.add(mix);
			Mixes.add(new Mixes.Mix("Weihnachten", Map.of("swords", "Testohd Christmas V1 Turquoise", "totem", "Fremdes Pack XY")));
			mc.setScreen(new MyPacksScreen(new PackMixerScreen(null), sel, PackLibrary.scan()));
		});
		shot("mypacks");
		step("preview", 30, () -> {
			Cosmetics.Slot.BACK.set("plasma_wings");
			Cosmetics.Slot.HEAD.set("plasma_crown");
			Cosmetics.Slot.EFFECT.set("fx_plasma");
			Map<String, String> sel = new LinkedHashMap<>();
			for (Category c : PackMixerConfig.loadCategories()) sel.put(c.id, "Testohd23 V1 Purple");
			mc.setScreen(new PackPreviewScreen(new PackMixerScreen(null), sel, PackLibrary.scan(), new PreviewCache()));
		});
		shot("preview_a");
		step("preview turn", 25, () -> { });
		shot("preview_b");
		step("quit", 40, () -> {
			mc.setScreen(null);
			mc.stop();
		});
	}

	// ---- video in CI: every tick the picture is read back from the game and piped into ffmpeg
	private static Process video;
	private static java.io.OutputStream videoIn;
	private static final java.util.concurrent.BlockingQueue<byte[]> videoFrames = new java.util.concurrent.ArrayBlockingQueue<>(64);
	private static int vw, vh;

	private static void startVideo(Minecraft mc, String name) {
		try {
			vw = mc.getMainRenderTarget().width / 2;
			vh = mc.getMainRenderTarget().height / 2;
			vw -= vw % 2;
			vh -= vh % 2;
			java.nio.file.Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(name + ".mp4");
			java.nio.file.Files.createDirectories(out.getParent());
			video = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "warning", "-f", "rawvideo", "-pix_fmt", "rgba",
					"-s", vw + "x" + vh, "-r", "20", "-i", "-", "-c:v", "libx264", "-preset", "veryfast", "-crf", "22",
					"-pix_fmt", "yuv420p", out.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
			videoIn = new java.io.BufferedOutputStream(video.getOutputStream(), 1 << 20);
			Thread t = new Thread(() -> {
				try {
					while (true) {
						byte[] f = videoFrames.take();
						if (f.length == 0) break;
						videoIn.write(f);
					}
					videoIn.close();
				} catch (Exception e) {
					PackMixerClient.LOGGER.error("autotest: video writer", e);
				}
			}, "autotest video");
			t.setDaemon(true);
			t.start();
			PackMixerClient.LOGGER.info("autotest: recording {}x{}", vw, vh);
		} catch (Exception e) {
			PackMixerClient.LOGGER.error("autotest: video failed", e);
			video = null;
		}
	}

	private static void captureFrame(Minecraft mc) {
		Screenshot.takeScreenshot(mc.getMainRenderTarget(), 2, img -> {
			try {
				if (video == null) return;
				byte[] buf = new byte[vw * vh * 4];
				int w = Math.min(vw, img.getWidth()), h = Math.min(vh, img.getHeight());
				for (int y = 0; y < h; y++)
					for (int x = 0; x < w; x++) {
						int p = img.getPixel(x, y), i = (y * vw + x) * 4;
						buf[i] = (byte) (p >> 16);
						buf[i + 1] = (byte) (p >> 8);
						buf[i + 2] = (byte) p;
						buf[i + 3] = (byte) 255;
					}
				videoFrames.offer(buf);
			} finally {
				img.close();
			}
		});
	}

	private static void stopVideo() {
		if (video == null) return;
		Process p = video;
		video = null;
		try {
			videoFrames.put(new byte[0]);
			p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			p.destroy();
		}
	}

	static void tick(Minecraft mc) {
		if (!ENABLED) return;
		if (video != null && mc.level != null && mc.getOverlay() == null) { // loading screens are left out
			try {
				mc.getToastManager().clear(); // no "recipe unlocked" popups in the video
				captureFrame(mc);
			} catch (Throwable e) {
				PackMixerClient.LOGGER.error("autotest: frame", e);
				video = null;
			}
		}
		if (!started) {
			started = true;
			plan(mc);
			PackMixerClient.LOGGER.info("autotest: {} steps", steps.size());
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		Step s = steps.peek();
		if (s == null) return;
		if (!s.ready().getAsBoolean()) {
			if (++timeout > 20 * 180) {
				PackMixerClient.LOGGER.error("autotest: timed out at '{}'", s.name());
				steps.clear();
				mc.stop();
			}
			return;
		}
		timeout = 0;
		steps.poll();
		PackMixerClient.LOGGER.info("autotest: {}", s.name());
		try {
			s.action().run();
		} catch (Throwable e) {
			PackMixerClient.LOGGER.error("autotest: step '{}' failed", s.name(), e);
		}
		waitTicks = s.delay();
	}

	private AutoTest() {
	}
}
