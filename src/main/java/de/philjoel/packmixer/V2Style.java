package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * "Testohd23 V2" look: instead of the element taking over now and then (V1), the items are always alive –
 * two colours flowing through them, a shine sweeping over, and two lights running around the outline like
 * comets. Weapons, tools and armor keep their normal colours (only edges, shine and the running lights are
 * coloured); golden apples and pearls stay completely inside their pixels.
 */
final class V2Style {
	/** a = main colour, b = second colour, glow = light colour of the comets / shine. */
	record Palette(int a, int b, int glow) {
	}

	/** Palette of the pack being generated right now (null = not a V2 pack). */
	static Palette current;

	static final int FRAMES = V1Packs.FRAMES;

	private static int mix(int a, int b, double t) {
		return BuiltinPacks.gradientAt(new int[]{a, b}, Math.max(0, Math.min(1, t)));
	}

	private static int col(int[] st, double t) {
		return BuiltinPacks.gradientAt(st, Math.max(0, Math.min(1, t)));
	}

	private static int dark(int c, double k) {
		return mix(c, 0x000000, k);
	}

	private static double rnd(int x, int y, int s) {
		return (BuiltinPacks.hash(x * 91 + s * 37, y * 173 - s * 19) & 0xFFFF) / 65535.0;
	}

	/** Colour flowing between a and b along the diagonal, moving with time. */
	private static double flow(double x, double y, double size, int f) {
		return 0.5 + 0.5 * Math.sin(2 * Math.PI * (f / (double) FRAMES) - (x + y) / size * Math.PI * 1.6);
	}

	/** Shine band sweeping once per loop (0 = none, 1 = centre). */
	private static double shine(double x, double y, double size, int f) {
		double pos = -0.3 + 1.6 * f / (FRAMES - 1.0);
		double d = (x + (size - y)) / (2 * size);
		return Math.exp(-Math.pow((d - pos) * 9, 2));
	}

	static NativeImage animate(NativeImage img, Palette p, int seed, V1Packs.Mode mode) {
		int w = img.getWidth(), h = img.getHeight();
		double u = Math.max(1, w / 16.0);
		boolean[] solid = new boolean[w * h];
		double cx = 0, cy = 0;
		int n = 0;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++)
				if ((img.getPixel(x, y) >>> 24) >= 10) {
					solid[y * w + x] = true;
					cx += x;
					cy += y;
					n++;
				}
		NativeImage out = new NativeImage(w, h * FRAMES, true);
		if (n == 0) {
			for (int f = 0; f < FRAMES; f++) for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setPixel(x, f * h + y, 0);
			return out;
		}
		cx /= n;
		cy /= n;
		int ext = (int) Math.max(1, Math.round(u));
		int[] ramp = {dark(p.a, 0.75), dark(p.a, 0.25), p.a, p.b, mix(p.b, 0xFFFFFF, 0.55)};
		for (int f = 0; f < FRAMES; f++) {
			double runner = 2 * Math.PI * f / FRAMES; // comet angle
			int oy = f * h;
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int px = img.getPixel(x, y);
					double fl = flow(x, y, w, f), sh = shine(x, y, w, f);
					if (solid[y * w + x]) {
						int a = px >>> 24;
						double l = BuiltinPacks.lum(px) / 255.0;
						boolean edge = isEdge(solid, w, h, x, y, ext);
						int c;
						if (mode == V1Packs.Mode.TOOL) {
							c = px & 0xFFFFFF; // own colours, coloured outline + shine
							if (edge) c = mix(c, mix(p.a, p.b, fl), 0.55);
							c = mix(c, p.glow, sh * 0.45);
						} else {
							c = col(ramp, l * 0.82 + fl * 0.18);
							if (edge) c = mix(c, mix(p.b, p.glow, fl), 0.35);
							c = mix(c, 0xFFFFFF, sh * 0.42);
							// tiny glints inside
							double g = rnd((int) (x / u), (int) (y / u), seed);
							int ph = (int) (g * FRAMES);
							if (g > 0.9 && Math.abs(f - ph) <= 1) c = mix(c, 0xFFFFFF, f == ph ? 0.85 : 0.4);
						}
						out.setPixel(x, oy + y, (a << 24) | c);
						continue;
					}
					int c = 0;
					if (mode != V1Packs.Mode.INSIDE && near(solid, w, h, x, y, ext)) {
						// two comets running around the outline (a and b), with a fading tail
						double ang = Math.atan2(y - cy, x - cx);
						double best = 0;
						int bc = 0;
						for (int k = 0; k < 2; k++) {
							double d = ang - (runner + k * Math.PI);
							d = Math.atan2(Math.sin(d), Math.cos(d)); // -pi..pi, tail behind (negative)
							double v = d > 0 ? Math.exp(-d * d * 60) : Math.exp(-d * d * 4);
							if (v > best) {
								best = v;
								bc = k == 0 ? mix(p.glow, p.a, -d) : mix(p.glow, p.b, -d);
							}
						}
						if (best > 0.35) c = (Math.min(255, (int) (255 * Math.min(1, best + 0.2))) << 24) | bc;
					}
					out.setPixel(x, oy + y, c);
				}
		}
		return out;
	}

	/** Blocks / entity textures: the two colours flow over the block, a shine sweeps over it, sparkles. */
	static NativeImage animateBlock(NativeImage img, Palette p, int seed, int grid) {
		int w = img.getWidth(), h = img.getHeight();
		double u = Math.max(1, w / (double) grid);
		NativeImage out = new NativeImage(w, h * FRAMES, true);
		for (int f = 0; f < FRAMES; f++)
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int px = img.getPixel(x, y);
					int a = px >>> 24;
					if (a < 10) {
						out.setPixel(x, f * h + y, px);
						continue;
					}
					int gx = (int) (x / u), gy = (int) (y / u);
					double fl = flow(gx, gy, grid, f), sh = shine(gx, gy, grid, f);
					int c = mix(px & 0xFFFFFF, mix(p.a, p.b, fl), 0.22 + 0.12 * fl);
					c = mix(c, p.glow, sh * 0.55);
					if (sh > 0.3 && rnd(gx, gy, seed + f * 7) > 0.97) c = 0xFFFFFF;
					out.setPixel(x, f * h + y, (a << 24) | c);
				}
		return out;
	}

	private static boolean isEdge(boolean[] solid, int w, int h, int x, int y, int r) {
		for (int[] d : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
			int nx = x + d[0], ny = y + d[1];
			if (nx < 0 || ny < 0 || nx >= w || ny >= h || !solid[ny * w + nx]) return true;
		}
		return false;
	}

	private static boolean near(boolean[] solid, int w, int h, int x, int y, int r) {
		for (int dy = -r; dy <= r; dy++)
			for (int dx = -r; dx <= r; dx++) {
				int nx = x + dx, ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < w && ny < h && solid[ny * w + nx]) return true;
			}
		return false;
	}
}
