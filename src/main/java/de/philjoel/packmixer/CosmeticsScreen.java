package de.philjoel.packmixer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Cosmetics (layout as in the reference video): player left, Head/Face/Back tabs and cards right. */
public class CosmeticsScreen extends Screen {
	static Cosmetics.Slot lastSlot = Cosmetics.Slot.HEAD;

	private final Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private Cosmetics.Slot slot = lastSlot;
	private int x0, y0, x1, y1, previewX1;

	public CosmeticsScreen(Screen parent) {
		super(Component.translatable("packmixer.cosmetics"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Ui.preloadCosmetics();
		GuiFit.fit(this);
		int w = Math.min(this.width - 16, Math.max(380, (int) (this.width * 0.62)));
		int h = Math.min(this.height - 26, Math.max(220, (int) (this.height * 0.66)));
		x0 = (this.width - w) / 2;
		x1 = x0 + w;
		y0 = Math.max(18, (this.height - h) / 2);
		y1 = y0 + h;
		previewX1 = x0 + Math.max(110, w * 7 / 20);
		PackMixerConfig.Settings s = PackMixerConfig.settings();

		// "On" switch top-right
		Component on = Component.literal(s.cosmeticsEnabled ? "■ " : "□ ")
				.append(Component.translatable(s.cosmeticsEnabled ? "packmixer.on_short" : "packmixer.off_short"));
		addRenderableWidget(Ui.glass(on, b -> {
			s.cosmeticsEnabled = !s.cosmeticsEnabled;
			PackMixerConfig.saveSettings();
			rebuildWidgets();
		}).bounds(x1 - 10 - 34, y0 + 4, 34, 12).light().build());

		// tabs
		int tabsX = previewX1 + 6;
		int nTabs = Cosmetics.Slot.values().length;
		int tabW = (x1 - 10 - tabsX - 4 * (nTabs - 1)) / nTabs;
		int i = 0;
		for (Cosmetics.Slot sl : Cosmetics.Slot.values()) {
			final Cosmetics.Slot target = sl;
			boolean activeTab = sl == slot;
			boolean worn = !sl.get().isEmpty();
			addRenderableWidget(new Ui.FlatButton(tabsX + i * (tabW + 4), y0 + 24, tabW, 13,
					Component.translatable("packmixer.slot." + sl.name().toLowerCase()), () -> {
				slot = target;
				lastSlot = target;
				rebuildWidgets();
			}) {
				@Override
				protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
					if (activeTab) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x55FFFFFF);
					else if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
					String t = getMessage().getString() + (worn ? " •" : " ·");
					g.drawString(font, t, getX() + (getWidth() - font.width(t)) / 2, getY() + 3, activeTab ? Ui.TEXT : Ui.DIM, false);
				}
			});
			i++;
		}

		// cards
		int gx0 = tabsX;
		int gy0 = y0 + 42;
		int gw = x1 - 10 - gx0;
		int cols = slot.items.size() + 1 > 12 ? 5 : 4;
		int cw = (gw - (cols - 1) * 4) / cols;
		int rows = (slot.items.size() + 1 + cols - 1) / cols;
		int ch = Math.min(cw, (y1 - 22 - gy0 - 4 * (rows - 1)) / Math.max(2, rows));
		String current = slot.get();
		int n = 0;
		addRenderableWidget(new ItemCard(gx0, gy0, cw, ch, "", current.isEmpty()));
		n++;
		for (String id : slot.items) {
			int x = gx0 + (n % cols) * (cw + 4);
			int y = gy0 + (n / cols) * (ch + 4);
			if (y + ch > y1 - 20) break;
			ItemCard card = new ItemCard(x, y, cw, ch, id, id.equals(current));
			Cosmetics.Rarity rarity = Cosmetics.rarity(id);
			card.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("packmixer.hat." + id)
					.append(Component.literal("\n")).append(Component.translatable("packmixer.rarity." + rarity.name().toLowerCase()).withStyle(rarity.format))));
			addRenderableWidget(card);
			n++;
		}

		// footer
		if (slot == Cosmetics.Slot.EFFECT) {
			Component fp = Component.translatable("packmixer.effects.first_person").append(": ").append(Component.translatable(s.effectsFirstPerson ? "packmixer.on" : "packmixer.off"));
			int fw = this.font.width(fp) + 12;
			addRenderableWidget(Ui.glass(fp, b -> {
				s.effectsFirstPerson = !s.effectsFirstPerson;
				PackMixerConfig.saveSettings();
				rebuildWidgets();
			}).bounds((previewX1 + x1) / 2 - fw / 2, y1 - 17, fw, 13).build());
		}
		addRenderableWidget(new Ui.TextButton(x0 + 8, y1 - 16, 70, 12, Component.translatable("packmixer.cosmetics.remove_all"), false, Ui.TEXT, () -> {
			for (Cosmetics.Slot sl : Cosmetics.Slot.values()) sl.set("");
			rebuildWidgets();
		}));
		int backW = this.font.width(Component.translatable("packmixer.settings.back")) + 12;
		addRenderableWidget(new Ui.TextButton(x1 - 12 - backW, y1 - 16, backW, 12, Component.translatable("packmixer.settings.back"),
				false, Ui.TEXT, this::onClose));
	}

	/** Card with a small dark bust wearing the item (like the reference). */
	private class ItemCard extends Ui.FlatButton {
		private final String id;
		private final boolean selected;

		ItemCard(int x, int y, int w, int h, String id, boolean selected) {
			super(x, y, w, h, id.isEmpty() ? Component.translatable("packmixer.cosmetics.none") : Component.translatable("packmixer.hat." + id), () -> {
				slot.set(id);
				CosmeticsScreen.this.rebuildWidgets();
			});
			this.id = id;
			this.selected = selected;
		}

		@Override
		protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			Cosmetics.Rarity rarity = id.isEmpty() ? Cosmetics.Rarity.COMMON : Cosmetics.rarity(id);
			g.fill(x, y, x + w, y + h, selected ? 0x40FFFFFF : isHoveredOrFocused() ? 0x30FFFFFF : Ui.CARD);
			if (!id.isEmpty()) {
				// rarity glow from the bottom
				g.fillGradient(x, y + h / 2, x + w, y + h, rarity.color & 0x00FFFFFF, (rarity.color & 0x00FFFFFF) | 0x38000000);
				g.fill(x, y + h - 1, x + w, y + h, rarity.color);
			}
			int cx = x + w / 2;
			int headS = Math.max(8, h / 3);
			int headY = y + h / 2 - headS / 2 - 2;
			float t = Ui.time();
			if (id.isEmpty()) {
				g.renderOutline(cx - 9, headY - 2, 18, 18, Ui.DIM);
				for (int k = -8; k <= 8; k++) g.fill(cx + k, headY + 7 - k, cx + k + 1, headY + 8 - k, Ui.DIM);
			} else if (slot == Cosmetics.Slot.EFFECT) {
				// animated swirl of the effect symbol
				String glyph = EFFECT_GLYPH.getOrDefault(id, "✦");
				int col = EFFECT_COLOR.getOrDefault(id, 0xFFFFFFFF);
				int r = Math.max(6, h / 4);
				for (int k = 0; k < 4; k++) {
					double a = t * 1.6 + k * Math.PI / 2;
					int gx = cx + (int) (Math.cos(a) * r) - 3;
					int gy = y + h / 2 - 6 + (int) (Math.sin(a) * r * 0.5);
					g.drawString(font, glyph, gx, gy, k % 2 == 0 ? col : Ui.lerp(col, 0xFFFFFFFF, 0.5f), false);
				}
				g.pose().pushMatrix();
				g.pose().translate(cx, y + h / 2f - 4);
				g.pose().scale(2f, 2f);
				g.drawString(font, glyph, -font.width(glyph) / 2, -4, col, true);
				g.pose().popMatrix();
			} else {
				// one 3D model: a dark bust wearing the item, so it sits exactly where it sits on your skin
				int is = Math.min(w - 6, h - 12);
				float bob = (float) Math.sin(t * 2 + id.hashCode() % 7) * 1.2f;
				g.pose().pushMatrix();
				g.pose().translate(0, bob);
				Ui.cosmetic(g, Cosmetics.cardStack(id), id, cx - is / 2, y + 2, is);
				g.pose().popMatrix();
			}
			String label = getMessage().getString();
			if (Ui.smallWidth(label) > w - 4) {
				while (label.length() > 1 && Ui.smallWidth(label + "..") > w - 4) label = label.substring(0, label.length() - 1);
				label = label + "..";
			}
			Ui.small(g, label, cx - Ui.smallWidth(label) / 2, y + h - 9, id.isEmpty() ? (selected ? Ui.TEXT : Ui.DIM)
					: rarity == Cosmetics.Rarity.COMMON ? (selected ? Ui.TEXT : Ui.DIM) : rarity.color);
			if (rarity == Cosmetics.Rarity.LEGENDARY && !id.isEmpty()) {
				// running golden shimmer around legendary cards
				int per = 2 * (w + h);
				int pos = (int) ((t * 60) % per);
				for (int k = 0; k < 14; k++) {
					int pp = (pos + k) % per;
					int px, py;
					if (pp < w) { px = x + pp; py = y; }
					else if (pp < w + h) { px = x + w - 1; py = y + pp - w; }
					else if (pp < 2 * w + h) { px = x + w - 1 - (pp - w - h); py = y + h - 1; }
					else { px = x; py = y + h - 1 - (pp - 2 * w - h); }
					g.fill(px, py, px + 1, py + 1, Ui.lerp(0x00FFC23A, 0xFFFFE9A0, k / 13f));
				}
			}
			if (selected) Ui.selectedFrame(g, x, y, w, h);
		}
	}

	private static final java.util.Map<String, String> EFFECT_GLYPH = java.util.Map.ofEntries(
			java.util.Map.entry("fx_sparkle", "✦"), java.util.Map.entry("fx_souls", "✺"), java.util.Map.entry("fx_flames", "✹"),
			java.util.Map.entry("fx_snow", "❄"), java.util.Map.entry("fx_cherry", "✿"), java.util.Map.entry("fx_enchant", "✧"),
			java.util.Map.entry("fx_hearts", "♥"), java.util.Map.entry("fx_notes", "♪"), java.util.Map.entry("fx_rainbow", "◎"),
			java.util.Map.entry("fx_galaxy", "✪"), java.util.Map.entry("fx_totem", "✵"), java.util.Map.entry("fx_lightning", "⚡"),
			java.util.Map.entry("fx_void", "●"), java.util.Map.entry("fx_lightwings", "❦"),
			java.util.Map.entry("fx_inferno", "✹"), java.util.Map.entry("fx_plasma", "✺"), java.util.Map.entry("fx_frost", "❄"),
			java.util.Map.entry("fx_energy", "◎"), java.util.Map.entry("fx_thunder", "⚡"), java.util.Map.entry("fx_blackhole", "◉"),
			java.util.Map.entry("fx_holo", "◇"), java.util.Map.entry("fx_toxic", "☣"), java.util.Map.entry("fx_matrix", "▤"),
			java.util.Map.entry("fx_heartbeat", "♥"));
	private static final java.util.Map<String, Integer> EFFECT_COLOR = java.util.Map.ofEntries(
			java.util.Map.entry("fx_sparkle", 0xFFFFF4C0), java.util.Map.entry("fx_souls", 0xFF5FE6FF), java.util.Map.entry("fx_flames", 0xFFFF8A2A),
			java.util.Map.entry("fx_snow", 0xFFDFF4FF), java.util.Map.entry("fx_cherry", 0xFFFFA8D8), java.util.Map.entry("fx_enchant", 0xFFB38CFF),
			java.util.Map.entry("fx_hearts", 0xFFFF4D6A), java.util.Map.entry("fx_notes", 0xFF6BFF8F), java.util.Map.entry("fx_rainbow", 0xFFFF7AD9),
			java.util.Map.entry("fx_galaxy", 0xFF8A6CFF), java.util.Map.entry("fx_totem", 0xFFB6FF4A), java.util.Map.entry("fx_lightning", 0xFF7DF9FF),
			java.util.Map.entry("fx_void", 0xFF9B30FF), java.util.Map.entry("fx_lightwings", 0xFFFFF6D0),
			java.util.Map.entry("fx_inferno", 0xFFFF3A1A), java.util.Map.entry("fx_plasma", 0xFFFF4FD8), java.util.Map.entry("fx_frost", 0xFF8FF5EA),
			java.util.Map.entry("fx_energy", 0xFF35F2DD), java.util.Map.entry("fx_thunder", 0xFFFFB800),
			java.util.Map.entry("fx_blackhole", 0xFFB45CFF), java.util.Map.entry("fx_holo", 0xFF9FE8FF), java.util.Map.entry("fx_toxic", 0xFF45FF2A),
			java.util.Map.entry("fx_matrix", 0xFF00FF66), java.util.Map.entry("fx_heartbeat", 0xFFFF1F5A));

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float in = Ui.intro(openedAt);
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - in) * 6);
		renderContent(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	private void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Ui.header(g, this.width, null);
		Ui.window(g, x0, y0, x1, y1);
		Ui.headerBar(g, x0, y0, x1);
		g.drawString(this.font, Component.translatable("packmixer.cosmetics"), x0 + 12, y0 + 6, Ui.TEXT, false);
		Ui.small(g, Component.translatable("packmixer.cosmetics.only_you").getString(),
				x0 + 16 + this.font.width(Component.translatable("packmixer.cosmetics")), y0 + 7, Ui.FAINT);
		g.fill(previewX1 + 1, y0 + 22, previewX1 + 2, y1 - 20, Ui.LINE);

		if (this.minecraft.player != null) {
			int top = y0 + 26;
			int bottom = y1 - 24;
			int size = Math.max(30, Math.min(80, (bottom - top) / 2 - 6));
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0 + 6, top, previewX1 - 4, bottom, size, 0.0625f, mouseX, mouseY, this.minecraft.player);
			String hint = Component.translatable("packmixer.cosmetics.rotate").getString();
			Ui.small(g, hint, (x0 + previewX1) / 2 - Ui.smallWidth(hint) / 2, y1 - 30, Ui.FAINT);
		} else {
			// main menu (no player yet): show what you wear as big items
			int cx = (x0 + previewX1) / 2, cy = (y0 + 26 + y1 - 24) / 2;
			int n = 0;
			for (Cosmetics.Slot sl : new Cosmetics.Slot[]{Cosmetics.Slot.HEAD, Cosmetics.Slot.FACE, Cosmetics.Slot.BACK}) {
				String id = sl.get();
				if (id.isEmpty()) continue;
				int size = n == 0 ? 56 : 30;
				int iy = n == 0 ? cy - 40 : cy + 22;
				int ix = n == 0 ? cx - size / 2 : cx - 34 + (n - 1) * 38;
				float bob = (float) Math.sin(Ui.time() * 2 + n) * 2;
				g.pose().pushMatrix();
				g.pose().translate(0, bob);
				Ui.cosmetic(g, Cosmetics.hatStack(id), id, ix, iy, size);
				g.pose().popMatrix();
				n++;
			}
		}

		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		GuiFit.restore();
	}
}
