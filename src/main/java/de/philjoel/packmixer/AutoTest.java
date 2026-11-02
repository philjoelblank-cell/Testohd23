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
				Minecraft.getInstance().gameRenderer.mainRenderTarget(), 1, c -> { }));
	}

	private static void command(String cmd) {
		step("cmd " + cmd, 2, () -> {
			MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
			if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
		});
	}

	private static void plan(Minecraft mc) {
		when("game ready", () -> mc.gui.overlay() == null && mc.gui.screen() != null
				&& PackMixerConfig.settings().builtinPacksVersion >= BuiltinPacks.VERSION, () -> { });
		for (int scale : new int[]{2, 3, 4}) {
			step("gui scale " + scale, 5, () -> {
				mc.options.guiScale().set(scale);
				mc.resizeGui();
			});
			step("open menu", 30, () -> mc.gui.setScreen(new PackMixerScreen(null)));
			int n = PackMixerConfig.loadCategories().size();
			for (int i = 0; i < n; i++) {
				int index = i;
				step("category " + i, 25, () -> {
					if (mc.gui.screen() instanceof PackMixerScreen s) s.testSelect(index);
				});
				shot("menu_s" + scale + "_" + String.format("%02d", i));
				if (scale != 3) break; // all categories once, the other scales only the first one
			}
			for (String entry : new String[]{"item_position", "packmixer", "tt"}) {
				step("settings " + entry, 20, () -> {
					PackMixerSettingsScreen.lastSelected = entry;
					mc.gui.setScreen(new PackMixerSettingsScreen(null));
				});
				shot("settings_s" + scale + "_" + entry);
				if (scale != 3) break;
			}
			for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
				step("cosmetics " + slot, 20, () -> {
					CosmeticsScreen.lastSlot = slot;
					mc.gui.setScreen(new CosmeticsScreen(null));
				});
				shot("cosmetics_s" + scale + "_" + slot.name().toLowerCase());
				if (scale != 3) break;
			}
		}
		// ---- in a world: one built-in pack for everything + cosmetics
		step("gui scale back", 5, () -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
			mc.gui.setScreen(null);
		});
		step("select pack", 5, () -> {
			Map<String, String> sel = new LinkedHashMap<>();
			for (Category c : PackMixerConfig.loadCategories()) sel.put(c.id, c.id.equals("sky") ? "Verlauf Sonnenuntergang" : "Galaxie");
			PackMixerConfig.saveSelection(sel);
			PackBuilder.rebuildAndApply();
		});
		when("reload done", () -> mc.gui.overlay() == null, () -> { });
		step("create world", 40, () -> CreateWorldScreen.openFresh(mc, () -> { }));
		when("create screen", () -> mc.gui.screen() instanceof CreateWorldScreen, () -> {
			try {
				var m = CreateWorldScreen.class.getDeclaredMethod("onCreate");
				m.setAccessible(true);
				m.invoke(mc.gui.screen());
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("autotest: could not create world", e);
			}
		});
		when("in world", () -> mc.player != null && mc.level != null && mc.gui.screen() == null, () -> {
			mc.options.renderDistance().set(4);
			Cosmetics.Slot.HEAD.set("crown");
			Cosmetics.Slot.BACK.set("phoenix_wings");
			Cosmetics.Slot.EFFECT.set("fx_lightwings");
		});
		step("settle", 200, () -> { });
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
		step("hud", 10, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		command("gamemode survival @p");
		step("wait hud", 30, () -> { });
		shot("world_hud");
		command("setblock ~ ~ ~ fire");
		step("burn", 25, () -> { });
		shot("world_fire");
		command("setblock ~ ~ ~ air");
		command("effect give @p fire_resistance 60");
		step("menu in world", 30, () -> mc.gui.setScreen(new PackMixerScreen(null)));
		shot("world_menu");
		step("quit", 40, () -> {
			mc.gui.setScreen(null);
			mc.stop();
		});
	}

	static void tick(Minecraft mc) {
		if (!ENABLED) return;
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
