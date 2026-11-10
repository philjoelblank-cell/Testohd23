package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * "Testohd23 V2" packs: every pack has its own animation that never stops (lightning, liquid, glitch,
 * stars, code rain, heartbeat, hologram). Weapons, tools and armor keep their normal colours (only the effect
 * is coloured); golden apples and pearls stay completely inside their pixels.
 */
final class V2Style {
	enum Anim {
		LIGHTNING, LIQUID, GLITCH, STARS, RAIN, HEARTBEAT, HOLO
	}

	/** a = dark / main colour, b = effect colour, glow = brightest light. */
	record Palette(Anim anim, int a, int b, int glow) {
	}

	/** Palette of the pack being generated right now (null = not a V2 pack). */
	static Palette current;

	static final int FRAMES = V1Packs.FRAMES;

	static int mix(int a, int b, double t) {
		return BuiltinPacks.gradientAt(new int[]{a, b}, Math.max(0, Math.min(1, t)));
	}

	private static int col(int[] st, double t) {
		return BuiltinPacks.gradientAt(st, Math.max(0, Math.min(1, t)));
	}

	private static double rnd(int x, int y, int s) {
		return (BuiltinPacks.hash(x * 91 + s * 37, y * 173 - s * 19) & 0xFFFF) / 65535.0;
	}

	private static int hsv(double h, double s, double v) {
		h = ((h % 1) + 1) % 1 * 6;
		int i = (int) h;
		double f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		double r, g, b;
		switch (i) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	/** Lightning bolt: a jagged line from top to bottom, new path every 10 frames; returns 0..1. */
	private static double bolt(double x, double y, int f, int frames, int seed) {
		int period = Math.max(4, frames / 4), k = f / period, age = f % period;
		if (age > 2) return 0;
		double flash = age == 0 ? 1 : age == 1 ? 0.7 : 0.35;
		double bx = 3 + rnd(k, 1, seed) * 10;
		for (int yy = 0; yy <= (int) y; yy++) bx += (rnd(k, yy, seed + 5) - 0.5) * 2.6;
		double d = Math.abs(x - bx);
		return d < 0.7 ? flash : d < 1.6 ? flash * 0.35 : 0;
	}

	/** Liquid blobs (metaballs) moving in loops; returns field value. */
	private static double blobs(double x, double y, int f, int frames, int seed) {
		double t = 2 * Math.PI * f / frames, v = 0;
		for (int i = 0; i < 3; i++) {
			double bx = 8 + Math.cos(t * (i == 1 ? -1 : 1) + i * 2.1 + seed) * (3 + i);
			double by = 8 + Math.sin(t * (i == 2 ? 2 : 1) + i * 1.3) * (4 - i * 0.7);
			v += 4.2 / ((x - bx) * (x - bx) + (y - by) * (y - by) + 0.6);
		}
		return v;
	}

	/**
	 * Effect colour and strength (0..1) at art position (x,y) 0..16 for frame f of {@code frames}.
	 * base = the item's own colour there (for effects that tint), l = its brightness.
	 */
	static long effect(Palette p, double x, double y, int f, int frames, double l, int seed) {
		double t = f / (double) frames;
		int c;
		double s;
		switch (p.anim) {
			case LIGHTNING -> {
				double b = bolt(x, y, f, frames, seed) + bolt(16 - x, y, (f + frames / 8) % frames, frames, seed + 3) * 0.7;
				double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * t * 2);
				c = b > 0.5 ? p.glow : mix(col(new int[]{p.a, p.b, p.glow}, l * 0.8 + 0.1 * pulse), p.glow, b);
				s = Math.min(1, b);
			}
			case LIQUID -> {
				double v = blobs(x, y, f, frames, seed);
				double in = Math.max(0, Math.min(1, (v - 1.0) * 3));
				boolean rim = v > 0.9 && v < 1.15;
				c = rim ? p.glow : mix(col(new int[]{p.a, mix(p.a, p.b, 0.35)}, l), mix(p.b, p.glow, l * 0.5), in);
				s = rim ? 0.9 : in * 0.75;
			}
			case GLITCH -> {
				int row = (int) y;
				boolean glitch = rnd(f / 2, row / 3, seed) > 0.78 && (f % 10) < 6;
				double scan = ((y - t * 16 * 2) % 16 + 16) % 16 < 1.2 ? 1 : 0;
				int base = col(new int[]{p.a, mix(p.a, p.b, 0.6), p.b}, l);
				c = glitch ? (rnd(f, row, seed + 9) > 0.5 ? p.glow : p.b) : mix(base, p.glow, scan * 0.55);
				s = glitch ? 0.85 : scan * 0.5;
			}
			case STARS -> {
				// two star layers drifting diagonally (portal look)
				double best = 0;
				for (int layer = 0; layer < 2; layer++) {
					double sp = layer == 0 ? 16 : 32;
					int sx = (int) Math.floor(x + t * sp), sy = (int) Math.floor(y + t * sp * 0.5);
					double r = rnd(Math.floorMod(sx, 16), Math.floorMod(sy, 16), seed + layer * 11);
					if (r > (layer == 0 ? 0.9 : 0.86)) best = Math.max(best, layer == 0 ? 1 : 0.6);
				}
				double neb = 0.5 + 0.5 * Math.sin(x * 0.5 + y * 0.3 + t * 2 * Math.PI);
				int base = col(new int[]{p.a, mix(p.a, p.b, 0.45)}, l * 0.7 + neb * 0.3);
				c = mix(base, p.glow, best);
				s = 0.25 + best * 0.75;
			}
			case RAIN -> {
				int colx = (int) x;
				double speed = 0.8 + rnd(colx, 0, seed) * 0.8, off = rnd(colx, 1, seed) * 16;
				double head = ((t * 16 * speed * 2 + off) % 20) - 2;
				double d = head - y;
				double tr = d >= 0 && d < 7 ? 1 - d / 7 : 0;
				int base = col(new int[]{p.a, mix(p.a, p.b, 0.4)}, l);
				c = d >= 0 && d < 1 ? p.glow : mix(base, p.b, tr);
				s = d >= 0 && d < 1 ? 1 : tr * 0.8;
			}
			case HEARTBEAT -> {
				// "ba-dum": two pulses, then rest; a ring runs out from the middle
				double b1 = Math.exp(-Math.pow((t - 0.08) * 18, 2)), b2 = Math.exp(-Math.pow((t - 0.24) * 18, 2)) * 0.8;
				double beat = b1 + b2;
				double r = Math.hypot(x - 8, y - 8), ringR = (t % 0.5) * 2 * 14;
				double ring = Math.exp(-Math.pow(r - ringR, 2) / 1.5) * (1 - (t % 0.5) * 2);
				c = mix(col(new int[]{p.a, p.b, p.glow}, l * 0.75 + beat * 0.3), p.glow, ring);
				s = Math.min(1, beat * 0.6 + ring);
			}
			default -> { // HOLO: rainbow sheen by angle + moving scanlines
				double h = (x + y) / 32.0 + t;
				int rainbow = hsv(h, 0.45, 1);
				double scan = (((int) (y + t * 32)) % 4 == 0) ? 0.25 : 0;
				c = mix(mix(p.a, rainbow, 0.55 + l * 0.2), 0xFFFFFF, scan + l * 0.15);
				s = 0.55 + scan;
			}
		}
		return ((long) Math.round(Math.max(0, Math.min(1, s)) * 1000) << 32) | (c & 0xFFFFFFL);
	}

	static int effColor(long e) {
		return (int) (e & 0xFFFFFF);
	}

	static double effStrength(long e) {
		return (e >>> 32) / 1000.0;
	}

	/** Effect outside the item (FULL / TOOL only): 0 = nothing, else ARGB. */
	private static int outside(Palette p, boolean[] solid, int w, int h, int x, int y, double u, int f, int seed) {
		double ax = x / u, ay = y / u, t = f / (double) FRAMES;
		int ext = (int) Math.max(1, Math.round(u));
		switch (p.anim) {
			case LIGHTNING -> { // sparks next to the item while a bolt flashes
				if (f % 10 <= 1 && near(solid, w, h, x, y, ext) && rnd((int) ax, (int) ay, seed + f) > 0.75) return 0xFF000000 | p.glow;
			}
			case LIQUID -> { // drops falling from the item
				int cx = (int) ax;
				if (rnd(cx, 7, seed) > 0.72) {
					int bottom = -1;
					for (int yy = h - 1; yy >= 0 && bottom < 0; yy--) if (solid[yy * w + Math.min(w - 1, (int) (cx * u + u / 2))]) bottom = yy;
					if (bottom >= 0) {
						double dy = (ay - bottom / u);
						double drop = ((t + rnd(cx, 3, seed)) % 1) * 7;
						if (dy > 0 && Math.abs(dy - drop) < 0.6) return 0xFF000000 | p.b;
					}
				}
			}
			case GLITCH -> { // ghost copy shifted sideways on glitch frames
				if ((f % 10) < 6 && rnd(f / 2, (int) (ay / 3), seed) > 0.78) {
					int sx = x + (int) Math.round(u * 2);
					if (sx < w && solid[y * w + sx]) return 0xFF000000 | ((f & 1) == 0 ? p.glow : p.b);
				}
			}
			case HEARTBEAT -> { // the ring goes on outside the item
				double r = Math.hypot(ax - 8, ay - 8), ringR = (t % 0.5) * 2 * 14;
				double ring = Math.exp(-Math.pow(r - ringR, 2) / 0.8) * (1 - (t % 0.5) * 2);
				if (ring > 0.45 && near(solid, w, h, x, y, ext * 2)) return 0xFF000000 | p.glow;
			}
			case STARS -> { // twinkles around the item
				double r = rnd((int) ax, (int) ay, seed + 77);
				int ph = (int) (r * FRAMES);
				if (r > 0.93 && Math.abs(f - ph) <= 1 && near(solid, w, h, x, y, ext * 2)) return 0xFF000000 | (f == ph ? 0xFFFFFF : p.b);
			}
			default -> {
			}
		}
		return 0;
	}

	static NativeImage animate(NativeImage img, Palette p, int seed, V1Packs.Mode mode) {
		int w = img.getWidth(), h = img.getHeight();
		double u = Math.max(1, w / 16.0);
		boolean[] solid = new boolean[w * h];
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) solid[y * w + x] = (img.getPixel(x, y) >>> 24) >= 10;
		int ext = (int) Math.max(1, Math.round(u));
		NativeImage out = new NativeImage(w, h * FRAMES, true);
		for (int f = 0; f < FRAMES; f++) {
			int oy = f * h;
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int px = img.getPixel(x, y);
					if (solid[y * w + x]) {
						double l = BuiltinPacks.lum(px) / 255.0;
						long e = effect(p, x / u, y / u, f, FRAMES, l, seed);
						int c;
						if (mode == V1Packs.Mode.TOOL) {
							boolean edge = isEdge(solid, w, h, x, y, ext);
							c = mix(px & 0xFFFFFF, effColor(e), effStrength(e) * 0.8 + (edge ? 0.25 : 0));
						} else {
							// keep the item's shading and outline readable under the effect
							c = mix(effColor(e), 0x000000, (1 - l) * 0.35 * (1 - effStrength(e)));
							if (isEdge(solid, w, h, x, y, ext)) c = mix(c, p.glow, 0.3);
						}
						out.setPixel(x, oy + y, (px & 0xFF000000) | c);
						continue;
					}
					out.setPixel(x, oy + y, mode == V1Packs.Mode.INSIDE ? 0 : outside(p, solid, w, h, x, y, u, f, seed));
				}
		}
		return out;
	}

	/** Blocks / entity textures: same effect, softer, over the normal texture. */
	static NativeImage animateBlock(NativeImage img, Palette p, int seed, int grid) {
		int w = img.getWidth(), h = img.getHeight();
		double u = Math.max(1, w / (double) grid), k = 16.0 / grid;
		NativeImage out = new NativeImage(w, h * FRAMES, true);
		for (int f = 0; f < FRAMES; f++)
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int px = img.getPixel(x, y);
					if ((px >>> 24) < 10) {
						out.setPixel(x, f * h + y, px);
						continue;
					}
					double l = BuiltinPacks.lum(px) / 255.0;
					long e = effect(p, (x / u) * k, (y / u) * k, f, FRAMES, l, seed);
					int c = mix(px & 0xFFFFFF, effColor(e), 0.25 + effStrength(e) * 0.55);
					out.setPixel(x, f * h + y, (px & 0xFF000000) | c);
				}
		return out;
	}

	/** Palette of a V2 crystal design key ("c2_..."), or null. */
	static Palette ofCrystal(String key) {
		for (V1Packs.V1 v : V1Packs.PACKS) if (v.v2() != null && v.crystal().equals(key)) return v.v2();
		return null;
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
