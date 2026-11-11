package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Built-in texture packs "Made by Testohd23". They are generated on the player's PC from the
 * player's own vanilla textures (recoloured + hand-drawn pixel art), so no textures of other
 * people are shipped with the mod.
 */
public final class BuiltinPacks {
	/** Bump to regenerate the packs after changes. */
	public static final int VERSION = 19;

	enum Style {
		NORMAL, CANDY, FROST, TREE, RAINBOW, GALAXY, GRADIENT, PATTERN, DUO, V1XMAS;

		boolean christmas() {
			return this == CANDY || this == FROST || this == TREE || this == V1XMAS;
		}
	}

	/** Colour theme from a hue: ramp dark -> light, dark grey handles. */
	private static Theme hue(String name, String key, float hue, float sat, String crosshair) {
		int[] ramp = new int[5];
		float[] val = {0.13f, 0.32f, 0.58f, 0.82f, 1.0f};
		for (int i = 0; i < 5; i++) ramp[i] = hsv(hue, sat * (i == 4 ? 0.35f : i == 3 ? 0.75f : 1f), val[i]);
		return new Theme(name, key, Style.NORMAL, ramp, new int[]{0x111118, 0x2A2A35, 0x45455A}, ramp[3], crosshair,
				new int[]{ramp[0], ramp[2], ramp[4]});
	}

	private static Theme custom(String name, String key, Style style, int[] ramp, int[] handle, String crosshair) {
		return new Theme(name, key, style, ramp, handle, ramp[3], crosshair, new int[]{ramp[0], ramp[2], ramp[4]});
	}

	/** Colour gradient pack: items/blocks/hearts get a diagonal gradient through the given colours. */
	static final java.util.Map<String, int[]> GRADIENTS = new java.util.HashMap<>();

	private static Theme gradient(String name, String key, String crosshair, int... stops) {
		GRADIENTS.put(name, stops);
		int mid = stops[stops.length / 2];
		return new Theme(name, key, Style.GRADIENT,
				new int[]{0x202020, 0x505050, 0x909090, 0xC8C8C8, 0xFFFFFF}, new int[]{0x111118, 0x2A2A35, 0x45455A},
				mid, crosshair, new int[]{0x303030, 0x9A9A9A, 0xFFFFFF});
	}

	/** Duo packs: clean main colour, highlights shift into a matching second colour. */
	private static Theme duo(String name, String key, String crosshair, int main, int accent) {
		GRADIENTS.put(name, new int[]{main, accent});
		return new Theme(name, key, Style.DUO,
				new int[]{0x202020, 0x505050, 0x909090, 0xC8C8C8, 0xFFFFFF}, new int[]{0x111118, 0x2A2A35, 0x45455A},
				accent, crosshair, new int[]{0x303030, 0x9A9A9A, 0xFFFFFF});
	}

	/** Testohd23 V1 packs: clean duo colours + element animation (see V1Packs). */
	private static Theme v1(String name, String key, String crosshair, Style style, int main, int accent) {
		GRADIENTS.put(name, new int[]{main, accent});
		return new Theme(name, key, style,
				new int[]{0x202020, 0x505050, 0x909090, 0xC8C8C8, 0xFFFFFF}, new int[]{0x111118, 0x2A2A35, 0x45455A},
				accent, crosshair, new int[]{darken(main, 0.4), main, accent});
	}

	static void duo(NativeImage img, int main, int accent) {
		int[] ramp = {darken(main, 0.18), darken(main, 0.55), main, gradientAt(new int[]{main, accent}, 0.75), gradientAt(new int[]{accent, 0xFFFFFF}, 0.45)};
		int w = img.getWidth(), h = img.getHeight();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int a = p >>> 24;
				if (a < 10) continue;
				img.setPixel(x, y, (a << 24) | gradientAt(ramp, lum(p) / 255.0));
			}
	}

	/** Pattern packs: neon outline, stripes, two-tone, camo, checker, glitter, chrome. */
	private record Pat(String type, int[] colors) {
	}

	private static final java.util.Map<String, Pat> PATTERNS = new java.util.HashMap<>();

	private static Theme pattern(String name, String key, String crosshair, String type, int... colors) {
		PATTERNS.put(name, new Pat(type, colors));
		return new Theme(name, key, Style.PATTERN,
				new int[]{0x202020, 0x505050, 0x909090, 0xC8C8C8, 0xFFFFFF}, new int[]{0x111118, 0x2A2A35, 0x45455A},
				colors[0], crosshair, new int[]{0x303030, 0x9A9A9A, 0xFFFFFF});
	}

	private static int shadeRgb(int c, double k) {
		int r = (int) Math.min(255, ((c >> 16) & 0xFF) * k), g = (int) Math.min(255, ((c >> 8) & 0xFF) * k), b = (int) Math.min(255, (c & 0xFF) * k);
		return (r << 16) | (g << 8) | b;
	}

	static void pattern(NativeImage img, Pat pat) {
		int w = img.getWidth(), h = img.getHeight();
		int fh = Math.min(w, h);
		int u = Math.max(1, w / 16);
		int[] c = pat.colors();
		if (pat.type().equals("glitter")) {
			gradient(img, c);
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int p = img.getPixel(x, y);
					if ((p >>> 24) < 10) continue;
					int r = Math.floorMod(hash(x / u * 5 + 1, (y % fh) / u * 11 + 7), 100);
					if (r < 5) img.setPixel(x, y, 0xFFFFFFFF);
					else if (r < 9) img.setPixel(x, y, (p & 0xFF000000) | shadeRgb(p & 0xFFFFFF, 1.5));
				}
			return;
		}
		// copy of the alpha, needed for the neon outline
		boolean[] solid = new boolean[w * h];
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) solid[y * w + x] = (img.getPixel(x, y) >>> 24) >= 10;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int a = p >>> 24;
				if (a < 10) continue;
				double l = lum(p) / 255.0;
				int fy = y % fh;
				int px = x / u, py = fy / u;
				int col;
				switch (pat.type()) {
					case "neon" -> {
						boolean edge = false;
						for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int nx = x + d[0] * u, ny = fy + d[1] * u;
							if (nx < 0 || ny < 0 || nx >= w || ny >= fh || !solid[(y - fy + ny) * w + nx]) { edge = true; break; }
						}
						col = edge ? shadeRgb(c[0], 0.85 + 0.3 * l) : shadeRgb(c[0], 0.06 + 0.3 * l * l);
					}
					case "stripes" -> col = shadeRgb(c[Math.floorMod((px + py) / 2, c.length)], 0.4 + 0.8 * l);
					case "checker" -> col = shadeRgb(c[Math.floorMod(px / 2 + py / 2, 2)], 0.4 + 0.8 * l);
					case "split" -> col = shadeRgb(px + (15 - py) < 16 ? c[0] : c[1], 0.35 + 0.85 * l);
					case "camo" -> {
						int n = Math.floorMod(hash(px / 3, py / 3) + hash((px + 1) / 3 * 3, py / 2), c.length);
						col = shadeRgb(c[n], 0.55 + 0.6 * l);
					}
					case "chrome" -> col = gradientAt(c, 0.5 + 0.5 * Math.sin(l * 9 + (px + py) * 0.35));
					default -> col = p & 0xFFFFFF;
				}
				img.setPixel(x, y, (a << 24) | col);
			}
	}

	/** Colour at position t (0..1) of a multi-stop gradient. */
	static int gradientAt(int[] stops, double t) {
		t = Math.max(0, Math.min(1, t)) * (stops.length - 1);
		int i = Math.min(stops.length - 2, (int) t);
		double f = t - i;
		int a = stops[i], b = stops[i + 1];
		int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
		int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
		int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
		return (r << 16) | (g << 8) | bl;
	}

	/** Gradient from bottom-left (handle) to top-right (tip); shading comes from the original brightness. */
	static void gradient(NativeImage img, int[] stops) {
		int w = img.getWidth(), h = img.getHeight();
		int fh = Math.min(w, h); // animated strips: same gradient in every frame
		// stretch the gradient over the visible part, so small items show all colours too
		int minS = Integer.MAX_VALUE, maxS = Integer.MIN_VALUE;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if ((img.getPixel(x, y) >>> 24) < 10) continue;
				int d = x + (fh - 1 - y % fh);
				minS = Math.min(minS, d);
				maxS = Math.max(maxS, d);
			}
		if (minS == Integer.MAX_VALUE) return;
		double span = Math.max(1, maxS - minS);
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int a = p >>> 24;
				if (a < 10) continue;
				double l = lum(p) / 255.0;
				int c = gradientAt(stops, (x + (fh - 1 - y % fh) - minS) / span);
				double k = 0.35 + 0.85 * l;
				int r = (int) (((c >> 16) & 0xFF) * k), g = (int) (((c >> 8) & 0xFF) * k), b = (int) ((c & 0xFF) * k);
				if (l > 0.88) { // keep highlights shiny
					double wgt = (l - 0.88) / 0.12 * 0.5;
					r += (int) ((255 - r) * wgt); g += (int) ((255 - g) * wgt); b += (int) ((255 - b) * wgt);
				}
				img.setPixel(x, y, (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b));
			}
	}

	static int hsv(float h, float s, float v) {
		h = (h % 1f + 1f) % 1f;
		int i = (int) (h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	record Theme(String name, String description, Style style, int[] ramp, int[] handleRamp,
			int glint, String crosshair, int[] heartRamp) {
	}

	/** Every pack design (also older ones, so they can be cleaned up). */
	static final List<Theme> ALL_THEMES = List.of(
			new Theme("Testohd Neon Lila", "neon", Style.NORMAL,
					new int[]{0x1A0033, 0x4B0082, 0x8A2BE2, 0xC77DFF, 0xF3E1FF}, new int[]{0x111118, 0x2A2A35, 0x45455A},
					0xB04CFF, "ring_dot", new int[]{0x2A0050, 0x8A2BE2, 0xE0B0FF}),
			new Theme("Testohd Blutrot", "blood", Style.NORMAL,
					new int[]{0x120000, 0x4D0000, 0xA00000, 0xFF3030, 0xFFC0C0}, new int[]{0x0A0A0A, 0x1C1C1C, 0x333333},
					0xFF2020, "small_plus", new int[]{0x300000, 0xC00000, 0xFF6060}),
			new Theme("Testohd Ozean", "ocean", Style.NORMAL,
					new int[]{0x001A1F, 0x005F73, 0x0A9396, 0x94D2BD, 0xE9FFFB}, new int[]{0x1B263B, 0x2E3F5C, 0x415A77},
					0x30E0FF, "circle", new int[]{0x003040, 0x0A9396, 0x9FF5FF}),
			new Theme("Testohd Weihnachten Zuckerstange", "candy", Style.CANDY,
					new int[]{0x5C0000, 0xB00010, 0xE01020, 0xFF6070, 0xFFE0E0}, new int[]{0x0B3D0B, 0x146B14, 0x2E9E2E},
					0xFF3040, "star", new int[]{0x500000, 0xE01020, 0xFFFFFF}),
			new Theme("Testohd Weihnachten Frost", "frost", Style.FROST,
					new int[]{0x0B2545, 0x13315C, 0x5DA9E9, 0xB9E3FF, 0xFFFFFF}, new int[]{0x8D99AE, 0xB8C4D6, 0xEDF2F4},
					0x9AD8FF, "snowflake", new int[]{0x13315C, 0x5DA9E9, 0xE8F7FF}),
			new Theme("Testohd Weihnachten Tannenbaum", "tree", Style.TREE,
					new int[]{0x04250F, 0x0B4F1C, 0x1E7B34, 0x52B05F, 0xC9F2C7}, new int[]{0x5C0A0A, 0x9E1B1B, 0xD43A3A},
					0xFFD24A, "tree", new int[]{0x5C0A0A, 0xD43A3A, 0xFFD24A}),
			// ---- more colours
			hue("Testohd Sonnengold", "gold", 0.13f, 0.85f, "small_plus"),
			hue("Testohd Orange Glut", "orange", 0.07f, 0.9f, "circle"),
			hue("Testohd Limette", "lime", 0.24f, 0.85f, "ring_dot"),
			hue("Testohd Smaragd", "emerald", 0.38f, 0.8f, "small_plus"),
			hue("Testohd Mint", "mint", 0.45f, 0.5f, "circle"),
			hue("Testohd Himmelblau", "sky", 0.56f, 0.7f, "ring_dot"),
			hue("Testohd Saphir", "sapphire", 0.62f, 0.9f, "small_plus"),
			hue("Testohd Indigo", "indigo", 0.70f, 0.8f, "circle"),
			hue("Testohd Magenta", "magenta", 0.85f, 0.85f, "ring_dot"),
			hue("Testohd Pink", "pink", 0.92f, 0.65f, "star"),
			hue("Testohd Pastell", "pastel", 0.80f, 0.35f, "circle"),
			hue("Testohd Schneeweiss", "snow", 0.6f, 0.04f, "small_plus"),
			hue("Testohd Stahlgrau", "steel", 0.6f, 0.12f, "ring_dot"),
			custom("Testohd Void", "void", Style.NORMAL, new int[]{0x050308, 0x120A1C, 0x24123A, 0x3E2066, 0x7A4CC0},
					new int[]{0x000000, 0x0A0A0A, 0x1A1A1A}, "small_plus"),
			custom("Testohd Lava", "lava", Style.NORMAL, new int[]{0x1A0500, 0x6B0F00, 0xD43A00, 0xFF8A00, 0xFFE070},
					new int[]{0x111111, 0x262626, 0x3A3A3A}, "circle"),
			custom("Testohd Toxic", "toxic", Style.NORMAL, new int[]{0x031203, 0x0A3A0A, 0x1FA81F, 0x7CFF3A, 0xE8FFC0},
					new int[]{0x111118, 0x2A2A35, 0x45455A}, "ring_dot"),
			custom("Testohd Regenbogen", "rainbow", Style.RAINBOW, new int[]{0x202020, 0x505050, 0x909090, 0xC8C8C8, 0xFFFFFF},
					new int[]{0x111118, 0x2A2A35, 0x45455A}, "star"),
			custom("Testohd Galaxie", "galaxy", Style.GALAXY, new int[]{0x07051A, 0x1B1150, 0x3B2A9A, 0x8A6CFF, 0xE6DCFF},
					new int[]{0x0A0A14, 0x1A1A2A, 0x2E2E44}, "ring_dot"),
			custom("Testohd Weihnachten Lebkuchen", "gingerbread", Style.TREE, new int[]{0x2A1406, 0x5C3010, 0x9A5A22, 0xD08C48, 0xFFF4E6},
					new int[]{0xE8E8E8, 0xF6F6F6, 0xFFFFFF}, "star"),
			custom("Testohd Weihnachten Gold", "xmasgold", Style.TREE, new int[]{0x3A2400, 0x8A5A00, 0xD9A400, 0xFFD24A, 0xFFF6CC},
					new int[]{0x5C0A0A, 0x9E1B1B, 0xD43A3A}, "tree"),
			// ---- colour gradients (Farbverläufe)
			gradient("Testohd Verlauf Signature", "g_signature", "ring_dot", 0xFF4FD8, 0xB26BFF, 0x4FE3FF),
			gradient("Testohd Verlauf Sonnenuntergang", "g_sunset", "circle", 0x7B2FF7, 0xF107A3, 0xFF8C00),
			gradient("Testohd Verlauf Ozean", "g_ocean", "small_plus", 0x0A1F8F, 0x0072FF, 0x00E5FF),
			gradient("Testohd Verlauf Aurora", "g_aurora", "ring_dot", 0x7B5CFF, 0x00D9F5, 0x00F5A0),
			gradient("Testohd Verlauf Feuer", "g_fire", "circle", 0xB00000, 0xFF5A00, 0xFFE600),
			gradient("Testohd Verlauf Candy", "g_candy", "star", 0x6A82FB, 0xC77DFF, 0xFF6FD8),
			gradient("Testohd Verlauf Vaporwave", "g_vapor", "ring_dot", 0xFF71CE, 0x01CDFE, 0x05FFA1),
			gradient("Testohd Verlauf Eis", "g_ice", "small_plus", 0x3A47D5, 0x5DA9E9, 0xE0F7FF),
			gradient("Testohd Verlauf Gift", "g_poison", "circle", 0x006B3C, 0x2EE86B, 0xD4FF00),
			gradient("Testohd Verlauf Kirsche", "g_cherry", "star", 0x8A0030, 0xFF0844, 0xFFB199),
			gradient("Testohd Verlauf Mitternacht", "g_midnight", "small_plus", 0x0F0C29, 0x302B63, 0x8C7BFF),
			gradient("Testohd Verlauf Pfirsich", "g_peach", "circle", 0xFD6585, 0xFF9A8B, 0xFFD3A5),
			gradient("Testohd Verlauf Lila Gold", "g_purplegold", "ring_dot", 0x5B0FB5, 0xB34BE0, 0xFFD700),
			gradient("Testohd Verlauf Smaragd Gold", "g_emeraldgold", "small_plus", 0x0B6E4F, 0x3CCB7F, 0xF7D046),
			gradient("Testohd Verlauf Weihnachten", "g_xmas", "tree", 0xE0102A, 0xFFD24A, 0x1EB846),
			// ---- energy: dark / turquoise with a running light on mace and spears
			duo("Testohd Energie Schwarz", "e_black", "ring_dot", 0x1A1D22, 0x2FD9C6),
			duo("Testohd Energie Tuerkis", "e_teal", "circle", 0x0E9C92, 0xB8FFF6),
			// ---- Testohd23 V1: element animations + animated crystals
			v1("Testohd23 V1 Purple", "v1_purple", "ring_dot", Style.DUO, 0x6A22D0, 0xFF6FE0),
			v1("Testohd23 V1 Orange", "v1_orange", "circle", Style.DUO, 0xC2360C, 0xFFC23A),
			v1("Testohd23 V1 Black", "v1_black", "small_plus", Style.DUO, 0x22202A, 0x9B4DFF),
			v1("Testohd23 V1 Turquoise", "v1_turquoise", "circle", Style.DUO, 0x0E9C92, 0xB8FFF6),
			v1("Testohd Christmas V1 Turquoise", "v1_xmas", "snowflake", Style.V1XMAS, 0x18A8A4, 0xE6FFFC),
			v1("Testohd23 V1 White", "v1_white", "circle", Style.DUO, 0xE2DCCB, 0xFFE27A),
			v1("Testohd23 V2 Mono", "v2_mono", "circle", Style.DUO, 0x5A5A60, 0xF2F2F2),
			v1("Testohd23 V2 Graphite", "v2_graphite", "circle", Style.DUO, 0x4B5059, 0xC9CDD3),
			v1("Testohd23 V2 Navy", "v2_navy", "circle", Style.DUO, 0x2E4A7A, 0xB8C8E0),
			v1("Testohd23 V2 Sage", "v2_sage", "circle", Style.DUO, 0x6F8466, 0xDCE6D2),
			v1("Testohd23 V2 Sand", "v2_sand", "circle", Style.DUO, 0xB49A74, 0xF1E6D0),
			v1("Testohd23 V2 Bordeaux", "v2_bordeaux", "circle", Style.DUO, 0x6E1A28, 0xE6C3C8),
			v1("Testohd23 V2 Silver", "v2_silver", "circle", Style.DUO, 0xC8CCD2, 0xFFFFFF),
			v1("Testohd23 V3 Aurora", "v3_aurora", "circle", Style.DUO, 0x34D8B4, 0x8B7CF6),
			v1("Testohd23 V3 Vortex", "v3_vortex", "circle", Style.DUO, 0x7C6CF2, 0xE59CF5),
			v1("Testohd23 V3 Ember", "v3_ember", "circle", Style.DUO, 0xF2704F, 0xFFB35C),
			v1("Testohd23 V3 Tide", "v3_tide", "circle", Style.DUO, 0x1487A8, 0x6FE3F2),
			v1("Testohd23 V3 Prism", "v3_prism", "circle", Style.DUO, 0x3CCB9B, 0xB5F5DC),
			v1("Testohd23 V3 Rose", "v3_rose", "circle", Style.DUO, 0xEE7BAE, 0xFFC9DE),
			v1("Testohd23 V3 Warp", "v3_warp", "circle", Style.DUO, 0x5B9DF5, 0xB9D8FF),
			// ---- duo: clean colour + matching colour shift in the highlights
			duo("Testohd Duo Lila Pink", "d_purplepink", "ring_dot", 0x7B2FE0, 0xFF7AD9),
			duo("Testohd Duo Blau Cyan", "d_bluecyan", "small_plus", 0x1F4FE0, 0x5CF0FF),
			duo("Testohd Duo Rot Orange", "d_redorange", "circle", 0xC0141E, 0xFF9A3C),
			duo("Testohd Duo Gruen Gelb", "d_greenyellow", "small_plus", 0x1E9E3C, 0xE8F04A),
			duo("Testohd Duo Schwarz Gold", "d_blackgold", "ring_dot", 0x2A2A30, 0xFFCC33),
			duo("Testohd Duo Weiss Blau", "d_whiteblue", "circle", 0xB8C4D6, 0x8FD3FF),
			duo("Testohd Duo Tuerkis Mint", "d_tealmint", "ring_dot", 0x0E8A8A, 0x9CFFD6),
			duo("Testohd Duo Pink Pfirsich", "d_pinkpeach", "star", 0xE0457B, 0xFFC6A0),
			// ---- mixed styles: neon outlines, stripes, two-tone, camo, checker, glitter, chrome
			pattern("Testohd Neon Gruen", "p_neongreen", "ring_dot", "neon", 0x39FF14),
			pattern("Testohd Neon Pink", "p_neonpink", "circle", "neon", 0xFF2BD6),
			pattern("Testohd Neon Cyan", "p_neoncyan", "small_plus", "neon", 0x00F0FF),
			pattern("Testohd Streifen Biene", "p_bee", "small_plus", "stripes", 0xFFD000, 0x1A1A1A),
			pattern("Testohd Streifen Zebra", "p_zebra", "circle", "stripes", 0xF2F2F2, 0x151515),
			pattern("Testohd Streifen Blau Weiss", "p_bluewhite", "star", "stripes", 0x2E6BFF, 0xF4F8FF),
			pattern("Testohd Zweifarbig Rot Blau", "p_redblue", "ring_dot", "split", 0x2E5BFF, 0xFF2E3C),
			pattern("Testohd Zweifarbig Lila Gruen", "p_purplegreen", "circle", "split", 0x9B30FF, 0x3CFF6B),
			pattern("Testohd Camo Wald", "p_camoforest", "small_plus", "camo", 0x2F4F1F, 0x5B7B2A, 0x8A6B3D, 0x1F2A14),
			pattern("Testohd Camo Schnee", "p_camosnow", "circle", "camo", 0xF2F4F7, 0xB8C0CC, 0x7D8794),
			pattern("Testohd Camo Pink", "p_camopink", "star", "camo", 0xFF8AD8, 0xD94FB0, 0xFFC8EE, 0x8E2A74),
			pattern("Testohd Schachbrett", "p_checker", "small_plus", "checker", 0xF0F0F0, 0x202020),
			pattern("Testohd Glitzer Gold", "p_glittergold", "star", "glitter", 0x8A5A00, 0xFFC400, 0xFFF1A8),
			pattern("Testohd Glitzer Lila", "p_glitterpurple", "star", "glitter", 0x3A0CA3, 0xB517FF, 0xF2B5FF),
			pattern("Testohd Chrom", "p_chrome", "ring_dot", "chrome", 0x2A2E35, 0x8A93A3, 0xF4F7FB, 0x8A93A3)
	);

	/** Shipped packs: everything clean. The busy pattern packs (stripes, camo, ...) are only listed so old copies get removed. */
	static final List<Theme> THEMES = ALL_THEMES.stream().filter(t -> t.style() != Style.PATTERN && !t.description().equals("frost")).toList();

	private static final String[] MATERIALS = {"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"};
	private static final String[] TOOLS = {"sword", "axe", "pickaxe", "shovel"};

	/** True while the built-in packs are being (re)written - the menu waits and reloads them afterwards. */
	public static volatile boolean generating;

	/** What the generator is working on. One per thread, so several packs can be written at the same time. */
	static final class Ctx {
		Theme current;
		boolean frostAnim;
		V1Packs.Element element;
		V2Style.Palette palette;
	}

	private static final ThreadLocal<Ctx> CTX = ThreadLocal.withInitial(Ctx::new);

	static Ctx ctx() {
		return CTX.get();
	}

	/** Progress of the running generation (for the menu). */
	public static volatile int genDone, genTotal;

	/** Small file inside every generated pack: the VERSION it was made with. */
	private static final String MARKER = ".testohd_version";

	private static boolean hasMarker(Path dir) {
		try {
			Path m = dir.resolve(MARKER);
			return Files.isRegularFile(m) && Files.readString(m).trim().equals(String.valueOf(VERSION));
		} catch (Exception e) {
			return false;
		}
	}

	private static void writeMarker(Path dir) {
		try {
			Files.writeString(dir.resolve(MARKER), String.valueOf(VERSION));
		} catch (Exception e) {
			PackMixerClient.LOGGER.debug("Could not write pack marker in {}", dir, e);
		}
	}

	/**
	 * Generates (in a background thread) only the packs that are missing or were made by an older VERSION.
	 * Every finished pack is marked, so closing the game in between never starts everything again.
	 * {@code whenDone} gets the folder names of the packs that were written (only called if there were any).
	 */
	public static void generateIfNeeded(java.util.function.Consumer<java.util.Set<String>> whenDone) {
		generateIfNeeded(whenDone, false);
	}

	/** @param force write every pack again (settings button) */
	public static void generateIfNeeded(java.util.function.Consumer<java.util.Set<String>> whenDone, boolean force) {
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		if (generating) return;
		boolean current = !force && s.builtinPacksVersion >= VERSION; // packs written by this VERSION before the markers existed
		List<Theme> todo = new ArrayList<>();
		List<Path> unmarked = new ArrayList<>();
		for (Theme t : THEMES) {
			Path dir = PackMixerConfig.packsDir().resolve(folder(t));
			if (force || !Files.isDirectory(dir)) todo.add(t);
			else if (hasMarker(dir)) continue;
			else if (current) unmarked.add(dir);
			else todo.add(t);
		}
		if (todo.isEmpty() && unmarked.isEmpty() && current) return;
		// packs that don't exist yet first (the new ones of an update), then the outdated ones
		todo.sort(java.util.Comparator.comparing(th -> Files.isDirectory(PackMixerConfig.packsDir().resolve(folder(th)))));
		generating = !todo.isEmpty();
		genDone = 0;
		genTotal = todo.size();
		Thread t = new Thread(() -> {
			try {
				for (Path dir : unmarked) writeMarker(dir);
				if (!current) cleanupOldPacks();
				if (todo.isEmpty()) return;
				// several packs at once, but always leave cores for the game itself
				int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
				PackMixerClient.LOGGER.info("Generating {} of {} built-in packs ({} at a time)", todo.size(), THEMES.size(), workers);
				long start = System.currentTimeMillis();
				java.util.Set<String> done = java.util.concurrent.ConcurrentHashMap.newKeySet();
				java.util.concurrent.ConcurrentLinkedQueue<Theme> queue = new java.util.concurrent.ConcurrentLinkedQueue<>(todo);
				List<Thread> pool = new ArrayList<>();
				for (int i = 0; i < workers; i++) {
					Thread w = new Thread(() -> {
						Theme theme;
						while ((theme = queue.poll()) != null) {
							Path dir = PackMixerConfig.packsDir().resolve(folder(theme));
							Path tmp = PackMixerConfig.packsDir().resolve(folder(theme) + TMP);
							try {
								// written next to the real folder and swapped in when complete: the menu never sees half a pack
								deleteDir(tmp);
								generate(theme, tmp);
								writeMarker(tmp);
								deleteDir(dir);
								try {
									Files.move(tmp, dir);
								} catch (Exception e) { // old folder could not be removed completely (file in use): write over it
									copyTree(tmp, dir);
									deleteDir(tmp);
								}
								done.add(folder(theme));
							} catch (Throwable e) { // one broken texture / locked file must never stop the rest
								PackMixerClient.LOGGER.error("Built-in pack {} failed", theme.name, e);
							} finally {
								endGeneration();
								synchronized (BuiltinPacks.class) {
									genDone++;
								}
							}
						}
					}, "PackMixer pack generator " + (i + 1));
					w.setDaemon(true);
					w.setPriority(Thread.NORM_PRIORITY - 1);
					pool.add(w);
					w.start();
				}
				for (Thread w : pool) w.join();
				PackExtras.clearFields();
				PackMixerClient.LOGGER.info("Built-in packs: {} written in {} s", done.size(), (System.currentTimeMillis() - start) / 1000);
				if (done.size() == todo.size()) {
					s.builtinPacksVersion = VERSION;
					PackMixerConfig.saveSettings();
				}
				if (!done.isEmpty()) Minecraft.getInstance().execute(() -> whenDone.accept(new java.util.LinkedHashSet<>(done)));
			} catch (Throwable e) {
				PackMixerClient.LOGGER.error("Could not generate built-in packs", e);
			} finally {
				generating = false;
			}
		}, "PackMixer pack generator");
		t.setDaemon(true);
		t.start();
	}


	/** Folder / display name of a built-in pack: without the "Testohd" in front. */
	public static String folder(Theme th) {
		if (V1Packs.isV1(th.name)) return th.name; // the V1 packs keep their full name
		return th.name.startsWith("Testohd ") ? th.name.substring(8) : th.name;
	}

	private static java.util.Set<String> builtinNames;

	/** True for the packs this mod generates itself. */
	public static boolean isBuiltin(String packName) {
		if (builtinNames == null) {
			java.util.Set<String> n = new java.util.HashSet<>();
			for (Theme t : THEMES) n.add(folder(t));
			builtinNames = n;
		}
		return builtinNames.contains(packName);
	}

	/** Everything again (settings button / tests). */
	public static void generateAll() throws Exception {
		cleanupOldPacks();
		// (re)generate every pack; one broken texture must never stop the rest
		for (Theme theme : THEMES) {
			Path dir = PackMixerConfig.packsDir().resolve(folder(theme));
			try {
				generate(theme, dir);
				writeMarker(dir);
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("Built-in pack {} failed", theme.name, e);
			}
		}
		endGeneration();
		PackExtras.clearFields();
	}

	/** Forget what this thread was working on. */
	private static void endGeneration() {
		Ctx c = ctx();
		c.current = null;
		c.frostAnim = false;
		c.element = null;
		c.palette = null;
	}

	private static void cleanupOldPacks() {
		// old folders ("Testohd …" names, removed pattern packs) go away, your choices move to the new names
		java.util.Map<String, String> sel = PackMixerConfig.loadSelection();
		List<String> favs = PackMixerConfig.settings().favorites;
		boolean selChanged = false;
		for (Theme old : ALL_THEMES) {
			String newName = old.style() == Style.PATTERN ? null
					: old.description().equals("frost") ? "Testohd Christmas V1 Turquoise" // Frost became the V1 christmas pack
					: folder(old);
			if (!old.name().equals(newName)) deleteDir(PackMixerConfig.packsDir().resolve(old.name()));
			if (newName == null || !newName.equals(folder(old))) deleteDir(PackMixerConfig.packsDir().resolve(folder(old)));
			for (Map.Entry<String, String> e : sel.entrySet()) {
				if (e.getValue().equals(old.name()) || (!folder(old).equals(newName) && e.getValue().equals(folder(old)))) {
					e.setValue(newName);
					selChanged = true;
				}
			}
			for (int i = 0; i < favs.size(); i++)
				if (favs.get(i).equals(old.name()) || favs.get(i).equals(folder(old))) favs.set(i, newName);
		}
		sel.values().removeIf(java.util.Objects::isNull);
		favs.removeIf(java.util.Objects::isNull);
		if (selChanged) PackMixerConfig.saveSelection(sel);
	}

	/** Ending of the folder a pack is written to while it is not complete yet. */
	static final String TMP = ".tmp";

	private static void copyTree(Path from, Path to) throws java.io.IOException {
		try (var walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static void deleteDir(Path dir) {
		if (!Files.isDirectory(dir)) return;
		try (var walk = Files.walk(dir)) {
			for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		} catch (Exception e) { // e.g. a file that Windows still has open: leave it, never stop the generation
			PackMixerClient.LOGGER.warn("Could not remove old pack folder {}: {}", dir, e.toString());
		}
	}

	// ======================================================================= generation

	private static void generate(Theme th, Path root) throws Exception {
		ctx().current = th;
		ctx().frostAnim = th.style == Style.FROST;
		V1Packs.V1 v1 = V1Packs.of(th.name);
		ctx().element = v1 == null ? null : v1.element();
		ctx().palette = v1 == null ? null : v1.v2();
		Path a = root.resolve("assets/minecraft/textures");
		Files.createDirectories(a);

		// ---- tools
		for (String mat : MATERIALS) {
			for (String tool : TOOLS) {
				String path = "item/" + mat + "_" + tool + ".png";
				NativeImage img = vanilla(path);
				if (img == null) continue;
				boolean detectHandle = !mat.equals("wooden") && !mat.equals("golden");
				recolorTool(img, th, detectHandle, materialShift(mat));
				save(img, a.resolve(path));
			}
		}
		// ---- simple items
		for (String item : new String[]{"golden_apple", "totem_of_undying", "end_crystal", "experience_bottle",
				"snowball", "wind_charge", "mace", "bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2", "ender_pearl"}) {
			String path = "item/" + item + ".png";
			if (item.equals("ender_pearl") && th.style.christmas() && th.style != Style.V1XMAS) {
				save(ornament(th), a.resolve(path)); // christmas bauble
				continue;
			}
			NativeImage img = vanilla(path);
			if (img == null) continue;
			if (item.equals("totem_of_undying")) { // normal totem, only the eyes get the colour of the pack
				totemEyes(img, eyeColor(th));
				Theme keep = ctx().current;
				ctx().current = null;
				save(img, a.resolve(path));
				ctx().current = keep;
				continue;
			}
			if (item.startsWith("bow") || item.equals("mace")) recolorTool(img, th, true, 0);
			else recolor(img, th.ramp, 0);
			if (item.equals("totem_of_undying") && th.style.christmas() && th.style != Style.FROST) santaHat(img);
			save(img, a.resolve(path));
		}
		// ---- blocks
		for (String block : new String[]{"obsidian", "glowstone", "respawn_anchor_top", "respawn_anchor_bottom",
				"respawn_anchor_side0", "respawn_anchor_side1", "respawn_anchor_side2", "respawn_anchor_side3", "respawn_anchor_side4"}) {
			String path = "block/" + block + ".png";
			NativeImage img = vanilla(path);
			if (img == null) continue;
			recolor(img, block.equals("glowstone") ? brighter(th.ramp) : th.ramp, 0);
			if (th.style == Style.TREE && block.equals("obsidian")) lights(img);
			if ((th.style == Style.FROST || th.style == Style.V1XMAS) && block.equals("obsidian")) frost(img);
			save(img, a.resolve(path));
			copyMeta(path, a); // animated textures (respawn anchor top)
		}
		// ---- snowy grass for the christmas packs
		if (th.style.christmas()) {
			NativeImage side = vanilla("block/grass_block_side.png");
			if (side != null) {
				snowCap(side);
				save(side, a.resolve("block/grass_block_side.png"));
				String[] blank = new String[16];
				java.util.Arrays.fill(blank, "................");
				save(draw(blank, Map.of()), a.resolve("block/grass_block_side_overlay.png"));
			}
		}
		// ---- cobweb
		if (th.style == Style.FROST || th.style == Style.CANDY || th.style == Style.V1XMAS) {
			save(draw(SNOWFLAKE_16, Map.of('#', 0xFFFFFFFF, '+', 0xFFB9E3FF)), a.resolve("block/cobweb.png"));
		} else {
			NativeImage web = vanilla("block/cobweb.png");
			if (web != null) {
				recolor(web, th.ramp, 1);
				save(web, a.resolve("block/cobweb.png"));
			}
		}
		// ---- hearts
		for (String h : new String[]{"full", "half", "full_blinking", "half_blinking", "hardcore_full", "hardcore_half"}) {
			String path = "gui/sprites/hud/heart/" + h + ".png";
			NativeImage img = vanilla(path);
			if (img == null) continue;
			recolor(img, th.heartRamp, 0);
			save(img, a.resolve(path));
		}
		// ---- crosshair
		save(crosshair(th.crosshair), a.resolve("gui/sprites/hud/crosshair.png"));
		// ---- enchant glint
		for (String g : new String[]{"enchanted_glint_item", "enchanted_glint_armor"}) {
			String path = "misc/" + g + ".png";
			NativeImage img = vanilla(path);
			if (img == null) continue;
			tint(img, th.glint);
			save(img, a.resolve(path));
			copyMeta(path, a);
		}

		// ---- everything else: placed / worn / thrown things, GUI, sky
		safe(() -> PackExtras.generate(th, a, root));
		if (v1 != null) { // potions (bottle in the pack colour, animated) + animated shield
			for (String pot : new String[]{"potion", "splash_potion", "lingering_potion"}) {
				safe(() -> {
					NativeImage img = vanilla("item/" + pot + ".png");
					if (img == null) return;
					recolor(img, th.ramp, 0);
					save(img, a.resolve("item/" + pot + ".png"));
				});
			}
			V1Packs.Element el = v1.element();
			// shields live in a texture atlas, so a normal .mcmeta animation works there
			for (String[] sh : new String[][]{{"entity/shield/base.png", "entity/shield_base.png"},
					{"entity/shield/base_nopattern.png", "entity/shield_base_nopattern.png"}}) {
				Path file = a.resolve(sh[0]);
				Path src = Files.exists(file) ? file : a.resolve(sh[1]);
				if (!Files.exists(src)) continue;
				safe(() -> {
					NativeImage img;
					try (InputStream in = Files.newInputStream(src)) {
						img = NativeImage.read(in);
					}
					NativeImage strip = V1Packs.animateBlock(img, el, Math.floorMod(sh[0].hashCode(), 9973), 64);
					img.close();
					Files.createDirectories(file.getParent());
					strip.writeToFile(file);
					strip.close();
					Files.writeString(file.resolveSibling(file.getFileName() + ".mcmeta"), "{\"animation\": {\"frametime\": 2}}\n", StandardCharsets.UTF_8);
				});
			}
		}
		if (v1 != null) { // the animated end crystal of this pack
			CrystalPacks.Design d = null;
			for (CrystalPacks.Design c : CrystalPacks.DESIGNS) if (c.key().equals(v1.crystal())) d = c;
			CrystalPacks.Design design = d;
			if (design != null) safe(() -> CrystalPacks.writeInto(design, a));
		}
		ctx().element = null;
		ctx().palette = null;

		// ---- pack.mcmeta + icon
		Files.writeString(root.resolve("pack.mcmeta"), """
				{
				  "pack": {
				    "description": {"translate": "packmixer.pack.desc.%s", "fallback": "Made by Testohd23"},
				    "min_format": 75,
				    "max_format": 75
				  }
				}
				""".formatted(th.description), StandardCharsets.UTF_8);
		save(icon(th, a), root.resolve("pack.png"));
	}

	// ======================================================================= helpers

	interface Step {
		void run() throws Exception;
	}

	static void safe(Step step) {
		try {
			step.run();
		} catch (Exception e) {
			PackMixerClient.LOGGER.warn("Built-in pack part failed: {}", e.toString());
		}
	}

	static NativeImage vanilla(String texturePath) {
		try {
			var res = Minecraft.getInstance().getVanillaPackResources();
			IoSupplier<InputStream> s = res.getResource(PackType.CLIENT_RESOURCES,
					Identifier.fromNamespaceAndPath("minecraft", "textures/" + texturePath));
			if (s == null) return null;
			try (InputStream in = s.get()) {
				return NativeImage.read(in);
			}
		} catch (Exception e) {
			return null;
		}
	}

	static void copyMeta(String texturePath, Path texturesRoot) {
		try {
			var res = Minecraft.getInstance().getVanillaPackResources();
			IoSupplier<InputStream> s = res.getResource(PackType.CLIENT_RESOURCES,
					Identifier.fromNamespaceAndPath("minecraft", "textures/" + texturePath + ".mcmeta"));
			if (s == null) return;
			try (InputStream in = s.get()) {
				Path out = texturesRoot.resolve(texturePath + ".mcmeta");
				Files.createDirectories(out.getParent());
				Files.write(out, in.readAllBytes());
			}
		} catch (Exception ignored) {
		}
	}

	/** Theme being generated right now (for rainbow / galaxy post effects). */

	static void save(NativeImage img, Path out) throws Exception {
		String path = out.toString().replace('\\', '/');
		boolean styled = path.contains("/item/") || path.contains("/block/") || path.contains("/entity/")
				|| path.contains("/hud/food") || path.contains("/hud/hotbar");
		if (ctx().current != null && styled && !path.contains("/environment/")) {
			if (ctx().current.style == Style.RAINBOW) rainbow(img);
			else if (ctx().current.style == Style.GALAXY) galaxy(img);
		}
		if (ctx().current != null && path.contains("/heart/")) { // hearts follow rainbow / galaxy / gradient too
			if (ctx().current.style == Style.RAINBOW) rainbow(img);
			else if (ctx().current.style == Style.GALAXY) galaxy(img);
		}
		if (ctx().current != null && (styled || path.contains("/heart/"))) {
			if (ctx().current.style == Style.GRADIENT) gradient(img, GRADIENTS.get(ctx().current.name));
			else if (ctx().current.style == Style.PATTERN) pattern(img, PATTERNS.get(ctx().current.name));
			else if (ctx().current.style == Style.DUO || ctx().current.style == Style.V1XMAS) duo(img, GRADIENTS.get(ctx().current.name)[0], GRADIENTS.get(ctx().current.name)[1]);
		}
		Files.createDirectories(out.getParent());
		if (ctx().element != null && path.contains("/entity/equipment/humanoid")) { // worn armor stays normal in V1
			NativeImage orig = vanilla(path.substring(path.indexOf("/textures/") + 10));
			if (orig != null) {
				img.close();
				img = orig;
			}
		}
		if (ctx().element != null && img.getWidth() == img.getHeight() && (path.endsWith("/block/obsidian.png")
				|| path.endsWith("/block/glowstone.png") || path.endsWith("/block/cobweb.png")
				|| (path.contains("/block/respawn_anchor_") && !path.endsWith("_top.png")))) {
			NativeImage strip = V1Packs.animateBlock(img, ctx().element, Math.floorMod(path.hashCode(), 9973), 16);
			img.close();
			strip.writeToFile(out);
			strip.close();
			Files.writeString(out.resolveSibling(out.getFileName() + ".mcmeta"), "{\"animation\": {\"frametime\": 2}}\n", StandardCharsets.UTF_8);
			return;
		}
		if (ctx().element != null && img.getWidth() == img.getHeight() && path.contains("/textures/item/")) {
			V1Packs.Mode mode = V1Packs.mode(path);
			if (mode == V1Packs.Mode.TOOL) { // weapons / tools: original colours from vanilla
				NativeImage orig = vanilla(path.substring(path.indexOf("/textures/") + 10));
				if (orig != null && orig.getWidth() == img.getWidth() && orig.getHeight() == img.getHeight()) {
					img.close();
					img = orig;
				} else if (orig != null) orig.close();
			}
			NativeImage strip = V1Packs.animate(img, ctx().element, Math.floorMod(path.hashCode(), 9973), mode);
			img.close();
			strip.writeToFile(out);
			strip.close();
			Files.writeString(out.resolveSibling(out.getFileName() + ".mcmeta"), "{\"animation\": {\"frametime\": 2}}\n", StandardCharsets.UTF_8);
			return;
		}
		if (ctx().frostAnim && img.getWidth() == img.getHeight() && (path.contains("/textures/item/")
				|| path.endsWith("/block/obsidian.png") || path.endsWith("/block/glowstone.png"))) {
			NativeImage strip = frostStrip(img, path.hashCode());
			img.close();
			strip.writeToFile(out);
			strip.close();
			Files.writeString(out.resolveSibling(out.getFileName() + ".mcmeta"), "{\"animation\": {\"frametime\": 2}}\n", StandardCharsets.UTF_8);
			return;
		}
		img.writeToFile(out);
		img.close();
	}

	/** Frost pack is animated: an icy shine runs over every item, with small twinkles. */

	static NativeImage frostStrip(NativeImage img, int seed) {
		int w = img.getWidth(), h = img.getHeight(), n = 16;
		int u = Math.max(1, w / 16);
		NativeImage out = new NativeImage(w, h * n, true);
		for (int f = 0; f < n; f++) {
			double pos = f / (double) n * 2.2 - 0.6;
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int p = img.getPixel(x, y);
					int a = p >>> 24;
					if (a < 10) {
						out.setPixel(x, f * h + y, p);
						continue;
					}
					double d = (x + (h - 1 - y)) / (double) (w + h - 2);
					double band = Math.exp(-Math.pow((d - pos) * 8, 2)) * 0.7;
					int c = gradientAt(new int[]{p & 0xFFFFFF, 0xEBFAFF}, band);
					int tw = Math.floorMod(hash(x / u * 7 + seed, y / u * 13 + f * 101), 1000);
					if (tw < 25) c = 0xFFFFFF;
					out.setPixel(x, f * h + y, (a << 24) | c);
				}
		}
		return out;
	}

	static int darken(int rgb, double k) {
		return ((int) (((rgb >> 16) & 0xFF) * k) << 16) | ((int) (((rgb >> 8) & 0xFF) * k) << 8) | (int) ((rgb & 0xFF) * k);
	}

	static int lum(int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return (r * 299 + g * 587 + b * 114) / 1000;
	}

	/** Colour from a ramp (dark -> light), t in 0..1. */
	static int ramp(int[] ramp, double t) {
		t = Math.max(0, Math.min(1, t));
		double pos = t * (ramp.length - 1);
		int i = Math.min(ramp.length - 2, (int) pos);
		double f = pos - i;
		int c1 = ramp[i], c2 = ramp[i + 1];
		int r = (int) (((c1 >> 16) & 0xFF) * (1 - f) + ((c2 >> 16) & 0xFF) * f);
		int g = (int) (((c1 >> 8) & 0xFF) * (1 - f) + ((c2 >> 8) & 0xFF) * f);
		int b = (int) ((c1 & 0xFF) * (1 - f) + (c2 & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	/** Maps brightness onto the ramp, contrast-stretched. */
	static void recolor(NativeImage img, int[] ramp, double shift) {
		int min = 255, max = 0;
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getPixel(x, y);
				if ((p >>> 24) < 10) continue;
				int l = lum(p);
				min = Math.min(min, l);
				max = Math.max(max, l);
			}
		double range = Math.max(1, max - min);
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getPixel(x, y);
				int alpha = p >>> 24;
				if (alpha < 10) continue;
				double t = (lum(p) - min) / range * 0.9 + 0.08 + shift * 0.1;
				img.setPixel(x, y, (alpha << 24) | ramp(ramp, t));
			}
	}

	private static boolean isHandle(int p, int x, int y, int w) {
		int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
		boolean brown = r > g && g > b && r - b > 25 && r < 200;
		// handles are in the lower-left half of vanilla tool textures
		return brown && x + (w - 1 - y) < w;
	}

	static void recolorTool(NativeImage img, Theme th, boolean detectHandle, double shift) {
		int w = img.getWidth();
		boolean[][] handle = new boolean[w][img.getHeight()];
		if (detectHandle) {
			for (int y = 0; y < img.getHeight(); y++)
				for (int x = 0; x < w; x++) {
					int p = img.getPixel(x, y);
					if ((p >>> 24) >= 10 && isHandle(p, x, y, w)) handle[x][y] = true;
				}
		}
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int alpha = p >>> 24;
				if (alpha < 10) continue;
				double t = lum(p) / 255.0;
				int c;
				if (handle[x][y]) {
					c = ramp(th.handleRamp, t * 1.3);
				} else if (th.style == Style.CANDY) {
					// candy cane: diagonal red / white stripes across the blade
					boolean red = Math.floorMod((x - y) / 2, 2) == 0;
					c = red ? ramp(th.ramp, 0.25 + t * 0.5) : ramp(new int[]{0xD8D8D8, 0xFFFFFF}, t + 0.3);
				} else {
					c = ramp(th.ramp, t * 0.95 + 0.1 + shift * 0.1);
				}
				img.setPixel(x, y, (alpha << 24) | c);
			}
	}

	private static double materialShift(String mat) {
		return switch (mat) {
			case "wooden", "stone" -> -1.0;
			case "netherite" -> -0.5;
			case "golden", "diamond" -> 1.0;
			default -> 0;
		};
	}

	static int[] brighter(int[] ramp) {
		int[] r = new int[ramp.length];
		for (int i = 0; i < ramp.length; i++) r[i] = ramp(ramp, 0.35 + 0.65 * i / (ramp.length - 1.0));
		return r;
	}

	static void tint(NativeImage img, int rgb) {
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getPixel(x, y);
				int l = Math.min(255, lum(p) * 5 / 2); // the glint is drawn additively: it has to be bright to be visible
				int r = ((rgb >> 16) & 0xFF) * l / 255, g = ((rgb >> 8) & 0xFF) * l / 255, b = (rgb & 0xFF) * l / 255;
				img.setPixel(x, y, (p & 0xFF000000) | (r << 16) | (g << 8) | b);
			}
	}

	static int hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return h ^ (h >>> 16);
	}

	/** Snow on the top rows of a block side texture. */
	static void snowCap(NativeImage img) {
		int w = img.getWidth();
		int unit = Math.max(1, w / 16);
		for (int x = 0; x < w; x++) {
			int depth = (3 + Math.floorMod(hash(x / unit, 7), 3)) * unit;
			for (int y = 0; y < depth && y < img.getHeight(); y++) {
				int shade = Math.floorMod(hash(x / unit, y / unit), 5) == 0 ? 0xDDEBFF : 0xFFFFFF;
				img.setPixel(x, y, 0xFF000000 | shade);
			}
		}
	}

	/** Coloured christmas lights sprinkled over a block. */
	static void lights(NativeImage img) {
		int[] colors = {0xFF3B3B, 0xFFD24A, 0x4AA3FF, 0xFFFFFF};
		int unit = Math.max(1, img.getWidth() / 16);
		for (int i = 0; i < 9; i++) {
			int x = Math.floorMod(hash(i, 3), 16) * unit, y = Math.floorMod(hash(i, 11), 16) * unit;
			int c = colors[i % colors.length];
			for (int dx = 0; dx < unit; dx++)
				for (int dy = 0; dy < unit; dy++) img.setPixel(x + dx, y + dy, 0xFF000000 | c);
		}
	}

	/** Frosty ice crystals on a block. */
	static void frost(NativeImage img) {
		int unit = Math.max(1, img.getWidth() / 16);
		for (int i = 0; i < 14; i++) {
			int x = Math.floorMod(hash(i, 5), 16) * unit, y = Math.floorMod(hash(i, 9), 16) * unit;
			for (int dx = 0; dx < unit; dx++)
				for (int dy = 0; dy < unit; dy++) img.setPixel(x + dx, y + dy, 0xFFEAF6FF);
		}
	}

	/** Rainbow: hue runs diagonally over the texture, brightness from the original. */
	static void rainbow(NativeImage img) {
		int w = img.getWidth(), h = img.getHeight();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int a = p >>> 24;
				if (a < 10) continue;
				float l = lum(p) / 255f;
				img.setPixel(x, y, (a << 24) | hsv((x + y) / (float) (w + h) * 1.2f, 0.75f, 0.3f + 0.7f * l));
			}
	}

	/** Energy packs: theme name -> colour of the running light on mace and spears. */
	static final java.util.Map<String, Integer> ENERGY = java.util.Map.of(
			"Testohd Energie Schwarz", 0x35F2DD, "Testohd Energie Tuerkis", 0xE6FFFC);

	/** Galaxy colours: deep space -> indigo -> purple -> pink nebula -> cyan glow. */
	static final int[] GALAXY = {0x070720, 0x1A1260, 0x4B1FA8, 0x9B3BD6, 0xF062C8, 0x7FE8FF};

	/**
	 * Galaxy: a smooth nebula (colour clouds) that follows the shape of the texture, a dark outline for clean
	 * edges and only a few bright stars – instead of random noise.
	 */
	static void galaxy(NativeImage img) {
		int w = img.getWidth(), h = img.getHeight();
		int fh = Math.min(w, h), u = Math.max(1, w / 16);
		boolean[] solid = new boolean[w * h];
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) solid[y * w + x] = (img.getPixel(x, y) >>> 24) >= 10;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = img.getPixel(x, y);
				int a = p >>> 24;
				if (a < 10) continue;
				int px = x / u, py = (y % fh) / u;
				double l = lum(p) / 255.0;
				double n = PackExtras.fbm(px * 0.22, py * 0.22, 0.5, 7);       // nebula clouds
				double diag = (px + (fh / u - 1 - py)) / (2.0 * fh / u);       // dark handle -> bright tip
				double t = Math.max(0, Math.min(1, 0.08 + diag * 0.62 + (n - 0.5) * 0.7 + l * 0.3));
				int c = gradientAt(GALAXY, t);
				double k = 0.5 + 0.85 * l;
				int col = PackExtras.mix(0, c, Math.min(1.2, k));
				boolean edge = false;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0] * u, ny = y % fh + d[1] * u;
					if (nx < 0 || ny < 0 || nx >= w || ny >= fh || !solid[(y - y % fh + ny) * w + nx]) { edge = true; break; }
				}
				if (edge) col = PackExtras.mix(0x05030F, col, 0.35);
				img.setPixel(x, y, (a << 24) | col);
			}
	}

	/** Eye colour of a pack's totem: a bright version of its main colour (frost = light blue). */
	static int eyeColor(Theme th) {
		V1Packs.V1 v = V1Packs.of(th.name);
		if (v != null) return v.eye();
		if (th.style == Style.FROST) return 0x8FE3FF;
		if (th.style == Style.CANDY) return 0xFF3B4E;
		if (th.style == Style.TREE) return th.name.contains("Lebkuchen") ? 0xFFB347 : th.name.contains("Gold") ? 0xFFD24A : 0x3DFF6E;
		if (th.style == Style.RAINBOW) return 0xFF4FD8;
		if (th.style == Style.GALAXY) return 0xB46CFF;
		int[] c = PackExtras.skyColors(th);
		int col = c[1];
		float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
		if (hsb[1] < 0.15f) return 0xF2F6FF;                 // grey / white packs: white eyes
		return java.awt.Color.HSBtoRGB(hsb[0], Math.min(1f, hsb[1] * 1.1f + 0.1f), 1f) & 0xFFFFFF;
	}

	/** Recolours only the (green) eyes of the vanilla totem. */
	static void totemEyes(NativeImage img, int eye) {
		int dark = darker(eye, 0.62);
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getPixel(x, y);
				if ((p >>> 24) < 10) continue;
				int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
				if (g > r + 60 && g > b + 20) { // green eye pixels
					int c = g > 200 ? eye : dark;
					img.setPixel(x, y, (p & 0xFF000000) | c);
				}
			}
	}

	/** Little Santa hat on top of the totem. */
	static void santaHat(NativeImage img) {
		if (img.getWidth() != 16) return;
		String[] hat = {
				"..........W.....",
				"........RRR.....",
				".......RRRR.....",
				"......RRRRR.....",
				".....WWWWWWW....",
		};
		paint(img, hat, Map.of('R', 0xFFD01020, 'W', 0xFFFFFFFF), 0, -1);
	}

	static void paint(NativeImage img, String[] art, Map<Character, Integer> colors, int ox, int oy) {
		for (int y = 0; y < art.length; y++)
			for (int x = 0; x < art[y].length(); x++) {
				Integer c = colors.get(art[y].charAt(x));
				int px = x + ox, py = y + oy;
				if (c != null && px >= 0 && py >= 0 && px < img.getWidth() && py < img.getHeight()) img.setPixel(px, py, c);
			}
	}

	static NativeImage draw(String[] art, Map<Character, Integer> colors) {
		NativeImage img = new NativeImage(art[0].length(), art.length, true);
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) img.setPixel(x, y, 0);
		paint(img, art, colors, 0, 0);
		return img;
	}

	/** Christmas bauble (replaces the ender pearl). */
	static NativeImage ornament(Theme th) {
		String[] art = {
				"................",
				"......GGGG......",
				".......GG.......",
				"......D33D......",
				"....D333333D....",
				"...D33344333D...",
				"..D333444333D...",
				"..D33333333332..",
				"..D3SSSSSSSS2...",
				"..D33333333332..",
				"..D22333333222..",
				"...D222332222...",
				"....D2222222....",
				"......D222......",
				"................",
				"................",
		};
		int base = th.style == Style.FROST ? 0x5DA9E9 : th.style == Style.TREE ? 0xFFC020 : 0xE01020;
		return draw(art, Map.of(
				'G', 0xFFC9A227, 'D', 0xFF000000 | darker(base, 0.45), '2', 0xFF000000 | darker(base, 0.7),
				'3', 0xFF000000 | base, '4', 0xFFFFFFFF, 'S', 0xFF000000 | (th.style == Style.TREE ? 0xD43A3A : 0xFFE680)));
	}

	static int darker(int rgb, double f) {
		int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	static final String[] SNOWFLAKE_16 = {
			"................",
			".......#........",
			"....#..#..#.....",
			".....#.#.#......",
			"..#...###...#...",
			"...#...#...#....",
			"....#..#..#.....",
			".#######+#######",
			"....#..#..#.....",
			"...#...#...#....",
			"..#...###...#...",
			".....#.#.#......",
			"....#..#..#.....",
			".......#........",
			"................",
			"................",
	};

	static NativeImage crosshair(String style) {
		String[] art = switch (style) {
			case "ring_dot" -> new String[]{
					"...............", "...............", "...............", "......###......", ".....#...#.....",
					"....#.....#....", "...#.......#...", "...#...#...#...", "...#.......#...", "....#.....#....",
					".....#...#.....", "......###......", "...............", "...............", "..............."};
			case "small_plus" -> new String[]{
					"...............", "...............", "...............", "...............", "...............",
					".......#.......", ".......#.......", ".....##.##.....", ".......#.......", ".......#.......",
					"...............", "...............", "...............", "...............", "..............."};
			case "circle" -> new String[]{
					"...............", "...............", "...............", "...............", "......###......",
					".....#...#.....", "....#.....#....", "....#.....#....", "....#.....#....", ".....#...#.....",
					"......###......", "...............", "...............", "...............", "..............."};
			case "star" -> new String[]{
					"...............", "...............", ".......#.......", ".......#.......", "......###......",
					"..###########..", "....#######....", ".....#####.....", "....###.###....", "...##.....##...",
					"..#.........#..", "...............", "...............", "...............", "..............."};
			case "snowflake" -> new String[]{
					"...............", "...............", ".......#.......", ".....#.#.#.....", "......###......",
					"...#...#...#...", "....#..#..#....", "..#####.#####..", "....#..#..#....", "...#...#...#...",
					"......###......", ".....#.#.#.....", ".......#.......", "...............", "..............."};
			default -> new String[]{ // tree
					"...............", "...............", ".......#.......", "......###......", ".......#.......",
					"......###......", ".....#####.....", "......###......", ".....#####.....", "....#######....",
					"...#########...", ".......#.......", ".......#.......", "...............", "..............."};
		};
		return draw(art, Map.of('#', 0xFFFFFFFF));
	}

	/** 64x64 pack icon: theme gradient + the recoloured diamond sword. */
	private static NativeImage icon(Theme th, Path texturesRoot) throws Exception {
		NativeImage icon = new NativeImage(64, 64, true);
		for (int y = 0; y < 64; y++)
			for (int x = 0; x < 64; x++) icon.setPixel(x, y, 0xFF000000 | (th.style == Style.GRADIENT || th.style == Style.DUO || th.style == Style.V1XMAS
					? darken(gradientAt(GRADIENTS.get(th.name), (x + 63 - y) / 126.0), 0.45)
					: th.style == Style.PATTERN
					? darken(gradientAt(PATTERNS.get(th.name).colors().length > 1 ? PATTERNS.get(th.name).colors()
							: new int[]{0x000000, PATTERNS.get(th.name).colors()[0]}, (x + 63 - y) / 126.0), 0.4)
					: ramp(th.ramp, 0.15 + (x + y) / 128.0 * 0.5)));
		Path sword = texturesRoot.resolve("item/diamond_sword.png");
		if (Files.exists(sword)) {
			try (InputStream in = Files.newInputStream(sword); NativeImage s = NativeImage.read(in)) {
				int f = 48 / s.getWidth();
				for (int y = 0; y < Math.min(s.getHeight(), s.getWidth()); y++)
					for (int x = 0; x < s.getWidth(); x++) {
						int p = s.getPixel(x, y);
						if ((p >>> 24) < 10) continue;
						for (int dx = 0; dx < f; dx++)
							for (int dy = 0; dy < f; dy++) icon.setPixel(8 + x * f + dx, 8 + y * f + dy, p | 0xFF000000);
					}
			}
		}
		return icon;
	}

	/** Pack names, for the README / UI. */
	public static List<String> names() {
		List<String> l = new ArrayList<>();
		for (Theme t : THEMES) l.add(t.name);
		return l;
	}

	private BuiltinPacks() {
	}
}
