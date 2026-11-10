package de.philjoel.packmixer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Emote wheel (key B): click an emote (or press 1-8) to play it. */
public class EmoteWheelScreen extends Screen {
	private final long openedAt = System.currentTimeMillis();

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
		return Math.max(52, Math.min(90, Math.min(this.width, this.height) / 3));
	}

	/** Centre of card i. */
	private int[] pos(int i) {
		double a = -Math.PI / 2 + i * 2 * Math.PI / Emotes.ALL.size();
		return new int[]{cx() + (int) (Math.cos(a) * radius()), cy() + (int) (Math.sin(a) * radius())};
	}

	private int hovered(double mx, double my) {
		double d = Math.hypot(mx - cx(), my - cy());
		if (d < 22) return -1;
		double a = Math.atan2(my - cy(), mx - cx()) + Math.PI / 2;
		int n = Emotes.ALL.size();
		int i = (int) Math.floor(((a / (2 * Math.PI)) * n + 0.5 + n) % n);
		return i;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float in = Math.min(1f, (System.currentTimeMillis() - openedAt) / 150f);
		g.fill(0, 0, this.width, this.height, (int) (0x70 * in) << 24);
		int hov = hovered(mouseX, mouseY);
		int w = 58, h = 36;
		for (int i = 0; i < Emotes.ALL.size(); i++) {
			Emotes.Emote e = Emotes.ALL.get(i);
			int[] p = pos(i);
			int px = cx() + (int) ((p[0] - cx()) * in), py = cy() + (int) ((p[1] - cy()) * in);
			boolean hv = i == hov;
			g.fill(px - w / 2, py - h / 2, px + w / 2, py + h / 2, hv ? 0xC0A855F7 : 0xA0181424);
			g.fill(px - w / 2, py + h / 2 - 1, px + w / 2, py + h / 2, hv ? 0xFFFFFFFF : 0xFF7A4BC8);
			g.pose().pushMatrix();
			g.pose().translate(px, py - 6);
			g.pose().scale(2f, 2f);
			g.text(this.font, e.glyph(), -this.font.width(e.glyph()) / 2, -4, hv ? 0xFFFFFFFF : 0xFFE6D6FF, true);
			g.pose().popMatrix();
			String name = Component.translatable("packmixer.emote." + e.id()).getString();
			Ui.small(g, name, px - Ui.smallWidth(name) / 2, py + 8, hv ? 0xFFFFFFFF : Ui.DIM);
			Ui.small(g, String.valueOf(i + 1), px - w / 2 + 3, py - h / 2 + 3, Ui.FAINT);
		}
		String title = Component.translatable("packmixer.emotes").getString();
		g.text(this.font, title, cx() - this.font.width(title) / 2, cy() - 4, 0xFFFFFFFF, true);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = hovered(event.x(), event.y());
		if (i >= 0) {
			play(i);
			return true;
		}
		onClose();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k >= 49 && k < 49 + Emotes.ALL.size()) { // 1..8
			play(k - 49);
			return true;
		}
		return super.keyPressed(event);
	}

	private void play(int i) {
		onClose();
		Emotes.play(Emotes.ALL.get(i).id());
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
