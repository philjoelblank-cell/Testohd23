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

	private static void plan(Minecraft mc) {
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
			for (String entry : new String[]{"item_position", "packmixer", "tt"}) {
				step("settings " + entry, 20, () -> {
					PackMixerSettingsScreen.lastSelected = entry;
					mc.setScreen(new PackMixerSettingsScreen(null));
				});
				shot("settings_s" + scale + "_" + entry);
				if (scale != 3) break;
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
			String tag = v.name().replace("Testohd23 V1 ", "v1_").replace("Testohd Christmas V1 ", "xmas_").toLowerCase();
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
			command("summon end_crystal 0.5 201 4.5");
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
		command("setblock ~ ~ ~ fire");
		step("burn", 25, () -> { });
		shot("world_fire");
		command("setblock ~ ~ ~ air");
		command("effect give @p fire_resistance 60");
		step("menu in world", 30, () -> mc.setScreen(new PackMixerScreen(null)));
		shot("world_menu");
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
