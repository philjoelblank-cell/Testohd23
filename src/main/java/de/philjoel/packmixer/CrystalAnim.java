package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/**
 * Animates the placed end crystal: entity textures have no .mcmeta animation, so when the chosen crystal
 * pack has an animation strip, the crystal texture is replaced by our own texture that gets the next
 * frame uploaded every tick (blended, so it runs smoothly).
 */
final class CrystalAnim {
	private static final Identifier TEX = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	private static final Identifier STRIP = Identifier.withDefaultNamespace("textures/entity/end_crystal/" + CrystalPacks.STRIP_NAME);
	/** Ticks per frame. */
	private static final int SPEED = 2;

	private static NativeImage[] frames;
	private static DynamicTexture dyn;
	private static boolean needReload = true;
	private static boolean broken;
	private static long ticks;

	static void markDirty() {
		needReload = true;
	}

	static void tick(Minecraft mc) {
		if (broken) return;
		try {
			if (mc.gui.overlay() != null) { // resources are being reloaded
				needReload = true;
				return;
			}
			if (needReload) {
				needReload = false;
				reload(mc);
			}
			if (frames == null || dyn == null || mc.level == null) return;
			ticks++;
			int n = frames.length;
			int i = (int) ((ticks / SPEED) % n);
			double t = (ticks % SPEED) / (double) SPEED;
			NativeImage a = frames[i], b = frames[(i + 1) % n], out = dyn.getPixels();
			if (out == null) return;
			int w = a.getWidth(), h = a.getHeight();
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) out.setPixel(x, y, t == 0 ? a.getPixel(x, y) : blend(a.getPixel(x, y), b.getPixel(x, y), t));
			dyn.upload();
		} catch (Throwable e) {
			broken = true;
			PackMixerClient.LOGGER.warn("Crystal animation turned off: {}", e.toString());
		}
	}

	private static int blend(int p, int q, double t) {
		int r = 0;
		for (int s = 0; s < 32; s += 8) {
			int c1 = (p >>> s) & 0xFF, c2 = (q >>> s) & 0xFF;
			r |= ((int) (c1 + (c2 - c1) * t) & 0xFF) << s;
		}
		return r;
	}

	private static void reload(Minecraft mc) throws Exception {
		if (frames != null) for (NativeImage f : frames) f.close();
		frames = null;
		var res = mc.getResourceManager().getResource(STRIP);
		if (res.isEmpty()) { // no animated crystal chosen: back to the normal texture
			if (dyn != null) {
				mc.getTextureManager().release(TEX);
				dyn = null;
			}
			return;
		}
		NativeImage strip;
		try (InputStream in = res.get().open()) {
			strip = NativeImage.read(in);
		}
		int w = strip.getWidth(), fh = w / 2;
		int n = fh <= 0 ? 0 : strip.getHeight() / fh;
		if (n < 2) {
			strip.close();
			return;
		}
		NativeImage[] list = new NativeImage[n];
		for (int k = 0; k < n; k++) {
			NativeImage f = new NativeImage(w, fh, true);
			for (int y = 0; y < fh; y++) for (int x = 0; x < w; x++) f.setPixel(x, y, strip.getPixel(x, k * fh + y));
			list[k] = f;
		}
		strip.close();
		frames = list;
		NativeImage first = new NativeImage(w, fh, true);
		first.copyFrom(list[0]);
		dyn = new DynamicTexture(() -> "testohd animated end crystal", first);
		mc.getTextureManager().register(TEX, dyn);
	}

	private CrystalAnim() {
	}
}
