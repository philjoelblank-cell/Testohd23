package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.List;

/**
 * "Testohd23 V1" packs: clean coloured items that every few seconds get taken over by their element
 * (plasma, fire, void, energy, ice) with particles flying around the item, then go back to normal.
 * Each pack also brings its animated end crystal.
 */
final class V1Packs {
	enum Element {
		PLASMA, FIRE, VOID, ENERGY, ICE
	}

	record V1(String name, Element element, String crystal, int eye) {
	}

	static final List<V1> PACKS = List.of(
			new V1("Testohd23 V1 Purple", Element.PLASMA, "c_plasma", 0xFF4FD8),
			new V1("Testohd23 V1 Orange", Element.FIRE, "c_inferno", 0xFF9A1F),
			new V1("Testohd23 V1 Black", Element.VOID, "c_void", 0xB46CFF),
			new V1("Testohd23 V1 Turquoise", Element.ENERGY, "c_teal", 0x35F2DD),
			new V1("Testohd Christmas V1 Turquoise", Element.ICE, "c_frost", 0x8FE3FF));

	static V1 of(String name) {
		if (name == null) return null;
		for (V1 v : PACKS) if (v.name.equals(name)) return v;
		return null;
	}

	static boolean isV1(String name) {
		return of(name) != null;
	}

	/** Element of the pack being generated right now (null = no animation). */
	static Element current;

	static final int FRAMES = 40;

	private static double smooth(double a, double b, double x) {
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	private static double rnd(int x, int y, int s) {
		return (BuiltinPacks.hash(x * 73 + s * 31, y * 151 - s * 17) & 0xFFFF) / 65535.0;
	}

	private static int mix(int a, int b, double t) {
		return BuiltinPacks.gradientAt(new int[]{a, b}, t);
	}

	private static int col(int[] st, double t) {
		return BuiltinPacks.gradientAt(st, t);
	}

	/** Builds the animated strip (FRAMES frames) for one item texture. */
	static NativeImage animate(NativeImage img, Element el, int seed) {
		int w = img.getWidth(), h = img.getHeight();
		double u = w / 16.0; // pixel size of one "art pixel"
		boolean[] solid = new boolean[w * h];
		int minD = Integer.MAX_VALUE, maxD = Integer.MIN_VALUE;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if ((img.getPixel(x, y) >>> 24) < 10) continue;
				solid[y * w + x] = true;
				int d = x + (h - 1 - y);
				minD = Math.min(minD, d);
				maxD = Math.max(maxD, d);
			}
		NativeImage out = new NativeImage(w, h * FRAMES, true);
		if (minD == Integer.MAX_VALUE) {
			for (int f = 0; f < FRAMES; f++) for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setPixel(x, f * h + y, 0);
			return out;
		}
		double span = Math.max(1, maxD - minD);
		int ext = (int) Math.max(1, Math.round(u)); // reach of the crust outside the item
		for (int f = 0; f < FRAMES; f++) {
			// in: frames 9..19, hold, out: 25..35
			double in = -0.2 + 1.45 * smooth(9, 19, f);
			double outF = -0.2 + 1.45 * smooth(25, 35, f);
			boolean active = f >= 9 && f <= 36;
			int oy = f * h;
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int p = img.getPixel(x, y);
					int gx = (int) (x / u), gy = (int) (y / u);
					double jag = (rnd(gx, gy, seed) - 0.5) * 0.14;
					double d = (x + (h - 1 - y) - minD) / span + jag;
					boolean covered = d < in && d > outF;
					double rimIn = Math.abs(d - in), rimOut = Math.abs(d - outF);
					if (solid[y * w + x]) {
						int a = p >>> 24;
						int c = p & 0xFFFFFF;
						if (covered) {
							double l = BuiltinPacks.lum(p) / 255.0;
							boolean edge = edge(solid, w, h, x, y, ext);
							c = material(el, l, gx, gy, f, edge, seed);
							if (rimIn < 0.07 && in < 1.1) c = mix(c, rim(el), 0.75);
						} else if (rimOut < 0.06 && outF > -0.1 && outF < 1.1) {
							c = mix(c, rim(el), 0.55); // the element breaking away
						}
						out.setPixel(x, oy + y, (a << 24) | c);
						continue;
					}
					// ---- empty pixel: crust / flames / sparks around the covered part + particles
					int c = 0;
					if (active) {
						c = around(el, solid, w, h, x, y, gx, gy, f, u, minD, span, in, outF, seed);
						if (c == 0) c = particle(el, gx, gy, f, (int) Math.ceil(16 * h / (double) w), seed);
					}
					out.setPixel(x, oy + y, c);
				}
		}
		return out;
	}

	/**
	 * Blocks and big entity textures (anchor, glowstone, obsidian, shield): a soft wave of the element runs
	 * diagonally over the texture with a bright edge and sparkles, then the texture rests for a moment.
	 * @param grid art pixels per row (16 for blocks, 64 for entity textures)
	 */
	static NativeImage animateBlock(NativeImage img, Element el, int seed, int grid) {
		int w = img.getWidth(), h = img.getHeight();
		double u = Math.max(1, w / (double) grid);
		int gw = (int) Math.round(w / u), gh = (int) Math.round(h / u);
		NativeImage out = new NativeImage(w, h * FRAMES, true);
		for (int f = 0; f < FRAMES; f++) {
			double pos = -0.35 + 1.75 * f / (FRAMES - 1.0);
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int p = img.getPixel(x, y);
					int a = p >>> 24;
					if (a < 10) {
						out.setPixel(x, f * h + y, p);
						continue;
					}
					int gx = (int) (x / u), gy = (int) (y / u);
					double d = (gx + (gh - 1 - gy)) / (double) Math.max(1, gw + gh - 2) + (rnd(gx, gy, seed) - 0.5) * 0.08;
					double band = Math.exp(-Math.pow((d - pos) * 4.5, 2));
					double l = BuiltinPacks.lum(p) / 255.0;
					int c = p & 0xFFFFFF;
					if (band > 0.02) c = mix(c, material(el, l, gx, gy, f, false, seed), band * 0.85);
					if (Math.abs(d - pos) < 0.035) c = mix(c, rim(el), 0.6);
					if (band > 0.25 && rnd(gx, gy, seed + f * 13) > 0.975) c = rim(el);
					out.setPixel(x, f * h + y, (a << 24) | c);
				}
		}
		return out;
	}

	private static boolean edge(boolean[] solid, int w, int h, int x, int y, int r) {
		for (int[] dd : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
			int nx = x + dd[0], ny = y + dd[1];
			if (nx < 0 || ny < 0 || nx >= w || ny >= h || !solid[ny * w + nx]) return true;
		}
		return false;
	}

	private static int rim(Element el) {
		return switch (el) {
			case PLASMA -> 0xFFD6FF;
			case FIRE -> 0xFFF2A0;
			case VOID -> 0xE0B8FF;
			case ENERGY -> 0xE6FFFC;
			case ICE -> 0xFFFFFF;
		};
	}

	/** Item colour while the element covers it. */
	private static int material(Element el, double l, int gx, int gy, int f, boolean edge, int seed) {
		switch (el) {
			case ICE -> {
				double facet = rnd(gx / 2, gy / 2, seed + 5) * 0.18;
				int c = col(new int[]{0x0A4A52, 0x1E9EA6, 0x5FE0DC, 0xC8FFFA, 0xFFFFFF}, 0.05 + l * 0.72 + facet);
				if (edge) c = mix(c, 0xE8FFFD, 0.45);
				double tw = rnd(gx, gy, seed + 9);
				if (tw > 0.9 && (f + (int) (tw * 40)) % 8 < 2) c = 0xFFFFFF;
				return c;
			}
			case FIRE -> {
				double n = noise(gx * 0.5, gy * 0.5 + f * 0.8, seed) * 0.6 + noise(gx, gy + f * 1.6, seed + 1) * 0.4;
				int c = col(new int[]{0x5A0A00, 0xD02800, 0xFF8A00, 0xFFE680}, l * 0.45 + n * 0.55);
				if (edge) c = mix(c, 0xFFD040, 0.4);
				return c;
			}
			case PLASMA -> {
				double sw = Math.sin(gx * 0.9 - gy * 0.6 + f * 0.7) * 0.5 + 0.5;
				int c = col(new int[]{0x2A0050, 0x8A1FE0, 0xFF4FD8, 0xFFE0FF}, l * 0.5 + sw * 0.4);
				if (Math.abs(Math.sin(gx * 1.3 + gy * 0.8 + f * 1.1) + Math.cos(gy * 1.1 - f * 0.9)) < 0.05) c = 0xFFFFFF; // crackle
				if (edge) c = mix(c, 0xFF9AF0, 0.5);
				return c;
			}
			case VOID -> {
				int c = col(new int[]{0x030106, 0x120822, 0x2A0F45}, l * 0.9);
				if (edge) c = mix(0x7A2BFF, 0xE0B8FF, 0.5 + 0.5 * Math.sin(f * 0.6 + gx * 0.4));
				double st = rnd(gx, gy, seed + 3);
				if (!edge && st > 0.9 && (f + (int) (st * 40)) % 10 < 2) c = 0xD9B8FF;
				return c;
			}
			default -> { // ENERGY
				double band = Math.exp(-Math.pow(((gx + 15 - gy) / 30.0 - ((f % 10) / 10.0 * 1.6 - 0.3)) * 6, 2));
				int c = col(new int[]{0x04201E, 0x0E9C92, 0x35F2DD, 0xE6FFFC}, l * 0.55 + band * 0.45);
				if (edge) c = mix(c, 0x8FFFF2, 0.6);
				return c;
			}
		}
	}

	/** Pixels just outside the item next to the covered part: ice crust, flames, sparks, wisps, glow. */
	private static int around(Element el, boolean[] solid, int w, int h, int x, int y, int gx, int gy, int f, double u,
			int minD, double span, double in, double outF, int seed) {
		int r = (int) Math.max(1, Math.round(u));
		int reach = el == Element.FIRE ? 3 : 1;
		// nearest covered solid pixel (for fire: below it, flames go up)
		for (int k = 1; k <= reach; k++) {
			int[][] dirs = el == Element.FIRE ? new int[][]{{0, k * r}, {r, k * r}, {-r, k * r}}
					: new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}};
			for (int[] dd : dirs) {
				int nx = x + dd[0], ny = y + dd[1];
				if (nx < 0 || ny < 0 || nx >= w || ny >= h || !solid[ny * w + nx]) continue;
				double d = (nx + (h - 1 - ny) - minD) / span + (rnd((int) (nx / u), (int) (ny / u), seed) - 0.5) * 0.14;
				if (!(d < in && d > outF)) continue;
				double q = rnd(gx, gy, seed + 40 + (el == Element.ICE ? 0 : f));
				switch (el) {
					case ICE -> {
						if (q > 0.45) return 0xFF000000 | (q > 0.8 ? 0xFFFFFF : 0xB9F7F3);
					}
					case FIRE -> {
						double fl = noise(gx * 0.7, gy * 0.7 + f * 1.3, seed + 7);
						if (fl > 0.35 + k * 0.12) return 0xFF000000 | col(new int[]{0xD02800, 0xFF8A00, 0xFFE680}, 1 - k / 3.0 + fl * 0.3);
					}
					case PLASMA -> {
						if (q > 0.72) return 0xFF000000 | (q > 0.9 ? 0xFFFFFF : 0xFF6FE0);
					}
					case VOID -> {
						if (q > 0.55) return (q > 0.85 ? 0xFF2A0F45 : 0xCC0A0412);
					}
					case ENERGY -> {
						if (q > 0.6) return (q > 0.88 ? 0xFFE6FFFC : 0xB035F2DD);
					}
				}
				return 0;
			}
		}
		return 0;
	}

	/** Free particles in the empty space while the element is active. */
	private static int particle(Element el, int gx, int gy, int f, int gh, int seed) {
		int n = 7;
		for (int i = 0; i < n; i++) {
			double sx = rnd(i, 1, seed + 100), sy = rnd(i, 2, seed + 100), sp = 0.4 + rnd(i, 3, seed + 100) * 0.5;
			int life = f - 9 - i * 2;
			if (life < 0 || life > 22) continue;
			int px, py;
			switch (el) {
				case ICE -> { // snowflakes falling
					px = (int) (sx * 16) + (int) Math.round(Math.sin((life + i) * 0.5));
					py = Math.floorMod((int) (sy * 8 + life * sp), gh);
				}
				case FIRE -> { // embers rising
					px = (int) (sx * 16) + (int) Math.round(Math.sin((life + i) * 0.7) * 0.8);
					py = Math.floorMod((int) (16 - sy * 6 - life * (sp + 0.3)), gh);
				}
				case PLASMA -> { // sparks jumping around
					int hop = life / 3;
					px = (int) (rnd(i, hop, seed + 200) * 16);
					py = (int) (rnd(hop, i, seed + 201) * gh);
				}
				case VOID -> { // specks pulled into the item
					double a = sx * Math.PI * 2 + life * 0.15, rad = 8 * (1 - life / 22.0) + 1;
					px = (int) Math.round(7.5 + Math.cos(a) * rad);
					py = (int) Math.round(7.5 + Math.sin(a) * rad);
				}
				default -> { // ENERGY: sparks orbiting
					double a = sx * Math.PI * 2 + life * 0.35, rad = 6.5 + sy * 1.5;
					px = (int) Math.round(7.5 + Math.cos(a) * rad);
					py = (int) Math.round(7.5 + Math.sin(a) * rad * 0.9);
				}
			}
			if (px == gx && py == gy) {
				return 0xFF000000 | switch (el) {
					case ICE -> i % 2 == 0 ? 0xFFFFFF : 0xB9F7F3;
					case FIRE -> i % 2 == 0 ? 0xFFD040 : 0xFF7A00;
					case PLASMA -> i % 2 == 0 ? 0xFFFFFF : 0xFF6FE0;
					case VOID -> i % 2 == 0 ? 0xB46CFF : 0x5A1FB0;
					case ENERGY -> i % 2 == 0 ? 0xE6FFFC : 0x35F2DD;
				};
			}
		}
		return 0;
	}

	/** Smooth value noise (no tiling needed: the strips do not loop through the noise). */
	private static double noise(double x, double y, int seed) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		double fx = x - ix, fy = y - iy;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = rnd(ix, iy, seed), b = rnd(ix + 1, iy, seed), c = rnd(ix, iy + 1, seed), d = rnd(ix + 1, iy + 1, seed);
		return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
	}

	private V1Packs() {
	}
}
