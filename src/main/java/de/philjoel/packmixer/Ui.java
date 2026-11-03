package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/**
 * Look of the menus: window with cut corners over a purple night/mountain background,
 * dark header bar, translucent cards, light "Done"/"Settings" buttons, white pill switches.
 */
public final class Ui {
	// ---- palette
	public static final int TEXT = 0xFFF2F0F7;
	public static final int DIM = 0xFFA9A3BC;
	public static final int FAINT = 0x99A9A3BC;
	public static final int ACCENT = 0xFFFFFFFF;
	public static final int GOLD = 0xFFFFD25A;
	public static final int GOOD = 0xFF8FF0B0;
	public static final int CARD = 0x48000000;
	public static final int CARD_HOVER = 0x30FFFFFF;
	public static final int SEPARATOR = 0x33FFFFFF;
	public static final int LINE = 0x33FFFFFF;
	public static final int CHAMFER = 8;

	private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "textures/gui/background.png");
	private static final int BG_W = 512, BG_H = 288;

	public static float time() {
		return (System.currentTimeMillis() % 3_600_000L) / 1000f;
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	// ======================================================================= window

	/** Left/right inset of a row inside the cut-corner window (top-left and bottom-right corners are cut). */
	private static int insetLeft(int row, int h) {
		return row < CHAMFER ? CHAMFER - row : 0;
	}

	private static int insetRight(int row, int h) {
		return row >= h - CHAMFER ? CHAMFER - (h - 1 - row) : 0;
	}

	/** Window with background picture, dark tint, cut corners and corner brackets. */
	public static void window(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int w = x1 - x0, h = y1 - y0;
		float sx = BG_W / (float) w, sy = BG_H / (float) h;
		// the rows inside the cut corners are drawn one by one, the middle in one go
		for (int r = 0; r < h; r++) {
			int l = insetLeft(r, h), rr = insetRight(r, h);
			boolean special = l > 0 || rr > 0;
			int rows = 1;
			if (!special) {
				int end = h - CHAMFER;
				rows = end - r;
			}
			int rw = w - l - rr;
			g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x0 + l, y0 + r, l * sx, r * sy, rw, rows,
					Math.max(1, (int) (rw * sx)), Math.max(1, (int) (rows * sy)), BG_W, BG_H);
			g.fill(x0 + l, y0 + r, x0 + l + rw, y0 + r + rows, 0x70080618);
			r += rows - 1;
		}
		// thin border
		g.fill(x0 + CHAMFER, y0, x1, y0 + 1, LINE);
		g.fill(x0, y0 + CHAMFER, x0 + 1, y1, LINE);
		g.fill(x1 - 1, y0, x1, y1 - CHAMFER, LINE);
		g.fill(x0, y1 - 1, x1 - CHAMFER, y1, LINE);
		// bright corner brackets on the cut corners (as in the reference)
		int bright = 0xE6FFFFFF;
		for (int i = 0; i < CHAMFER; i++) {
			g.fill(x0 + CHAMFER - i - 1, y0 + i, x0 + CHAMFER - i, y0 + i + 1, bright);
			g.fill(x1 - CHAMFER + i, y1 - 1 - i, x1 - CHAMFER + i + 1, y1 - i, bright);
		}
		g.fill(x0 + CHAMFER, y0, x0 + CHAMFER + 14, y0 + 1, bright);
		g.fill(x0, y0 + CHAMFER, x0 + 1, y0 + CHAMFER + 14, bright);
		g.fill(x1 - CHAMFER - 14, y1 - 1, x1 - CHAMFER, y1, bright);
		g.fill(x1 - 1, y1 - CHAMFER - 14, x1, y1 - CHAMFER, bright);
		if (PackMixerConfig.settings().menuSnow) snow(g, x0, y0, x1, y1);
	}

	/** Kept for older call sites. */
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		window(g, x0, y0, x1, y1);
	}

	/** Dark header band at the top of a window (height 18) + separator. */
	public static void headerBar(GuiGraphicsExtractor g, int x0, int y0, int x1) {
		g.fill(x0 + CHAMFER, y0 + 1, x1 - 1, y0 + 2, 0x30000000);
		for (int r = 1; r < 19; r++) {
			int l = r < CHAMFER ? CHAMFER - r : 1;
			g.fill(x0 + l, y0 + r, x1 - 1, y0 + r + 1, 0x40000000);
		}
		g.fill(x0 + 1, y0 + 19, x1 - 1, y0 + 20, LINE);
	}

	/** "TESTOHD | SUBTITLE" brand text in the header. */
	public static void brand(GuiGraphicsExtractor g, int x, int y, String subtitle) {
		Component b = Component.literal("TESTOHD").withStyle(net.minecraft.ChatFormatting.BOLD);
		g.text(font(), b, x, y, TEXT, false);
		int bx = x + font().width(b) + 5;
		g.fill(bx, y - 1, bx + 1, y + 8, LINE);
		small(g, subtitle.toUpperCase(), bx + 5, y + 1, DIM);
	}

	/** Credit top-left of the screen. */
	public static void header(GuiGraphicsExtractor g, int screenWidth, String subtitle) {
		g.text(font(), PackMixerClient.CREDIT, 6, 6, 0xFFD9C8FF, true);
	}

	public static void hLine(GuiGraphicsExtractor g, int x0, int x1, int y) {
		g.fill(x0, y, x1, y + 1, LINE);
	}

	/** Small text (75 %). */
	public static void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font(), text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static int smallWidth(String text) {
		return (int) Math.ceil(font().width(text) * 0.75f);
	}

	/** "| TITLE  12 Styles" section label. */
	public static void section(GuiGraphicsExtractor g, String title, String count, int x, int y) {
		g.fill(x, y, x + 1, y + 8, TEXT);
		g.text(font(), title.toUpperCase(), x + 4, y, TEXT, false);
		if (count != null && !count.isEmpty()) small(g, count, x + 8 + font().width(title.toUpperCase()), y + 2, FAINT);
	}

	/** Gradient text, used for the "Testohd" brand inside pack names. */
	public static int gradientText(GuiGraphicsExtractor g, String text, int x, int y) {
		int cx = x;
		for (int i = 0; i < text.length(); i++) {
			float t = text.length() <= 1 ? 0 : i / (float) (text.length() - 1);
			int c = lerp(0xFFFF7AC6, 0xFF7AD9FF, t);
			String ch = String.valueOf(text.charAt(i));
			g.text(font(), ch, cx, y, c, false);
			cx += font().width(ch);
		}
		return cx - x;
	}

	/**
	 * Item for an icon. Newer Minecraft versions can't create items before a world was loaded
	 * (main menu) - then the icon is simply left empty instead of crashing.
	 */
	public static net.minecraft.world.item.ItemStack stack(net.minecraft.world.level.ItemLike item) {
		try {
			return new net.minecraft.world.item.ItemStack(item);
		} catch (Throwable e) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
	}

	/** Item icon that also works in the main menu of newer versions: then the item texture is drawn directly. */
	public static final class Icon {
		private final net.minecraft.world.item.Item item;
		private net.minecraft.world.item.ItemStack stack;

		public Icon(net.minecraft.world.level.ItemLike item) {
			this.item = item.asItem();
		}

		void draw(GuiGraphicsExtractor g, int x, int y, int size) {
			if (stack == null || stack.isEmpty()) stack = stack(item);
			if (!stack.isEmpty()) {
				item(g, stack, x, y, size);
				return;
			}
			String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
			for (String p : new String[]{"textures/item/" + name + ".png", "textures/block/" + name + ".png",
					"textures/block/" + name + "_side.png", "textures/block/" + name + "_front.png"}) {
				PreviewCache.Preview pv = ICONS.texture(null, p);
				if (pv != null) {
					drawIcon(g, pv, x, y, size, 0xFFFFFFFF);
					return;
				}
			}
		}
	}

	private static final PreviewCache ICONS = new PreviewCache();

	public static Icon icon(net.minecraft.world.level.ItemLike item) {
		return new Icon(item);
	}

	/** A cosmetic's item; in the main menu of newer versions its texture instead (wings / V1 hats). */
	public static void cosmetic(GuiGraphicsExtractor g, net.minecraft.world.item.ItemStack stack, String id, int x, int y, int size) {
		if (stack != null && !stack.isEmpty()) {
			item(g, stack, x, y, size);
			return;
		}
		String tex = Cosmetics.WINGS.contains(id) ? "textures/item/wing_" + id.replace("_wings", "") + ".png"
				: Cosmetics.TEXTURED.contains(id) ? "textures/item/hat_" + id + ".png" : null;
		PreviewCache.Preview pv = tex == null ? null : ICONS.modTexture(tex);
		if (pv == null) return;
		if (Cosmetics.WINGS.contains(id)) { // both wings side by side
			int fw = pv.width(), fh = fw * 4 / 3, frames = Math.max(1, pv.height() / fh);
			int f = (int) (time() * 10) % frames;
			int w = size / 2, h = w * 4 / 3, yy = y + (size - h) / 2;
			g.blit(RenderPipelines.GUI_TEXTURED, pv.id(), x, yy, 0f, (float) (f * fh), w, h, fw, fh, fw, pv.height(), 0xFFFFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, pv.id(), x + w, yy, (float) fw, (float) (f * fh), w, h, -fw, fh, fw, pv.height(), 0xFFFFFFFF);
			return;
		}
		int fw = pv.width(), fh = id.endsWith("_crown") ? fw / 2 : fw, frames = Math.max(1, pv.height() / fh);
		int f = (int) (time() * 10) % frames;
		int h = size * fh / fw;
		g.blit(RenderPipelines.GUI_TEXTURED, pv.id(), x, y + (size - h) / 2, 0f, (float) (f * fh), size, h, fw, fh, fw, pv.height(), 0xFFFFFFFF);
	}

	public static void item(GuiGraphicsExtractor g, Icon icon, int x, int y, int size) {
		if (icon != null) icon.draw(g, x, y, size);
	}

	/** Display name of a pack: built-in packs are translated ("packmixer.pack.<folder name>"). */
	public static String packDisplay(String name) {
		if (name == null) return "";
		String key = "packmixer.pack." + name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
		return net.minecraft.locale.Language.getInstance().has(key) ? net.minecraft.client.resources.language.I18n.get(key) : name;
	}

	/** Pack name: built-in packs are drawn in the brand gradient. */
	public static void packName(GuiGraphicsExtractor g, String name, int centerX, int y, int maxW, boolean bright) {
		String shown = packDisplay(name);
		if (font().width(shown) > maxW && V1Packs.isV1(name)) // small cards: "V1 Purple", "Christmas V1 Turquoise"
			shown = shown.replace("Testohd23 ", "").replace("Testohd ", "");
		if (font().width(shown) > maxW) {
			while (shown.length() > 1 && font().width(shown + "...") > maxW) shown = shown.substring(0, shown.length() - 1);
			shown = shown + "...";
		}
		int x = centerX - font().width(shown) / 2;
		if (BuiltinPacks.isBuiltin(name)) { // built-in packs: name in the brand gradient
			gradientText(g, shown, x, y);
		} else {
			g.text(font(), shown, x, y, bright ? TEXT : DIM, false);
		}
	}

	/** Tiny tag like "TS" in the bottom-left of a card. */
	public static void badge(GuiGraphicsExtractor g, String text, int x, int y) {
		int w = smallWidth(text) + 4;
		g.fill(x, y, x + w, y + 7, 0xC0303040);
		g.fill(x, y, x + w, y + 1, 0x40FFFFFF);
		small(g, text, x + 2, y + 1, 0xFFCFCFE0);
	}

	public static void selectedFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.outline(x, y, w, h, 0xFFFFFFFF);
		for (int i = 0; i < 7; i++) g.fill(x + 1, y + 1 + i, x + 8 - i, y + 2 + i, 0xFFFFFFFF);
	}

	/** Intro: 0 -> 1 in 150 ms. */
	public static float intro(long openedAt) {
		float p = Math.min(1f, (System.currentTimeMillis() - openedAt) / 150f);
		return 1f - (1f - p) * (1f - p);
	}

	public static int lerp(int c1, int c2, float t) {
		t = Math.max(0, Math.min(1, t));
		int a = (int) (((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
		int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
		int gg = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
		int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}

	private static int hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return h ^ (h >>> 16);
	}

	private static void snow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		float t = time();
		int w = x1 - x0, h = y1 - y0;
		for (int i = 0; i < 30; i++) {
			float speed = 6 + Math.floorMod(hash(i, 3), 8);
			float fy = (t * speed + Math.floorMod(hash(i, 4), h)) % h;
			float fx = Math.floorMod(hash(i, 5), w) + (float) Math.sin(t * 0.6 + i) * 3;
			int px = x0 + (int) Math.max(2, Math.min(w - 3, fx));
			g.fill(px, y0 + (int) fy, px + 1, y0 + (int) fy + 1, 0x90FFFFFF);
		}
	}

	/** Item icon at any size. */
	public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
		if (stack == null || stack.isEmpty()) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 16f, size / 16f);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	/** White pill switch (as in the reference). */
	public static void drawSwitch(GuiGraphicsExtractor g, int x, int y, boolean on) {
		int w = 16, h = 8;
		g.fill(x + 1, y, x + w - 1, y + h, on ? 0xFFC9C6D6 : 0xFF14121C);
		g.fill(x, y + 1, x + w, y + h - 1, on ? 0xFFC9C6D6 : 0xFF14121C);
		if (!on) g.outline(x, y, w, h, 0x50FFFFFF);
		int kx = on ? x + w - 7 : x + 1;
		g.fill(kx + 1, y + 1, kx + 5, y + h - 1, 0xFFFFFFFF);
		g.fill(kx, y + 2, kx + 6, y + h - 2, 0xFFFFFFFF);
	}

	/** Slider icon (three lines with knobs) used on the Settings button. */
	public static void slidersIcon(GuiGraphicsExtractor g, int x, int y, int color) {
		for (int i = 0; i < 3; i++) {
			g.fill(x, y + i * 3, x + 9, y + i * 3 + 1, color);
			int k = x + (i == 1 ? 6 : i == 0 ? 2 : 4);
			g.fill(k, y + i * 3 - 1, k + 2, y + i * 3 + 2, color);
		}
	}

	// ======================================================================= previews

	public static void drawPreview(GuiGraphicsExtractor g, PreviewCache cache, Category cat, SourcePack pack, int x, int y, int size) {
		drawPreview(g, cache, cat, pack, x, y, size, 0f, false, false);
	}

	/**
	 * Animated preview: blocks turn slowly as real 3D cubes (fast spin on hover),
	 * items float and sway a little – like the reference video.
	 * @param phase per-card offset so the cards don't move in sync
	 */
	public static void drawPreview(GuiGraphicsExtractor g, PreviewCache cache, Category cat, SourcePack pack, int x, int y, int size,
			float phase, boolean hover, boolean animate) {
		float t = time();
		float bob = animate ? (float) Math.sin(t * 2.0 + phase) * size * 0.04f : 0f;
		if (cat.isCube()) {
			PreviewCache.Preview top = cache.texture(pack, cat.cube.get(0));
			PreviewCache.Preview side = cache.texture(pack, cat.cube.get(1));
			if (top != null && side != null) {
				float yaw;
				if (!animate) yaw = 45f;
				else if (hover) yaw = 45f + (t * 90f + phase * 30f) % 360f;     // spin while hovered
				else yaw = 45f + (float) Math.sin(t * 0.9 + phase) * 18f;        // gentle sway
				drawCube(g, top, side, cat.topTintArgb(), x, y + bob, size, yaw);
				return;
			}
		}
		if (cat.id.equals("end_crystal") || cat.id.equals(DefaultCategories.FULL_PACK)) {
			PreviewCache.Preview c = cache.crystal3d(pack);
			if (c != null) { // placed crystal in 3D: floats and turns like in the world (faster on hover)
				int n = PreviewCache.CRYSTAL_FRAMES, fw = c.width();
				int f = animate ? Math.floorMod((int) (t * (hover ? 24 : 14) + phase * 9), n) : 6;
				g.blit(RenderPipelines.GUI_TEXTURED, c.id(), x, y, 0f, (float) (f * fw), size, size, fw, fw, fw, c.height(), 0xFFFFFFFF);
				return;
			}
		}
		PreviewCache.Preview p = cache.icon(cat, pack);
		if (p == null) return; // nothing to show -> leave the card empty instead of a "?"
		if (!animate) {
			drawIcon(g, p, x, y, size, 0xFFFFFFFF);
			return;
		}
		boolean flat = p.path() != null && (p.path().contains("/gui/") || p.path().contains("/sky/")
				|| p.path().contains("/environment/") || p.path().contains("/misc/"));
		if (flat) { // GUI parts and skies: no wobbling, just a soft pulse
			float pulse = 1f + (float) Math.sin(t * 2 + phase) * (hover ? 0.035f : 0.015f);
			g.pose().pushMatrix();
			g.pose().translate(x + size / 2f, y + size / 2f);
			g.pose().scale(pulse, pulse);
			g.pose().translate(-size / 2f, -size / 2f);
			drawIcon(g, p, 0, 0, size, 0xFFFFFFFF);
			g.pose().popMatrix();
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x + size / 2f, y + size / 2f + bob);
		if (hover) {
			// hovered: the item turns around like a dropped item, with a bit of thickness
			double turn = (t - hoverStart(phase)) * 3.2;
			float sx = (float) Math.cos(turn);
			float scale = 1.12f;
			if (Math.abs(sx) < 0.12f) sx = Math.copySign(0.12f, sx);
			g.pose().scale(scale, scale);
			for (int i = 3; i >= 1; i--) { // edge / depth
				g.pose().pushMatrix();
				g.pose().translate((float) Math.sin(turn) * i * 0.45f, 0);
				g.pose().scale(sx, 1f);
				g.pose().translate(-size / 2f, -size / 2f);
				drawIcon(g, p, 0, 0, size, 0xFF000000 | (0x50 + i * 0x10) * 0x010101);
				g.pose().popMatrix();
			}
			g.pose().scale(sx, 1f);
		} else {
			// idle: floats, sways and breathes a little
			float sway = (float) Math.sin(t * 1.4 + phase) * 0.07f;
			float breathe = 1f + (float) Math.sin(t * 2.6 + phase) * 0.02f;
			g.pose().rotate(sway);
			g.pose().scale(breathe, breathe);
		}
		g.pose().translate(-size / 2f, -size / 2f);
		drawIcon(g, p, 0, 0, size, 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private static final java.util.Map<Float, Float> HOVER_START = new java.util.HashMap<>();
	private static long lastHoverFrame;

	/** Lets the spin start facing forward when the mouse enters a card. */
	private static float hoverStart(float phase) {
		long now = System.currentTimeMillis();
		if (now - lastHoverFrame > 120) HOVER_START.clear();
		lastHoverFrame = now;
		return HOVER_START.computeIfAbsent(phase, k -> time());
	}

	/** Small twinkling stars around a selected card's preview. */
	public static void sparkles(GuiGraphicsExtractor g, int cx, int cy, int radius, float phase) {
		float t = time();
		for (int i = 0; i < 4; i++) {
			double a = t * 1.3 + i * Math.PI / 2 + phase;
			int px = cx + (int) Math.round(Math.cos(a) * radius), py = cy + (int) Math.round(Math.sin(a) * radius * 0.8);
			float tw = (float) (Math.sin(t * 5 + i * 1.7) * 0.5 + 0.5);
			int alpha = (int) (90 + tw * 165);
			int c = (alpha << 24) | 0xFFFFFF;
			g.fill(px, py - 1, px + 1, py + 2, c);
			g.fill(px - 1, py, px + 2, py + 1, c);
		}
	}

	// ---- smart icons: entity/GUI atlases are cut into a readable picture, strips animate, everything keeps its aspect

	private record Part(int u, int v, int w, int h, int dx, int dy, boolean flipX, boolean flipY) {
		Part(int u, int v, int w, int h, int dx, int dy) {
			this(u, v, w, h, dx, dy, false, false);
		}
	}

	private record Layout(int base, int lw, int lh, Part... parts) {
	}

	private static Layout layoutFor(String path) {
		if (path == null) return null;
		String p = path.toLowerCase(java.util.Locale.ROOT);
		if (!p.contains("entity/") && !p.contains("models/armor") && !p.contains("gui/container/inventory")) return null;
		if (p.contains("gui/container/inventory")) return new Layout(256, 176, 166, new Part(0, 0, 176, 166, 0, 0));
		if (p.contains("shield")) return new Layout(64, 12, 22, new Part(1, 1, 12, 22, 0, 0));
		if (p.contains("entity/chest/") && !p.contains("_left") && !p.contains("_right")) return new Layout(64, 14, 15,
				new Part(42, 14, 14, 5, 0, 0, false, true),
				new Part(42, 33, 14, 10, 0, 5, false, true),
				new Part(4, 1, 2, 4, 6, 3, false, true));
		if (p.contains("humanoid_leggings") || p.contains("layer_2")) return new Layout(64, 8, 12,
				new Part(4, 20, 4, 12, 0, 0), new Part(4, 20, 4, 12, 4, 0, true, false));
		if (p.contains("equipment/humanoid") || p.contains("layer_1") || p.contains("models/armor")) return new Layout(64, 16, 20,
				new Part(8, 8, 8, 8, 4, 0), new Part(20, 20, 8, 12, 4, 8),
				new Part(44, 20, 4, 12, 0, 8), new Part(44, 20, 4, 12, 12, 8, true, false));
		if (p.contains("elytra")) return new Layout(64, 20, 20,
				new Part(24, 2, 10, 20, 0, 0, true, false), new Part(24, 2, 10, 20, 10, 0));
		if (p.contains("end_crystal")) return new Layout(64, 8, 8, new Part(40, 8, 8, 8, 0, 0), new Part(8, 8, 8, 8, 0, 0));
		return null;
	}

	/** Draws a preview texture nicely into a size x size box. */
	public static void drawIcon(GuiGraphicsExtractor g, PreviewCache.Preview p, int x, int y, int size, int color) {
		int tw = p.width(), th = p.height();
		if (tw <= 0 || th <= 0) return;
		Layout l = layoutFor(p.path());
		if (l != null && tw >= l.base() / 2) {
			float s = tw / (float) l.base();
			float fit = size / (float) Math.max(l.lw(), l.lh());
			g.pose().pushMatrix();
			g.pose().translate(x + (size - l.lw() * fit) / 2f, y + (size - l.lh() * fit) / 2f);
			g.pose().scale(fit, fit);
			for (Part q : l.parts()) {
				// mirrored parts are flipped through the texture coordinates (a mirrored quad would be culled)
				int uw = Math.max(1, Math.round(q.w() * s)), vh = Math.max(1, Math.round(q.h() * s));
				float u = q.u() * s, v = q.v() * s;
				if (q.flipX()) {
					u += uw;
					uw = -uw;
				}
				if (q.flipY()) {
					v += vh;
					vh = -vh;
				}
				g.blit(RenderPipelines.GUI_TEXTURED, p.id(), q.dx(), q.dy(), u, v, q.w(), q.h(), uw, vh, tw, th, color);
			}
			g.pose().popMatrix();
			return;
		}
		if (p.path() != null && p.path().contains("/sky/") && tw * 2 == th * 3) {
			// sky box (3x2 faces): slowly look around along the horizon (bottom row)
			int face = tw / 3;
			float pan = (float) ((Math.sin(time() * 0.35) * 0.5 + 0.5) * face * 2);
			g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, pan, (float) face, size, size, face, face, tw, th, color);
			return;
		}
		if (th > tw && th % tw == 0) { // animated strip (fire, clocks, ...): play the frames
			int frames = th / tw;
			int f = (int) (time() * 8) % frames;
			g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, 0f, (float) (f * tw), size, size, tw, tw, tw, th, color);
			return;
		}
		float fit = size / (float) Math.max(tw, th);
		int dw = Math.max(1, Math.round(tw * fit)), dh = Math.max(1, Math.round(th * fit));
		g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x + (size - dw) / 2, y + (size - dh) / 2, 0f, 0f, dw, dh, tw, th, tw, th, color);
	}

	public static void drawFlat(GuiGraphicsExtractor g, PreviewCache.Preview p, int x, int y, int size, int color) {
		int frameH = Math.min(p.width(), p.height());
		int frames = Math.max(1, p.height() / Math.max(1, frameH));
		int f = frames > 1 ? (int) (time() * 10) % frames : 0; // animated block textures play in the 3D preview too
		g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, 0f, (float) (f * frameH), size, size, p.width(), frameH, p.width(), p.height(), color);
	}

	/** Static isometric cube (kept for older callers). */
	public static void drawCube(GuiGraphicsExtractor g, PreviewCache.Preview top, PreviewCache.Preview side, int topTint, int x, int y, int size) {
		drawCube(g, top, side, topTint, x, y, size, 45f);
	}

	/**
	 * Real 3D cube turned around the vertical axis by {@code yawDeg}, seen slightly from above.
	 * Every visible face is a parallelogram on screen, so it can be drawn with one affine transform.
	 */
	public static void drawCube(GuiGraphicsExtractor g, PreviewCache.Preview top, PreviewCache.Preview side, int topTint,
			float x, float y, int size, float yawDeg) {
		double a = Math.toRadians(yawDeg);
		double ca = Math.cos(a), sa = Math.sin(a);
		double pitchCos = 0.82, pitchSin = 0.5;
		double k = size * 0.42;                      // half edge length on screen
		double cx = x + size / 2.0, cy = y + size / 2.0 + size * 0.04;

		// side faces: normal (nx, nz); right vector r = (nz, -nx)
		int[][] normals = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
		for (int[] n : normals) {
			double nzr = n[0] * sa + n[1] * ca;      // z of the rotated normal (towards the viewer)
			if (nzr <= 0.001) continue;
			double rx = n[1], rz = -n[0];
			double[] tl = {n[0] * 0.5 - rx * 0.5, 0.5, n[1] * 0.5 - rz * 0.5};
			double[] tr = {tl[0] + rx, 0.5, tl[2] + rz};
			double[] bl = {tl[0], -0.5, tl[2]};
			float shade = (float) (0.55 + 0.35 * nzr);
			faceQuad(g, side, project(tl, ca, sa, pitchCos, pitchSin, k, cx, cy), project(tr, ca, sa, pitchCos, pitchSin, k, cx, cy),
					project(bl, ca, sa, pitchCos, pitchSin, k, cx, cy), shade(0xFFFFFFFF, shade));
		}
		// top face
		double[] ttl = {-0.5, 0.5, -0.5}, ttr = {0.5, 0.5, -0.5}, tbl = {-0.5, 0.5, 0.5};
		faceQuad(g, top, project(ttl, ca, sa, pitchCos, pitchSin, k, cx, cy), project(ttr, ca, sa, pitchCos, pitchSin, k, cx, cy),
				project(tbl, ca, sa, pitchCos, pitchSin, k, cx, cy), topTint);
	}

	private static float[] project(double[] p, double ca, double sa, double pc, double ps, double k, double cx, double cy) {
		double xr = p[0] * ca - p[2] * sa;
		double zr = p[0] * sa + p[2] * ca;
		double sx = xr;
		double sy = -p[1] * pc + zr * ps;
		return new float[]{(float) (cx + sx * k), (float) (cy + sy * k)};
	}

	/** Draws a 16x16 texture onto the parallelogram tl / tr / bl. */
	private static void faceQuad(GuiGraphicsExtractor g, PreviewCache.Preview p, float[] tl, float[] tr, float[] bl, int color) {
		int t = 16;
		Matrix3x2f m = new Matrix3x2f((tr[0] - tl[0]) / t, (tr[1] - tl[1]) / t, (bl[0] - tl[0]) / t, (bl[1] - tl[1]) / t, tl[0], tl[1]);
		face(g, p, m, t, color);
	}

	private static void face(GuiGraphicsExtractor g, PreviewCache.Preview p, Matrix3x2f m, int t, int color) {
		g.pose().pushMatrix();
		g.pose().mul(m);
		drawFlat(g, p, 0, 0, t, color);
		g.pose().popMatrix();
	}

	public static int shade(int argb, float f) {
		int a = argb >>> 24;
		int r = (int) (((argb >> 16) & 0xFF) * f), gr = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
		return (a << 24) | (r << 16) | (gr << 8) | b;
	}

	// ======================================================================= widgets

	public abstract static class FlatButton extends AbstractButton {
		private final Runnable action;

		protected FlatButton(int x, int y, int w, int h, Component message, Runnable action) {
			super(x, y, w, h, message);
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}

	/** Category icon in the bar (white dot top-right, grey square when selected). */
	public static class IconButton extends FlatButton {
		private final PreviewCache cache;
		private final Category category;
		private final boolean selected;
		private final boolean changed;

		public IconButton(int x, int y, int size, PreviewCache cache, Category category, boolean selected, boolean changed, Runnable action) {
			super(x, y, size, size, Component.literal(category.displayName()), action);
			this.cache = cache;
			this.category = category;
			this.selected = selected;
			this.changed = changed;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), s = getWidth();
			if (selected) {
				g.fill(x, y, x + s, y + s, 0x55FFFFFF);
				g.outline(x, y, s, s, 0x90FFFFFF);
			} else if (isHoveredOrFocused()) {
				g.fill(x, y, x + s, y + s, 0x25FFFFFF);
			}
			drawPreview(g, cache, category, null, x + 2, y + 2, s - 4, category.id.hashCode() % 7, isHoveredOrFocused(), isHoveredOrFocused() || selected);
			g.fill(x + s - 2, y, x + s, y + 2, changed ? GOLD : 0xFFFFFFFF);
		}
	}

	/** Pack card. */
	public static class Card extends FlatButton {
		private final PreviewCache cache;
		private final Category category;
		private final SourcePack pack;
		private final String label;
		private final boolean selected;
		private final boolean favorite;

		public Card(int x, int y, int w, int h, PreviewCache cache, Category category, SourcePack pack, String label,
				boolean selected, Runnable action) {
			this(x, y, w, h, cache, category, pack, label, selected, false, action);
		}

		public Card(int x, int y, int w, int h, PreviewCache cache, Category category, SourcePack pack, String label,
				boolean selected, boolean favorite, Runnable action) {
			super(x, y, w, h, Component.literal(label), action);
			this.cache = cache;
			this.category = category;
			this.pack = pack;
			this.label = label;
			this.selected = selected;
			this.favorite = favorite;
		}

		public SourcePack pack() {
			return pack;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean hover = isHoveredOrFocused();
			g.fill(x, y, x + w, y + h, selected ? 0x40FFFFFF : hover ? 0x30FFFFFF : CARD);
			g.fillGradient(x, y, x + w, y + h / 2, 0x14FFFFFF, 0x00FFFFFF);
			int size = Math.min(w - 14, h - 20);
			float phase = (pack == null ? 0 : pack.name.hashCode() % 1000) * 0.0123f;
			g.enableScissor(x, y, x + w, y + h);
			drawPreview(g, cache, category, pack, x + (w - size) / 2, y + 5, size, phase, hover, true);
			if (selected) sparkles(g, x + w / 2, y + 5 + size / 2, size / 2 + 2, phase);
			g.disableScissor();
			if (pack != null) badge(g, BuiltinPacks.isBuiltin(pack.name) ? "TS" : "PACK", x + 3, y + h - 20);
			if (favorite) g.text(font(), "★", x + w - 9, y + 3, GOLD, false);
			packName(g, label, x + w / 2, y + h - 10, w - 6, selected || hover);
			if (selected) selectedFrame(g, x, y, w, h);
		}
	}

	/** Tab / list entry / small header button. */
	public static class TextButton extends FlatButton {
		private final boolean highlighted;
		private final int color;

		public TextButton(int x, int y, int w, int h, Component text, boolean highlighted, int color, Runnable action) {
			super(x, y, w, h, text, action);
			this.highlighted = highlighted;
			this.color = color;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			if (highlighted) {
				g.fill(x, y, x + w, y + h, 0x45FFFFFF);
				g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
			} else if (isHoveredOrFocused()) {
				g.fill(x, y, x + w, y + h, 0x22FFFFFF);
			}
			int c = !active ? 0xFF6A6478 : highlighted || isHoveredOrFocused() ? TEXT : color == TEXT ? DIM : color;
			g.text(font(), getMessage(), x + 5, y + (h - 8) / 2, c, false);
		}
	}

	// ---- light / dark buttons (API kept from the earlier versions)

	public interface Press {
		void press(GlassButton button);
	}

	public static GlassBuilder glass(Component text, Press press) {
		return new GlassBuilder(text, press);
	}

	public static final class GlassBuilder {
		private final Component text;
		private final Press press;
		private int x, y, w = 150, h = 20;
		private boolean light;
		private Tooltip tooltip;

		GlassBuilder(Component text, Press press) {
			this.text = text;
			this.press = press;
		}

		public GlassBuilder bounds(int x, int y, int w, int h) {
			this.x = x; this.y = y; this.w = w; this.h = h;
			return this;
		}

		public GlassBuilder tooltip(Tooltip t) {
			this.tooltip = t;
			return this;
		}

		/** GOLD / ACCENT -> light primary button (like "Done"), otherwise dark button. */
		public GlassBuilder accent(int argb) {
			this.light = argb == GOLD || argb == ACCENT;
			return this;
		}

		public GlassBuilder light() {
			this.light = true;
			return this;
		}

		public GlassButton build() {
			GlassButton b = new GlassButton(x, y, w, h, text, light, null);
			b.press = press;
			if (tooltip != null) b.setTooltip(tooltip);
			return b;
		}
	}

	/** Light button (Done / Save / Settings) or dark translucent button. */
	public static class GlassButton extends FlatButton {
		private final boolean light;
		private Press press;

		public GlassButton(int x, int y, int w, int h, Component text, boolean light, Runnable action) {
			super(x, y, w, h, text, action == null ? () -> { } : action);
			this.light = light;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (press != null) press.press(this);
			else super.onPress(input);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean hover = isHoveredOrFocused() && active;
			int textColor;
			if (light && active) {
				g.fillGradient(x, y, x + w, y + h, hover ? 0xFFF4F3FA : 0xFFDCDAE6, hover ? 0xFFD2D0E0 : 0xFFB5B2C6);
				g.outline(x, y, w, h, 0xFF8E8AA4);
				textColor = 0xFF2A2638;
			} else {
				g.fill(x, y, x + w, y + h, hover ? 0x40FFFFFF : 0x30000000);
				g.outline(x, y, w, h, hover ? 0x80FFFFFF : 0x40FFFFFF);
				textColor = active ? TEXT : 0xFF6A6478;
			}
			g.text(font(), getMessage(), x + (w - font().width(getMessage())) / 2, y + (h - 8) / 2, textColor, false);
		}
	}

	/** Big light "Settings" button with the slider icon (top-right of the bar). */
	public static class SettingsButton extends FlatButton {
		public SettingsButton(int x, int y, int w, int h, Component text, Runnable action) {
			super(x, y, w, h, text, action);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean hover = isHoveredOrFocused();
			g.fillGradient(x, y, x + w, y + h, hover ? 0xFFF4F3FA : 0xFFE2E0EC, hover ? 0xFFD2D0E0 : 0xFFBDBACD);
			g.outline(x, y, w, h, 0xFF8E8AA4);
			slidersIcon(g, x + 6, y + h / 2 - 3, 0xFF3A3650);
			g.text(font(), getMessage(), x + 19, y + (h - 8) / 2, 0xFF2A2638, false);
		}
	}

	private Ui() {
	}
}
