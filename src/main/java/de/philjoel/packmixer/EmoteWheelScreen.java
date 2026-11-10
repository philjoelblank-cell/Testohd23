package de.philjoel.packmixer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Emote wheel (key B): click an emote (or press 1-8) to play it. */
public class EmoteWheelScreen extends Screen {
	private final long openedAt = System.currentTimeMillis();
	private static final int PER_PAGE = 8;
	private static int page;

	private java.util.List<Emotes.Emote> list() {
		int pages = pages();
		page = Math.floorMod(page, pages);
		return Emotes.ALL.subList(page * PER_PAGE, Math.min(Emotes.ALL.size(), (page + 1) * PER_PAGE));
	}

	private static int pages() {
		return (Emotes.ALL.size() + PER_PAGE - 1) / PER_PAGE;
	}

	public EmoteWheelScreen() {
		super(Component.translatable("packmixer.emotes"));
	}

	private int cx() {
		return this.width / 2;
	}

	private int cy() {
		return this.height / 2;
	}

	private int radius() {
		return Math.max(50, Math.min(118, Math.min(this.width, this.height) / 2 - 50));
	}

	/** Centre of card i. */
	private int[] pos(int i) {
		int n = list().size();
		double a = (n <= 2 ? Math.PI : -Math.PI / 2) + i * 2 * Math.PI / n; // 1-2 emotes: left / right
		return new int[]{cx() + (int) (Math.cos(a) * radius()), cy() + (int) (Math.sin(a) * radius())};
	}

	private int hovered(double mx, double my) {
		double d = Math.hypot(mx - cx(), my - cy());
		if (d < 22) return -1;
		int n = list().size();
		double a = Math.atan2(my - cy(), mx - cx()) - (n <= 2 ? Math.PI : -Math.PI / 2);
		int i = (int) Math.floor(((a / (2 * Math.PI)) * n + 0.5 + n) % n);
		return i;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float in = 1f; // no fly-in (some versions draw the screen only once)
		g.fill(0, 0, this.width, this.height, (int) (0x70 * in) << 24);
		int hov = hovered(mouseX, mouseY);
		double chord = 2 * radius() * Math.sin(Math.PI / Math.max(3, list().size()));
		int w = (int) Math.min(64, chord - 4), h = (int) Math.min(78, chord * 0.85);
		java.util.List<Emotes.Emote> list = list();
		for (int i = 0; i < list.size(); i++) {
			Emotes.Emote e = list.get(i);
			int[] p = pos(i);
			int px = cx() + (int) ((p[0] - cx()) * in), py = cy() + (int) ((p[1] - cy()) * in);
			boolean hv = i == hov;
			g.fill(px - w / 2, py - h / 2, px + w / 2, py + h / 2, hv ? 0xC0A855F7 : 0xA0181424);
			g.fill(px - w / 2, py + h / 2 - 1, px + w / 2, py + h / 2, hv ? 0xFFFFFFFF : 0xFF7A4BC8);
			// the emote itself, played by a small you
			int idx = Emotes.ALL.indexOf(e);
			boolean drawn = Doll.draw(g, px - w / 2, py - h / 2 + 2, px + w / 2, py + h / 2 - 10, -(Emotes.PREVIEW + idx), 20f, 1.15f);
			if (!drawn) {
				g.pose().pushMatrix();
				g.pose().translate(px, py - 6);
				g.pose().scale(2f, 2f);
				g.drawString(this.font, e.glyph(), -this.font.width(e.glyph()) / 2, -4, hv ? 0xFFFFFFFF : 0xFFE6D6FF, true);
				g.pose().popMatrix();
			}
			String name = Component.translatable("packmixer.emote." + e.id()).getString();
			Ui.small(g, name, px - Ui.smallWidth(name) / 2, py + h / 2 - 9, hv ? 0xFFFFFFFF : Ui.DIM);
			Ui.small(g, String.valueOf(i + 1), px - w / 2 + 3, py - h / 2 + 3, Ui.FAINT);
		}
		if (pages() > 1) {
			String pg = "◀  " + (page + 1) + " / " + pages() + "  ▶";
			Ui.small(g, pg, cx() - Ui.smallWidth(pg) / 2, this.height - 22, 0xFFFFFFFF);
			String hint = Component.translatable("packmixer.emotes.scroll").getString();
			Ui.small(g, hint, cx() - Ui.smallWidth(hint) / 2, this.height - 12, Ui.FAINT);
		}
		String title = Component.translatable("packmixer.emotes").getString();
		g.drawString(this.font, title, cx() - this.font.width(title) / 2, cy() - 4, 0xFFFFFFFF, true);
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = hovered(event.x(), event.y());
		if (pages() > 1 && event.y() > this.height - 26) { // page arrows
			page += event.x() < cx() ? -1 : 1;
			return true;
		}
		if (i >= 0) {
			play(i);
			return true;
		}
		onClose();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) page += scrollY < 0 ? 1 : -1;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k == 262 || k == 263) { // arrow right / left: next / previous page
			page += k == 262 ? 1 : -1;
			return true;
		}
		if (k >= 49 && k < 49 + list().size()) { // 1..8
			play(k - 49);
			return true;
		}
		return super.keyPressed(event);
	}

	private void play(int i) {
		String id = list().get(i).id();
		onClose();
		Emotes.play(id);
	}

	/** For the auto test. */
	static void testPage(int p) {
		page = p;
	}

	/** For the auto test. */
	void testPlay(int i) {
		play(i);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
