package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static de.philjoel.packmixer.BuiltinPacks.*;

/**
 * The parts of a built-in pack that are not plain items: things you see placed / worn / thrown
 * (end crystal, shield, armor on the body, elytra wings, trident, wind charge, chests), fire, hotbar,
 * hunger bar, sun and a custom sky box (OptiFine format, drawn by {@link SkyboxRenderer}).
 */
final class PackExtras {
	private PackExtras() {
	}

	static void generate(BuiltinPacks.Theme th, Path a, Path root) {
		// ---- end crystal when placed
		safe(() -> recolorTo(th, "entity/end_crystal/end_crystal.png", a, brighter(th.ramp())));
		// ---- shield (entity texture = what you hold and see on screen)
		for (String p : new String[]{"entity/shield_base_nopattern.png", "entity/shield_base.png", "entity/shield/base.png"}) {
			safe(() -> shield(th, p, a));
		}
		// ---- armor on the body + armor items
		for (String mat : new String[]{"diamond", "netherite", "iron", "gold", "chainmail", "copper"}) {
			safe(() -> recolorTo(th, "entity/equipment/humanoid/" + mat + ".png", a, th.ramp()));
			safe(() -> recolorTo(th, "entity/equipment/humanoid_leggings/" + mat + ".png", a, th.ramp()));
			String item = mat.equals("gold") ? "golden" : mat;
			for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
				safe(() -> recolorTo(th, "item/" + item + "_" + piece + ".png", a, th.ramp()));
			}
		}
		// ---- elytra wings + item
		safe(() -> recolorTo(th, "entity/equipment/wings/elytra.png", a, th.ramp()));
		safe(() -> recolorTo(th, "item/elytra.png", a, th.ramp()));
		safe(() -> recolorTo(th, "item/broken_elytra.png", a, th.ramp()));
		// ---- trident (thrown + item), wind charge projectile, crossbow
		safe(() -> recolorTo(th, "entity/trident.png", a, th.ramp()));
		safe(() -> toolTo(th, "item/trident.png", a));
		safe(() -> recolorTo(th, "entity/projectiles/wind_charge.png", a, brighter(th.ramp())));
		for (String c : new String[]{"crossbow_standby", "crossbow_pulling_0", "crossbow_pulling_1", "crossbow_pulling_2",
				"crossbow_arrow", "crossbow_firework"}) {
			safe(() -> toolTo(th, "item/" + c + ".png", a));
		}
		// ---- chests
		for (String c : new String[]{"normal", "normal_left", "normal_right"}) {
			safe(() -> recolorTo(th, "entity/chest/" + c + ".png", a, th.ramp()));
		}
		// ---- fire (animated)
		for (String f : new String[]{"fire_0", "fire_1"}) {
			safe(() -> {
				recolorTo(th, "block/" + f + ".png", a, brighter(th.ramp()));
				copyMeta("block/" + f + ".png", a);
			});
		}
		// ---- hotbar
		safe(() -> recolorTo(th, "gui/sprites/hud/hotbar.png", a, th.ramp()));
		safe(() -> recolorTo(th, "gui/sprites/hud/hotbar_selection.png", a, brighter(th.ramp())));
		// ---- hunger bar
		for (String f : new String[]{"food_full", "food_half"}) {
			safe(() -> recolorTo(th, "gui/sprites/hud/" + f + ".png", a, th.heartRamp()));
		}
		// ---- sun + sky box
		safe(() -> sun(th, a));
		safe(() -> sky(th, root));
	}

	/** The sun is drawn additively: black must stay black, only the bright part gets the colour. */
	private static void sun(BuiltinPacks.Theme th, Path a) throws Exception {
		NativeImage img = vanilla("environment/celestial/sun.png");
		if (img == null) return;
		int col = mix(skyColors(th)[2], 0xFFFFFF, 0.35);
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getPixel(x, y);
				double l = lum(p) / 255.0;
				int c = mix(0x000000, col, Math.min(1, l * 1.15));
				if (l > 0.9) c = mix(c, 0xFFFFFF, (l - 0.9) * 6);
				img.setPixel(x, y, (p & 0xFF000000) | c);
			}
		BuiltinPacks.current = null;
		save(img, a.resolve("environment/celestial/sun.png"));
	}

	private static void recolorTo(BuiltinPacks.Theme th, String path, Path a, int[] ramp) throws Exception {
		NativeImage img = vanilla(path);
		if (img == null) return;
		recolor(img, ramp, 0);
		save(img, a.resolve(path));
	}

	private static void toolTo(BuiltinPacks.Theme th, String path, Path a) throws Exception {
		NativeImage img = vanilla(path);
		if (img == null) return;
		recolorTool(img, th, true, 0);
		save(img, a.resolve(path));
	}

	// ======================================================================= shield

	/** Wood becomes the theme colour, the iron rim a lighter shade, plus a small emblem in the middle. */
	private static void shield(BuiltinPacks.Theme th, String path, Path a) throws Exception {
		NativeImage img = vanilla(path);
		if (img == null) return;
		int w = img.getWidth(), s = Math.max(1, w / 64);
		int[] rim = brighter(brighter(th.ramp()));
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int al = p >>> 24;
				if (al < 10) continue;
				int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
				int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
				double t = lum(p) / 255.0;
				int c = sat < 28 ? ramp(rim, 0.2 + t * 0.8) : ramp(th.ramp(), 0.15 + t * 0.85);
				img.setPixel(x, y, (al << 24) | c);
			}
		// emblem on the front face (front face = x 1..13, y 1..23 at 64 px)
		if (!path.endsWith("shield_base.png") && !path.endsWith("shield/base.png")) {
			int accent = 0xFF000000 | (th.style().christmas() ? 0xFFD24A : ramp(rim, 0.95));
			int dark = 0xFF000000 | ramp(th.ramp(), 0.05);
			String[] emblem = th.style().christmas() ? new String[]{
					"..#..",
					".###.",
					"#####",
					".###.",
					".#.#."} : new String[]{
					"..#..",
					".#+#.",
					"#+++#",
					".#+#.",
					"..#.."};
			int ox = 5, oy = 10;
			for (int ey = 0; ey < emblem.length; ey++)
				for (int ex = 0; ex < emblem[ey].length(); ex++) {
					char ch = emblem[ey].charAt(ex);
					if (ch == '.') continue;
					int col = ch == '#' ? accent : dark;
					for (int dx = 0; dx < s; dx++)
						for (int dy = 0; dy < s; dy++) img.setPixel((ox + ex) * s + dx, (oy + ey) * s + dy, col);
				}
		}
		save(img, a.resolve(path));
	}

	// ======================================================================= sky box

	static final int FACE = 128;

	/** Rotation of every cell of the OptiFine 3x2 sky box image (same order as OptiFine draws them). */
	static double[] faceToBox(int side, double x, double y, double z) {
		double[] v = {x, y, z};
		switch (side) {
			case 1 -> v = rotX(v, 90);
			case 0 -> v = rotX(v, -90);
			case 5 -> v = rotZ(v, 90);
			case 2 -> v = rotZ(v, 180);
			case 3 -> v = rotZ(v, 270);
			default -> {
			}
		}
		v = rotZ(v, -90);
		v = rotX(v, 90);
		return v;
	}

	static double[] rotX(double[] v, double deg) {
		double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
		return new double[]{v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
	}

	static double[] rotZ(double[] v, double deg) {
		double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
		return new double[]{v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]};
	}

	interface SkyColor {
		/** @return ARGB for a direction (normalised) */
		int at(double x, double y, double z);
	}

	static NativeImage skyImage(SkyColor color) {
		NativeImage img = new NativeImage(FACE * 3, FACE * 2, true);
		for (int side = 0; side < 6; side++) {
			int cx = (side % 3) * FACE, cy = (side / 3) * FACE;
			for (int py = 0; py < FACE; py++)
				for (int px = 0; px < FACE; px++) {
					double lx = -1 + 2 * (px + 0.5) / FACE, lz = -1 + 2 * (py + 0.5) / FACE;
					double[] d = faceToBox(side, lx, -1, lz);
					double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
					img.setPixel(cx + px, cy + py, color.at(d[0] / len, d[1] / len, d[2] / len));
				}
		}
		return img;
	}

	/** Paints a small dot into the sky box for a direction (used for stars). */
	static void dot(NativeImage img, double x, double y, double z, int argb, int size) {
		for (int side = 0; side < 6; side++) {
			// inverse of faceToBox: try each face, the one where the direction hits the y = -1 plane
			double[] ax = faceToBox(side, 1, 0, 0), ay = faceToBox(side, 0, 1, 0), az = faceToBox(side, 0, 0, 1);
			double lx = x * ax[0] + y * ax[1] + z * ax[2];
			double ly = x * ay[0] + y * ay[1] + z * ay[2];
			double lz = x * az[0] + y * az[1] + z * az[2];
			if (ly >= -1e-6) continue;
			double u = lx / -ly, v = lz / -ly;
			if (Math.abs(u) > 1 || Math.abs(v) > 1) continue;
			int px = (int) ((u + 1) / 2 * FACE), py = (int) ((v + 1) / 2 * FACE);
			int cx = (side % 3) * FACE, cy = (side / 3) * FACE;
			for (int dx = 0; dx < size; dx++)
				for (int dy = 0; dy < size; dy++) {
					int qx = Math.min(FACE - 1, px + dx), qy = Math.min(FACE - 1, py + dy);
					img.setPixel(cx + qx, cy + qy, argb);
				}
			return;
		}
	}

	private static double noise(double x, double y, double z) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
		double fx = x - ix, fy = y - iy, fz = z - iz;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		double v = 0;
		for (int dz = 0; dz <= 1; dz++)
			for (int dy = 0; dy <= 1; dy++)
				for (int dx = 0; dx <= 1; dx++) {
					double h = (Math.floorMod(hash(hash(ix + dx, iy + dy), iz + dz), 10000)) / 10000.0;
					v += h * (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
				}
		return v;
	}

	static double fbm(double x, double y, double z, int seed) {
		double sum = 0, amp = 0.5, f = 1;
		for (int i = 0; i < 4; i++) {
			sum += amp * noise(x * f + seed * 17.3, y * f - seed * 7.1, z * f + seed * 3.7);
			f *= 2.1;
			amp *= 0.5;
		}
		return sum;
	}

	static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** Colours of a theme for the sky: [deep, mid, light]. */
	static int[] skyColors(BuiltinPacks.Theme th) {
		int[] g = BuiltinPacks.GRADIENTS.get(th.name());
		if (g != null) return new int[]{g[0], g[g.length / 2], g[g.length - 1]};
		if (th.style() == BuiltinPacks.Style.RAINBOW) return new int[]{0x3A1C71, 0xD76D77, 0xFFAF7B};
		int[] r = th.ramp();
		return new int[]{r[1], r[2], r[3]};
	}

	/**
	 * Two layers: a coloured day sky with soft clouds (fades in at sunrise) and a night sky with stars and a nebula
	 * in the theme colours that turns with the stars.
	 */
	static void sky(BuiltinPacks.Theme th, Path root) throws Exception {
		Path dir = root.resolve("assets/minecraft/optifine/sky/world0");
		Files.createDirectories(dir);
		int[] c = skyColors(th);
		int seed = Math.floorMod(th.name().hashCode(), 997);
		boolean xmas = th.style().christmas();
		boolean galaxy = th.style() == BuiltinPacks.Style.GALAXY;

		// ---- day: gradient from a light horizon to a deeper zenith + clouds
		int zenith = mix(c[1], 0x000000, 0.15), horizon = mix(c[2], 0xFFFFFF, 0.45);
		NativeImage day = skyImage((x, y, z) -> {
			double h = Math.max(0, y);
			int col = y < 0 ? mix(horizon, c[0], Math.min(1, -y * 2.5)) : mix(horizon, zenith, Math.pow(h, 0.55));
			if (y > -0.05) {
				double cl = fbm(x * 3, y * 3, z * 3, seed);
				double cloud = Math.max(0, (cl - 0.52) * 3.2) * Math.min(1, (y + 0.05) * 6);
				col = mix(col, 0xFFFFFF, Math.min(0.85, cloud));
			}
			return 0xE6000000 | col; // a little see-through: blends with the normal sky colour
		});
		BuiltinPacks.current = null; // no item post effects on the sky
		save(day, dir.resolve("sky1.png"));
		Files.writeString(dir.resolve("sky1.properties"), """
				# Made by Testohd23 – day sky
				source=./sky1.png
				startFadeIn=4:30
				endFadeIn=6:30
				endFadeOut=19:30
				startFadeOut=17:30
				blend=alpha
				rotate=false
				""", StandardCharsets.UTF_8);

		// ---- night: nebula + stars, drawn additively and turning with the night sky
		NativeImage night = skyImage((x, y, z) -> {
			double n = fbm(x * 2.2, y * 2.2, z * 2.2, seed + 1);
			double band = Math.exp(-Math.pow((x * 0.6 + y * 0.8) * 2.2, 2)); // milky-way like band
			double neb = Math.max(0, n - 0.42) * 2.2 * (galaxy ? 1.4 : 0.9) * (0.35 + band);
			int col = mix(0x000000, mix(c[0], c[2], n), Math.min(1, neb));
			if (xmas) { // aurora curtains
				double au = Math.max(0, Math.sin(x * 9 + fbm(x * 2, 0, z * 2, seed) * 6)) * Math.max(0, y - 0.15) * 1.2;
				col = mix(col, 0x2EFF8A, Math.min(0.55, au * 0.6));
			}
			return 0xFF000000 | col;
		});
		java.util.Random rnd = new java.util.Random(seed);
		for (int i = 0; i < 1400; i++) {
			double x = rnd.nextGaussian(), y = rnd.nextGaussian(), z = rnd.nextGaussian();
			double len = Math.sqrt(x * x + y * y + z * z);
			int b = 150 + rnd.nextInt(106);
			int tint = rnd.nextInt(4) == 0 ? mix(0xFFFFFF, c[2], 0.5) : 0xFFFFFF;
			int col = 0xFF000000 | mix(0x000000, tint, b / 255.0);
			dot(night, x / len, y / len, z / len, col, rnd.nextInt(12) == 0 ? 2 : 1);
		}
		save(night, dir.resolve("sky2.png"));
		Files.writeString(dir.resolve("sky2.properties"), """
				# Made by Testohd23 – night sky
				source=./sky2.png
				startFadeIn=17:30
				endFadeIn=19:30
				endFadeOut=6:00
				startFadeOut=4:30
				blend=add
				rotate=true
				""", StandardCharsets.UTF_8);
	}
}
