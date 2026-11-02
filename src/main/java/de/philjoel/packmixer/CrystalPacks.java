package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Animated end crystal packs (item, placed crystal and explosion), generated from the vanilla textures.
 * The placed crystal is animated by {@link CrystalAnim}, the explosion gets recoloured particles plus an
 * extra burst in the colours of the crystal.
 */
public final class CrystalPacks {
	static final int FRAMES = 16;

	/** One crystal design. */
	record Design(String folder, String key, int glass, int[] boomRamp, int c1, int c2, ParticleOptions extra) {
	}

	static final List<Design> DESIGNS = List.of(
			new Design("Kristall Frost Puls", "c_frost", 0xBFEFFF, new int[]{0x0B3A66, 0x3FB6FF, 0xBFF2FF, 0xFFFFFF},
					0x8FE8FF, 0xFFFFFF, ParticleTypes.SNOWFLAKE),
			new Design("Kristall Plasma", "c_plasma", 0xE8A6FF, new int[]{0x2A0050, 0x9B2CFF, 0xFF4FD8, 0xFFE0FF},
					0xB02CFF, 0xFF6FE0, ParticleTypes.ELECTRIC_SPARK),
			new Design("Kristall Inferno", "c_inferno", 0xFFC27A, new int[]{0x3A0500, 0xC81E00, 0xFF8A00, 0xFFF0A0},
					0xFF5A00, 0xFFD040, ParticleTypes.FLAME),
			new Design("Kristall Void", "c_void", 0x6A3AA8, new int[]{0x050208, 0x2A0F45, 0x8A2BFF, 0xE8CCFF},
					0x7A2BFF, 0x1A0830, ParticleTypes.REVERSE_PORTAL),
			new Design("Kristall Tuerkis", "c_teal", 0x8FF5EA, new int[]{0x04201E, 0x0E9C92, 0x35F2DD, 0xE6FFFC},
					0x35F2DD, 0xE6FFFC, ParticleTypes.SOUL_FIRE_FLAME));

	static boolean isCrystalPack(String name) {
		return design(name) != null;
	}

	static Design design(String name) {
		if (name == null) return null;
		for (Design d : DESIGNS) if (d.folder.equals(name)) return d;
		return null;
	}

	/** Design of the pack chosen for end crystals (set by the pack builder). */
	static volatile Design active;
	private static boolean activeChecked;

	static void selectionChanged(java.util.Map<String, String> selection) {
		active = design(selection.get("end_crystal"));
		activeChecked = true;
	}

	// ================================================================= generation

	static void generateAll(Path packsDir) {
		for (Design d : DESIGNS) {
			try {
				generate(d, packsDir.resolve(d.folder));
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("Crystal pack {} failed", d.folder, e);
			}
		}
	}

	private static void generate(Design d, Path root) throws Exception {
		BuiltinPacks.current = null;
		Path a = root.resolve("assets/minecraft/textures");
		Files.createDirectories(a);
		// ---- item: animated strip
		NativeImage item = BuiltinPacks.vanilla("item/end_crystal.png");
		NativeImage firstItem = null;
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
			firstItem = new NativeImage(w, h, true);
			for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) firstItem.setPixel(x, y, strip.getPixel(x, y));
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
			BuiltinPacks.save(strip, a.resolve("entity/end_crystal/" + CrystalAnim.STRIP_NAME));
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
					double t = Math.max(0, Math.min(1, l * 1.15 - age * 0.45 + 0.15));
					img.setPixel(x, y, (al << 24) | BuiltinPacks.gradientAt(d.boomRamp, t));
				}
			BuiltinPacks.save(img, a.resolve(path));
		}
		// ---- pack.mcmeta + icon
		Files.writeString(root.resolve("pack.mcmeta"), """
				{
				  "pack": {
				    "description": {"translate": "packmixer.pack.desc.%s", "fallback": "Made by Testohd23"},
				    "min_format": 75,
				    "max_format": 75
				  }
				}
				""".formatted(d.key), StandardCharsets.UTF_8);
		NativeImage icon = new NativeImage(64, 64, true);
		for (int y = 0; y < 64; y++)
			for (int x = 0; x < 64; x++) {
				double r = Math.hypot(x - 31.5, y - 31.5) / 45.0;
				icon.setPixel(x, y, 0xFF000000 | BuiltinPacks.gradientAt(new int[]{BuiltinPacks.darken(d.c1, 0.55), 0x0A0A12}, r));
			}
		if (firstItem != null) {
			int fw = firstItem.getWidth();
			for (int y = 0; y < 48; y++)
				for (int x = 0; x < 48; x++) {
					int p = firstItem.getPixel(x * fw / 48, y * fw / 48);
					if ((p >>> 24) >= 10) icon.setPixel(8 + x, 8 + y, p | 0xFF000000);
				}
			firstItem.close();
		}
		BuiltinPacks.save(icon, root.resolve("pack.png"));
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
		switch (d.key) {
			case "c_frost" -> {
				double ring = Math.exp(-Math.pow(r - (f / (double) FRAMES) * 10, 2) / 2.2)
						+ 0.6 * Math.exp(-Math.pow(r - ((f + 8) % FRAMES / (double) FRAMES) * 10, 2) / 2.2);
				int c = col(new int[]{0x06223A, 0x1F8FD6, 0x8FE8FF, 0xFFFFFF}, l * 0.7 + ring * 0.45);
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
		switch (d.key) {
			case "c_frost" -> {
				double ring = Math.exp(-Math.pow(r - (f / (double) FRAMES) * 11, 2) / 3);
				return glyph ? 0xFFFFFF : col(new int[]{0x0B3A66, 0x3FB6FF, 0xDFF8FF}, 0.3 + ring * 0.7);
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
						double k = Math.min(1.3, (0.35 + l * 0.95) * pulse);
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

	// ================================================================= explosion burst

	/** Extra explosion effect in the crystal colours (only for crystal explosions). */
	static void burst(ClientLevel level, double x, double y, double z) {
		if (!activeChecked) selectionChanged(PackMixerConfig.loadSelection());
		Design d = active;
		if (d == null) return;
		RandomSource r = level.getRandom();
		// shock ring
		for (int i = 0; i < 64; i++) {
			double a = i / 64.0 * Math.PI * 2;
			double s = 0.55 + r.nextDouble() * 0.15;
			level.addParticle(new DustParticleOptions(i % 2 == 0 ? d.c1 : d.c2, 2.2f), x, y + 0.3, z,
					Math.cos(a) * s * 8, 0.05, Math.sin(a) * s * 8);
		}
		// sphere of sparks
		for (int i = 0; i < 70; i++) {
			double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(r.nextDouble() * 2 - 1);
			double s = 0.25 + r.nextDouble() * 0.35;
			double vx = Math.sin(ph) * Math.cos(th) * s, vy = Math.cos(ph) * s, vz = Math.sin(ph) * Math.sin(th) * s;
			if (i % 3 == 0) level.addParticle(d.extra, x, y + 0.5, z, vx, vy, vz);
			else level.addParticle(new DustParticleOptions(i % 2 == 0 ? d.c2 : d.c1, 1.4f), x, y + 0.5, z, vx * 9, vy * 9, vz * 9);
		}
		// column of light
		for (int i = 0; i < 20; i++)
			level.addParticle(ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 0.4, y + 0.2, z + (r.nextDouble() - 0.5) * 0.4,
					0, 0.25 + r.nextDouble() * 0.35, 0);
	}

	static List<String> names() {
		List<String> l = new ArrayList<>();
		for (Design d : DESIGNS) l.add(d.folder);
		return l;
	}

	private CrystalPacks() {
	}
}
