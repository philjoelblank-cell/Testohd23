package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * "Testohd23 V2" packs: every pack has its own animation that never stops (lightning, liquid, glitch,
 * stars, code rain, heartbeat, hologram). Weapons, tools and armor keep their normal colours (only the effect
 * is coloured); golden apples and pearls stay completely inside their pixels.
 */
final class V2Style {
	enum Anim {
		LIGHTNING, LIQUID, GLITCH, STARS, RAIN, HEARTBEAT, HOLO, SHEEN,
		// V3
		AURORA, VORTEX, FLAME, TIDE, PRISM, TRACE, WARP, DREAM
	}

	/** a = dark / main colour, b = effect colour, glow = brightest light, c = second effect colour (V3). */
	record Palette(Anim anim, int a, int b, int glow, int c) {
		Palette(Anim anim, int a, int b, int glow) {
			this(anim, a, b, glow, b);
		}
	}

	/** Palette of the pack being generated right now (null = not a V2 pack). */

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

	/** Smooth value noise on a lattice that repeats every 8 cells (so scrolling by 8 cells loops). */
	private static double vnoise(double x, double y, int seed) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
		double fx = x - x0, fy = y - y0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = rnd(Math.floorMod(x0, 8), Math.floorMod(y0, 8), seed), b = rnd(Math.floorMod(x0 + 1, 8), Math.floorMod(y0, 8), seed);
		double c = rnd(Math.floorMod(x0, 8), Math.floorMod(y0 + 1, 8), seed), d = rnd(Math.floorMod(x0 + 1, 8), Math.floorMod(y0 + 1, 8), seed);
		return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
	}

	/** Distance behind a light that runs once around the centre per loop (0 = at the light, 1 = just ahead of it). */
	private static double behind(double x, double y, double t) {
		double ang = Math.atan2(y - 8, x - 8) / (2 * Math.PI);
		return (((t - ang) % 1) + 1) % 1;
	}

	/** TRACE: comet tail strength on the outline, two comets opposite each other. */
	static double comet(double x, double y, double t) {
		double d = Math.min(behind(x, y, t), behind(x, y, t + 0.5));
		return d < 0.3 ? Math.pow(1 - d / 0.3, 1.6) : 0;
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
					if (r > (layer == 0 ? 0.95 : 0.93)) best = Math.max(best, layer == 0 ? 1 : 0.6);
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
			case AURORA -> { // northern lights: three curtains waving sideways, colours drifting
				double v = 0;
				for (int i = 0; i < 3; i++) {
					double cx = 3.5 + i * 4.5 + 2.4 * Math.sin(2 * Math.PI * (t + y / 20.0) + i * 2.1) + 1.1 * Math.sin(2 * Math.PI * 2 * t + y * 0.55 + i);
					double d = x - cx;
					v += Math.exp(-d * d / 2.4) * (0.65 + 0.35 * Math.sin(2 * Math.PI * (t * 2 + i / 3.0) + y * 0.4));
				}
				v = Math.min(1, v);
				double hue = 0.5 + 0.5 * Math.sin(2 * Math.PI * t + x * 0.35 - y * 0.15);
				int light = mix(p.b, p.c, hue);
				int base = col(new int[]{p.a, mix(p.a, p.b, 0.35)}, l);
				c = mix(mix(base, light, v), p.glow, Math.max(0, v - 0.75) * 2.2);
				s = 0.3 + v * 0.7;
			}
			case VORTEX -> { // three spiral arms turning around a bright core
				double dx = x - 8, dy = y - 8, r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
				double v = 0.5 + 0.5 * Math.sin(3 * ang + r * 0.85 - 2 * Math.PI * t * 3);
				v = v * v;
				double core = Math.exp(-r * r / 7.0) * (0.75 + 0.25 * Math.sin(2 * Math.PI * t * 2));
				c = mix(col(new int[]{p.a, p.b, p.c}, v * 0.85 + l * 0.15), p.glow, Math.min(1, core + Math.max(0, v - 0.85) * 2));
				s = Math.min(1, 0.35 + v * 0.5 + core * 0.5);
			}
			case FLAME -> { // fire climbing up the item, hottest at the bottom
				double n = vnoise(x / 2.0, y / 2.0 + t * 16, seed) * 0.6 + vnoise(x, y + t * 24, seed + 4) * 0.4;
				double heat = Math.max(0, Math.min(1, Math.pow(y / 16.0, 1.15) * 1.15 + (n - 0.5) * 1.1));
				c = col(new int[]{p.a, mix(p.a, p.b, 0.7), p.b, p.c, p.glow}, heat);
				s = 0.4 + heat * 0.6;
			}
			case TIDE -> { // water sloshing left and right inside the item
				double surf = 8.5 + 3.2 * Math.sin(2 * Math.PI * t) * ((x - 8) / 8.0) + 1.1 * Math.sin(2 * Math.PI * 2 * t + x * 0.75);
				double d = y - surf;
				if (d < -0.8) { // air
					c = col(new int[]{p.a, mix(p.a, p.b, 0.3)}, l);
					s = 0.3;
				} else if (d < 0.6) { // foam line
					c = p.glow;
					s = 1;
				} else {
					double depth = Math.min(1, d / 7.0);
					double caust = Math.pow(0.5 + 0.5 * Math.sin(x * 1.3 + 1.6 * Math.sin(y * 0.8 + 2 * Math.PI * 2 * t) + 2 * Math.PI * 3 * t), 3);
					// bubbles rising in some columns
					int colx = (int) x;
					double by = 16 - (((t * 2 + rnd(colx, 2, seed)) % 1) * 12);
					boolean bubble = rnd(colx, 5, seed) > 0.7 && Math.abs(y - by) < 0.6 && by > surf + 1;
					c = bubble ? p.glow : mix(col(new int[]{p.c, p.b, mix(p.b, p.a, 0.55)}, depth), p.glow, caust * 0.35 * (1 - depth));
					s = 0.85;
				}
			}
			case PRISM -> { // crystal facets, a light wanders through them one by one
				double d1 = 99, d2 = 99;
				int cell = 0;
				double ccx = 0, ccy = 0;
				for (int i = 0; i < 9; i++) {
					double px = rnd(i, 1, seed + 21) * 16, py = rnd(i, 2, seed + 21) * 16, d = Math.hypot(x - px, y - py);
					if (d < d1) { d2 = d1; d1 = d; cell = i; ccx = px; ccy = py; } else if (d < d2) d2 = d;
				}
				double ph = (ccx + ccy) / 32.0, dt = Math.abs((((t - ph) % 1) + 1.5) % 1 - 0.5);
				double flash = Math.exp(-dt * dt * 260);
				boolean seam = d2 - d1 < 0.55;
				int shade = col(new int[]{mix(p.a, p.b, 0.5), mix(p.a, p.b, 0.8), p.b, mix(p.b, p.c, 0.6)}, rnd(cell, 3, seed + 21) * 0.85 + l * 0.15);
				c = mix(seam ? mix(shade, p.c, 0.6) : shade, p.glow, flash * 0.8);
				s = 0.7 + flash * 0.3;
			}
			case TRACE -> { // calm inside (the comets run on the outline, see animate)
				double breathe = 0.5 + 0.5 * Math.sin(2 * Math.PI * t * 2 - (x + y) * 0.25);
				c = col(new int[]{mix(p.a, p.b, 0.45), p.b, mix(p.b, p.c, 0.7)}, l * 0.6 + breathe * 0.35);
				s = 0.7;
			}
			case WARP -> { // hyperspace: rings and star streaks flying out of the middle
				double dx = x - 8, dy = y - 8, r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
				double ringPos = ((r / 6.0 - t * 3) % 1 + 1) % 1;
				double ring = Math.exp(-Math.pow(ringPos - 0.5, 2) * 40) * Math.min(1, r / 4.0);
				int sector = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * 20);
				double pos = Math.pow((t * 2 + rnd(sector, 9, seed)) % 1, 1.7) * 13, len = 0.8 + pos * 0.35;
				double streak = rnd(sector, 4, seed) > 0.45 && r <= pos && r > pos - len ? (r - (pos - len)) / len : 0;
				int base = col(new int[]{p.a, mix(p.a, p.b, 0.45)}, l * 0.3 + Math.min(1, r / 10.0) * 0.7);
				c = mix(mix(base, mix(p.b, p.c, ring), ring * 0.95), p.glow, streak);
				s = Math.min(1, 0.6 + ring * 0.3 + streak * 0.4);
			}
			case DREAM -> { // soft colours flowing through each other, a sparkle now and then
				double h = (((x * 0.6 + y) / 22.0 + t + 0.08 * Math.sin(2 * Math.PI * 2 * t + x * 0.5)) % 1 + 1) % 1;
				int flow = col(new int[]{p.b, p.c, p.glow, p.b}, h);
				double r = rnd((int) x, (int) y, seed + 31);
				double tw = r > 0.94 ? Math.max(0, 1 - Math.abs((((t - r * 7) % 1) + 1.5) % 1 - 0.5) * 9) : 0;
				c = mix(mix(flow, p.a, (1 - l) * 0.25), 0xFFFFFF, tw * 0.9 + l * 0.12);
				s = 0.75 + tw * 0.25;
			}
			case SHEEN -> { // calm metal: soft light band sliding across now and then
				double pos = (x + y) / 2.0 - (t * 1.6 - 0.3) * 24;
				double band = Math.exp(-pos * pos / 6.0);
				int base = col(new int[]{p.a, p.b}, l);
				c = mix(base, p.glow, band * 0.85);
				s = 0.2 + band * 0.6;
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
			case FLAME -> { // sparks flying up from the top of the item
				int cx = (int) ax;
				if (rnd(cx, 7, seed) > 0.6) {
					int top = -1;
					for (int yy = 0; yy < h && top < 0; yy++) if (solid[yy * w + Math.min(w - 1, (int) (cx * u + u / 2))]) top = yy;
					if (top >= 0) {
						double dy = top / u - ay, spark = ((t * 2 + rnd(cx, 3, seed)) % 1) * 6;
						if (dy > 0 && Math.abs(dy - spark) < 0.55) return 0xFF000000 | (spark < 2.5 ? p.glow : p.c);
					}
				}
			}
			case TRACE -> { // glow next to the comet heads
				if (comet(ax, ay, t) > 0.6 && near(solid, w, h, x, y, ext)) return 0xFF000000 | p.c;
			}
			case VORTEX -> { // specks circling the item
				double dx = ax - 8, dy = ay - 8, r = Math.hypot(dx, dy);
				double v = Math.sin(3 * Math.atan2(dy, dx) + r * 0.85 - 2 * Math.PI * t * 3);
				if (v > 0.93 && rnd((int) ax, (int) ay, seed + 5) > 0.5 && near(solid, w, h, x, y, ext * 2)) return 0xFF000000 | p.c;
			}
			case AURORA, DREAM -> { // soft twinkles around the item
				double r = rnd((int) ax, (int) ay, seed + 77);
				int ph = (int) (r * FRAMES);
				if (r > 0.94 && Math.abs(f - ph) <= 1 && near(solid, w, h, x, y, ext * 2)) return 0xFF000000 | (f == ph ? p.glow : p.c);
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
							if (edge && p.anim == Anim.TRACE) c = mix(c, p.glow, Math.min(1, comet(x / u, y / u, f / (double) FRAMES) * 1.15));
						} else {
							// keep the item's shading and outline readable under the effect
							c = mix(effColor(e), 0x000000, (1 - l) * 0.35 * (1 - effStrength(e)));
							if (isEdge(solid, w, h, x, y, ext)) {
								if (p.anim == Anim.TRACE) { // outline in the pack colour, the comets light it up
									double tail = comet(x / u, y / u, f / (double) FRAMES);
									c = mix(mix(c, p.a, 0.5), tail > 0.85 ? 0xFFFFFF : mix(p.c, p.glow, tail), Math.min(1, tail * 1.3));
								} else c = mix(c, p.glow, 0.3);
							}
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
