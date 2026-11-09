package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Animated end crystal packs (item, placed crystal and explosion), generated from the vanilla textures.
 * The placed crystal is animated by {@link CrystalAnim}, the explosion gets recoloured particles plus an
 * extra burst in the colours of the crystal.
 */
public final class CrystalPacks {
	static final int FRAMES = 16;

	/** One crystal design. */
	record Design(String folder, String key, int glass, int[] boomRamp, int c1, int c2) {
	}

	static final List<Design> DESIGNS = List.of(
			new Design("Kristall Frost Puls", "c_frost", 0x70F0E4, new int[]{0x0B4A48, 0x1BB5B0, 0x6FF0E6, 0xE0FFFC},
					0x6FF0E6, 0xFFFFFF),
			new Design("Kristall Plasma", "c_plasma", 0xC070FF, new int[]{0x2A0050, 0x8A1FE0, 0xFF4FD8, 0xFFB0F0},
					0xB02CFF, 0xFF6FE0),
			new Design("Kristall Inferno", "c_inferno", 0xFFA040, new int[]{0x3A0500, 0xD02000, 0xFF7A00, 0xFFD040},
					0xFF5A00, 0xFFD040),
			new Design("Kristall Void", "c_void", 0x5A2AA0, new int[]{0x050208, 0x2A0F45, 0x7A2BFF, 0xC890FF},
					0x7A2BFF, 0x1A0830),
			new Design("Kristall Tuerkis", "c_teal", 0x40E8D8, new int[]{0x04201E, 0x0E9C92, 0x35F2DD, 0xB8FFF6},
					0x35F2DD, 0xE6FFFC),
			new Design("Kristall Holy", "c_holy", 0xFFF2C8, new int[]{0x8A7A50, 0xE8D8A0, 0xFFF6D8, 0xFFFFFF},
					0xFFE680, 0xFFFFFF),
			// V2 crystals: two colours swirling, a light orbiting the core
			new Design("Kristall V2 Sunset", "c2_sunset", 0xFF8A50, new int[]{0x3A0800, 0xFF5A1F, 0xFF2E93, 0xFFE3A0}, 0xFF5A1F, 0xFF2E93),
			new Design("Kristall V2 Ocean", "c2_ocean", 0x50A0FF, new int[]{0x020A3A, 0x1240FF, 0x2EE6FF, 0xE0FBFF}, 0x1240FF, 0x2EE6FF),
			new Design("Kristall V2 Aurora", "c2_aurora", 0x60E8B0, new int[]{0x04201A, 0x18D98A, 0x8A3BFF, 0xD8FFF0}, 0x18D98A, 0x8A3BFF),
			new Design("Kristall V2 Neon", "c2_neon", 0xC060FF, new int[]{0x200030, 0xFF1FB4, 0x22E6FF, 0xFFFFFF}, 0xFF1FB4, 0x22E6FF),
			new Design("Kristall V2 Blood Moon", "c2_bloodmoon", 0xC02020, new int[]{0x120002, 0x7A0012, 0xFF2A2A, 0xFFB0A0}, 0x7A0012, 0xFF2A2A));

	static final String STRIP_NAME = "end_crystal_testohd_anim.png";

	/** Crystal design of a pack (the V1 packs bring one), or null. */
	static Design design(String packName) {
		V1Packs.V1 v = V1Packs.of(packName);
		if (v == null) return null;
		for (Design d : DESIGNS) if (d.key.equals(v.crystal())) return d;
		return null;
	}

	static boolean isCrystalPack(String name) {
		return design(name) != null;
	}

	/** Design of the pack chosen for end crystals (set by the pack builder). */
	static volatile Design active;
	private static boolean activeChecked;

	static boolean activeChecked() {
		return activeChecked;
	}

	static void selectionChanged(java.util.Map<String, String> selection) {
		active = design(selection.get("end_crystal"));
		activeChecked = true;
	}

	// ================================================================= generation

	/** Writes the animated crystal (item, placed crystal, explosion) into a pack's textures folder. */
	static void writeInto(Design d, Path a) throws Exception {
		BuiltinPacks.Theme keep = BuiltinPacks.current;
		V1Packs.Element keepEl = V1Packs.current;
		BuiltinPacks.current = null;
		V1Packs.current = null;
		boolean keepFrost = BuiltinPacks.frostAnim;
		BuiltinPacks.frostAnim = false;
		try {
			// ---- item: animated strip
			NativeImage item = BuiltinPacks.vanilla("item/end_crystal.png");
			if (item != null) {
				int w = item.getWidth(), h = item.getHeight();
				NativeImage strip = new NativeImage(w, h * FRAMES, true);
				for (int f = 0; f < FRAMES; f++)
					for (int y = 0; y < h; y++)
						for (int x = 0; x < w; x++) {
							int p = item.getPixel(x, y);
							int al = p >>> 24;
							if (al < 10) { strip.setPixel(x, f * h + y, 0); continue; }
							double u = x * 16.0 / w, v = y * 16.0 / h;
							int rgb = itemColor(d, f, u, v, BuiltinPacks.lum(p) / 255.0);
							strip.setPixel(x, f * h + y, (al << 24) | rgb);
						}
				item.close();
				BuiltinPacks.save(strip, a.resolve("item/end_crystal.png"));
				Files.writeString(a.resolve("item/end_crystal.png.mcmeta"),
						"{\"animation\": {\"frametime\": 2, \"interpolate\": true}}\n", StandardCharsets.UTF_8);
			}
			// ---- placed crystal: frame 0 as the normal texture + all frames as a strip for CrystalAnim
			NativeImage ent = BuiltinPacks.vanilla("entity/end_crystal/end_crystal.png");
			if (ent != null) {
				int w = ent.getWidth(), h = ent.getHeight();
				NativeImage strip = new NativeImage(w, h * FRAMES, true);
				for (int f = 0; f < FRAMES; f++) entityFrame(d, f, ent, strip, f * h);
				NativeImage first = new NativeImage(w, h, true);
				for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) first.setPixel(x, y, strip.getPixel(x, y));
				ent.close();
				BuiltinPacks.save(first, a.resolve("entity/end_crystal/end_crystal.png"));
				BuiltinPacks.save(strip, a.resolve("entity/end_crystal/" + STRIP_NAME));
			}
			// ---- explosion particles in the crystal colours
			for (int i = 0; i < 16; i++) {
				String path = "particle/explosion_" + i + ".png";
				NativeImage img = BuiltinPacks.vanilla(path);
				if (img == null) continue;
				double age = i / 15.0;
				for (int y = 0; y < img.getHeight(); y++)
					for (int x = 0; x < img.getWidth(); x++) {
						int p = img.getPixel(x, y);
						int al = p >>> 24;
						if (al < 10) continue;
						double l = BuiltinPacks.lum(p) / 255.0;
						// young frames glow bright, older ones fade into the dark colour
						double t = Math.max(0, Math.min(1, 0.2 + l * 0.7 - age * 0.35));
						img.setPixel(x, y, (al << 24) | BuiltinPacks.gradientAt(d.boomRamp, t));
					}
				BuiltinPacks.save(img, a.resolve(path));
			}
		} finally {
			BuiltinPacks.current = keep;
			V1Packs.current = keepEl;
			BuiltinPacks.frostAnim = keepFrost;
		}
	}

	private static int col(int[] stops, double t) {
		return BuiltinPacks.gradientAt(stops, t);
	}

	private static int mix(int a, int b, double t) {
		return BuiltinPacks.gradientAt(new int[]{a, b}, t);
	}

	private static double rnd(int x, int y, int seed) {
		return (BuiltinPacks.hash(x * 31 + seed, y * 17 - seed * 7) & 0xFFFF) / 65535.0;
	}

	/** Smooth value noise, tiles every 16 in y (so scrolling loops). */
	private static double noise(double x, double y, int seed) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		double fx = x - ix, fy = y - iy;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = rnd(ix, Math.floorMod(iy, 16), seed), b = rnd(ix + 1, Math.floorMod(iy, 16), seed);
		double c = rnd(ix, Math.floorMod(iy + 1, 16), seed), e = rnd(ix + 1, Math.floorMod(iy + 1, 16), seed);
		return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + e * fx) * fy;
	}

	/** Colour of the item at frame f, position (u,v) in 0..16, original brightness l. */
	static int itemColor(Design d, int f, double u, double v, double l) {
		double ph = f / (double) FRAMES * Math.PI * 2;
		double r = Math.hypot(u - 7.5, v - 7.5), ang = Math.atan2(v - 7.5, u - 7.5);
		int iu = (int) u, iv = (int) v;
		if (d.key.startsWith("c2_")) { // V2: two colours swirling around, a light orbiting
			double sw = Math.sin(ang * 2 + ph - r * 0.7) * 0.5 + 0.5;
			int c = col(new int[]{d.boomRamp[0], d.c1, d.c2, d.boomRamp[3]}, l * 0.5 + sw * 0.5);
			double oa = ph * 2, ox = 7.5 + Math.cos(oa) * 5, oy = 7.5 + Math.sin(oa) * 5;
			double orb = Math.exp(-(Math.pow(u - ox, 2) + Math.pow(v - oy, 2)) / 2.5);
			return mix(c, 0xFFFFFF, Math.min(1, orb * 0.9 + (sw > 0.94 ? 0.3 : 0)));
		}
		switch (d.key) {
			case "c_frost" -> {
				double ring = Math.exp(-Math.pow(r - (f / (double) FRAMES) * 10, 2) / 2.2)
						+ 0.6 * Math.exp(-Math.pow(r - ((f + 8) % FRAMES / (double) FRAMES) * 10, 2) / 2.2);
				int c = col(new int[]{0x06302E, 0x1BB5B0, 0x8FF5EA, 0xFFFFFF}, l * 0.7 + ring * 0.45);
				double tw = rnd(iu, iv, 3);
				int phase = (int) (tw * FRAMES);
				if (tw > 0.82 && (f == phase || f == (phase + 1) % FRAMES)) c = 0xFFFFFF;
				return c;
			}
			case "c_plasma" -> {
				double sw = Math.sin(ang * 3 + ph * 2 - r * 1.2) * 0.5 + 0.5;
				int c = col(new int[]{0x1A0030, 0x7A1FD6, 0xFF4FD8, 0xFFD6FF}, l * 0.55 + sw * 0.45);
				double pulse = Math.exp(-Math.pow(r - (1 - f / (double) FRAMES) * 9, 2) / 1.5); // energy pulls into the core
				return mix(c, 0xFFFFFF, Math.min(1, pulse * 0.55 + (sw > 0.93 ? 0.35 : 0)));
			}
			case "c_inferno" -> {
				double n = noise(u * 0.55, v * 0.55 + f * (16.0 * 0.55 / FRAMES) * 2, 11) * 0.65
						+ noise(u * 1.1, v * 1.1 + f * (16.0 * 1.1 / FRAMES), 12) * 0.35;
				int c = col(new int[]{0x1A0200, 0xB31A00, 0xFF7A00, 0xFFE28A}, l * 0.55 + (1 - v / 16) * 0.2 + n * 0.4);
				// embers rising
				double em = rnd(iu, Math.floorMod(iv + f, 16), 21);
				if (em > 0.94) c = mix(c, 0xFFF4C0, 0.8);
				return c;
			}
			case "c_void" -> {
				double sw = Math.sin(ang * 2 - ph + r * 0.9) * 0.5 + 0.5;
				int c = col(new int[]{0x000000, 0x0D0614, 0x1D0D2E}, l * 0.9);
				double edge = Math.max(0, l - 0.55) * 2.2 * (0.6 + 0.4 * Math.sin(ph));
				int glow = col(new int[]{0x5A00C8, 0xB45CFF, 0xF2D9FF}, sw);
				c = mix(c, glow, Math.min(1, edge * 0.9 + (sw > 0.85 ? 0.5 : 0)));
				double st = rnd(iu, iv, 41); // tiny stars blinking inside
				int phase = (int) (st * FRAMES);
				if (st > 0.86 && l < 0.6 && Math.abs(f - phase) <= 1) c = mix(c, 0xFFFFFF, f == phase ? 0.9 : 0.4);
				return c;
			}
			case "c_holy" -> { // white-gold light: rays turning around the crystal, soft glow
				double ray = Math.pow(Math.max(0, Math.cos(ang * 4 - ph)), 6);
				int c = col(new int[]{0x8A7A50, 0xE8D8A0, 0xFFF6D8, 0xFFFFFF}, l * 0.6 + 0.25 + ray * 0.3);
				double tw = rnd(iu, iv, 51);
				int phase = (int) (tw * FRAMES);
				if (tw > 0.88 && Math.abs(f - phase) <= 1) c = 0xFFFFFF;
				return c;
			}
			default -> { // turquoise energy: light running over the crystal
				double p = f / (double) FRAMES * 2.4 - 0.7, dd = (u + (15 - v)) / 30.0;
				double band = Math.exp(-Math.pow((dd - p) * 7, 2));
				int c = col(new int[]{0x0A1012, 0x123A3A, 0x2FD9C6, 0xD8FFFA}, l * 0.75);
				c = mix(c, 0x35F2DD, band * 0.85);
				return mix(c, 0xFFFFFF, Math.max(0, Math.min(1, (band - 0.75) * 1.6)));
			}
		}
	}

	/** Colour of the inner core cube at frame f; (u,v) 0..16 on one face, glyph = dark symbol pixel. */
	static int coreColor(Design d, int f, double u, double v, boolean glyph, int faceSeed) {
		double ph = f / (double) FRAMES * Math.PI * 2;
		double r = Math.hypot(u - 7.5, v - 7.5), ang = Math.atan2(v - 7.5, u - 7.5);
		if (d.key.startsWith("c2_")) {
			double sw = Math.sin(ang * 2 - ph * 2 + r * 1.2) * 0.5 + 0.5;
			return glyph ? mix(0xFFFFFF, d.c2, 0.3 + 0.3 * Math.sin(ph * 2)) : col(new int[]{d.boomRamp[0], d.c1, d.c2}, sw);
		}
		switch (d.key) {
			case "c_frost" -> {
				double ring = Math.exp(-Math.pow(r - (f / (double) FRAMES) * 11, 2) / 3);
				return glyph ? 0xFFFFFF : col(new int[]{0x0B4A48, 0x2FD9C6, 0xDFFFFA}, 0.3 + ring * 0.7);
			}
			case "c_plasma" -> {
				double sw = Math.sin(ang * 3 + ph * 2 - r * 1.4) * 0.5 + 0.5;
				return glyph ? mix(0xFFFFFF, 0xFF9AF0, 0.3 + 0.3 * Math.sin(ph * 2))
						: col(new int[]{0x3A006A, 0xB02CFF, 0xFF7AE6}, sw);
			}
			case "c_inferno" -> {
				double n = noise(u * 0.6, v * 0.6 + f * (16.0 * 0.6 / FRAMES) * 2, 30 + faceSeed);
				return glyph ? 0xFFF6C0 : col(new int[]{0x4A0500, 0xE03A00, 0xFFB000, 0xFFF0A0}, (1 - v / 16) * 0.5 + n * 0.55);
			}
			case "c_void" -> {
				double sw = Math.sin(ang * 2 - ph + r * 1.1) * 0.5 + 0.5;
				double g = 0.6 + 0.4 * Math.sin(ph * 2);
				return glyph ? mix(0x8A2BFF, 0xFFFFFF, g * 0.6) : mix(0x030106, 0x2A0F45, sw * 0.6);
			}
			case "c_holy" -> {
				double ray = Math.pow(Math.max(0, Math.cos(ang * 3 + ph)), 4);
				return glyph ? 0xFFFFFF : col(new int[]{0xC8A850, 0xFFE680, 0xFFFDF0}, 0.3 + ray * 0.7);
			}
			default -> {
				double p = f / (double) FRAMES * 2.4 - 0.7, dd = (u + (15 - v)) / 30.0;
				double band = Math.exp(-Math.pow((dd - p) * 6, 2));
				return glyph ? 0xE6FFFC : mix(0x0B2A2A, 0x35F2DD, band);
			}
		}
	}

	/**
	 * Placed crystal texture (vanilla layout: glass cube left half, core cube right half of the top,
	 * base below). The glass keeps its pattern but gets the crystal colour and a soft pulse.
	 */
	private static void entityFrame(Design d, int f, NativeImage src, NativeImage dst, int oy) {
		int w = src.getWidth(), h = src.getHeight();
		int face = Math.max(1, w / 8); // one cube face (16 px on the 128x64 texture)
		double pulse = 0.85 + 0.15 * Math.sin(f / (double) FRAMES * Math.PI * 2);
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = src.getPixel(x, y);
				int al = p >>> 24;
				int out = p;
				if (y < h / 2 && al >= 10) {
					double l = BuiltinPacks.lum(p) / 255.0;
					double u = (x % face) * 16.0 / face, v = (y % face) * 16.0 / face;
					if (x < w / 2) { // glass
						int g = d.glass;
						double k = Math.min(1.35, (0.25 + l * 1.0) * pulse);
						int rr = (int) Math.min(255, ((g >> 16) & 0xFF) * k), gg = (int) Math.min(255, ((g >> 8) & 0xFF) * k),
								bb = (int) Math.min(255, (g & 0xFF) * k);
						out = (al << 24) | (rr << 16) | (gg << 8) | bb;
					} else { // core
						boolean glyph = l < 120 / 255.0;
						out = 0xFF000000 | coreColor(d, f, u, v, glyph, x / face + (y / face) * 8);
					}
				} else if (al >= 10) { // base: darker, with a hint of the colour
					out = (al << 24) | mix(p & 0xFFFFFF, BuiltinPacks.darken(d.c1, 0.5), 0.25);
				}
				dst.setPixel(x, oy + y, out);
			}
	}

	private CrystalPacks() {
	}
}
