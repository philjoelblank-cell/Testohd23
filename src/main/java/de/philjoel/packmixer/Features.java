package de.philjoel.packmixer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Runtime state for the gameplay features (zoom). */
public final class Features {
	static KeyMapping zoomKey;

	private static float zoom = 1f;
	private static float prevZoom = 1f;

	/** Called every client tick: smooth zoom in/out. */
	static void tick(Minecraft mc) {
		prevZoom = zoom;
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		boolean active = s.zoomEnabled && zoomKey != null && zoomKey.isDown() && mc.screen == null;
		float target = active ? (float) s.zoomFactor : 1f;
		zoom += (target - zoom) * 0.5f;
		if (Math.abs(zoom - target) < 0.01f) zoom = target;
	}

	/** Current zoom factor (1 = no zoom), interpolated for smooth frames. */
	public static float zoomFactor(float partialTick) {
		return prevZoom + (zoom - prevZoom) * partialTick;
	}

	public static KeyMapping zoomKey() {
		return zoomKey;
	}

	private Features() {
	}
}
