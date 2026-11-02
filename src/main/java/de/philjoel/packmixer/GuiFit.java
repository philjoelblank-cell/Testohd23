package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Makes the menus fit at every GUI scale: if the chosen scale leaves too little room (e.g. "4" on a small
 * screen), the menu temporarily uses the next smaller scale. Your normal scale comes back when it closes.
 */
public final class GuiFit {
	private static final int MIN_W = 430, MIN_H = 250;

	/** Call first thing in init(). */
	public static void fit(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		Window w = mc.getWindow();
		int scale = w.getGuiScale();
		while (scale > 1 && (w.getWidth() / scale < MIN_W || w.getHeight() / scale < MIN_H)) scale--;
		if (scale != w.getGuiScale()) {
			w.setGuiScale(scale);
			screen.width = w.getGuiScaledWidth();
			screen.height = w.getGuiScaledHeight();
		}
	}

	/** Call in removed(): back to the scale from the options. */
	public static void restore() {
		Minecraft mc = Minecraft.getInstance();
		Window w = mc.getWindow();
		int normal = w.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
		if (normal != w.getGuiScale()) w.setGuiScale(normal);
	}

	private GuiFit() {
	}
}
