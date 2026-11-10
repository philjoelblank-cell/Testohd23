package de.philjoel.packmixer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Trailer only: you, big, turning slowly on a purple gradient. */
class ShowcaseScreen extends Screen {
	private final long start = System.nanoTime();
	private final float degPerSecond;

	ShowcaseScreen(float degPerSecond) {
		super(Component.literal("showcase"));
		this.degPerSecond = degPerSecond;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xFF2A0F45, 0xFF0B0614);
		float t = (System.nanoTime() - start) / 1e9f;
		int w = this.width, h = this.height;
		Doll.draw(g, w / 2 - h / 2, 0, w / 2 + h / 2, h, 0, 160f + t * degPerSecond, 1.7f);
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
