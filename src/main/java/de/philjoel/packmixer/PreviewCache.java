package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads preview textures from packs (or vanilla) into GPU textures. Render thread only. */
public final class PreviewCache {
	public record Preview(Identifier id, int width, int height, String path) {
	}

	private static final Preview MISSING = new Preview(null, 0, 0, null);

	private final Map<String, Preview> cache = new HashMap<>();
	private final List<Identifier> registered = new ArrayList<>();
	private static int counter; // shared by all caches, so two caches never use the same texture id

	/** Flat icon of a category. @param pack null = vanilla */
	public Preview icon(Category category, SourcePack pack) {
		String key = name(pack) + "|icon|" + category.id;
		Preview cached = cache.get(key);
		if (cached != null) return cached == MISSING ? null : cached;
		Preview loaded = null;
		try {
			if (category.id.equals("enchant_glint")) {
				loaded = glint(pack);
				cache.put(key, loaded == null ? MISSING : loaded);
				return loaded;
			}
			if (category.id.equals("potions")) {
				loaded = potion(pack);
				if (loaded != null) {
					cache.put(key, loaded);
					return loaded;
				}
			}
			byte[] png = null;
			String path = null;
			if (pack != null) {
				for (String icon : category.icons) {
					if (pack.has(icon)) { png = pack.read(icon); path = icon; break; }
				}
				if (png == null) {
					// prefer single textures over double-chest halves, pattern overlays etc.
					List<String> pngs = new ArrayList<>(pack.matching(category).stream().filter(p -> p.endsWith(".png")).toList());
					pngs.sort(java.util.Comparator.comparingInt(PreviewCache::badness));
					for (String p : pngs) {
						png = pack.read(p);
						if (png != null) { path = p; break; }
					}
				}
			}
			if (png == null) {
				// pack only changes models (e.g. "Low Shield") or has nothing usable -> show the normal texture
				for (String icon : category.icons) {
					png = readVanilla(icon);
					if (png != null) { path = icon; break; }
				}
			}
			if (png != null) loaded = upload(png, path);
		} catch (Exception e) {
			PackMixerClient.LOGGER.debug("No preview for {}: {}", key, e.toString());
		}
		cache.put(key, loaded == null ? MISSING : loaded);
		return loaded;
	}

	/** A single texture; falls back to vanilla if the pack doesn't have it. */
	public Preview texture(SourcePack pack, String path) {
		String key = name(pack) + "|tex|" + path;
		Preview cached = cache.get(key);
		if (cached != null) return cached == MISSING ? null : cached;
		Preview loaded = null;
		try {
			byte[] png = pack != null && pack.has(path) ? pack.read(path) : null;
			if (png == null) {
				if (pack != null) return texture(null, path);
				png = readVanilla(path);
			}
			if (png != null) loaded = upload(png, path);
		} catch (Exception e) {
			PackMixerClient.LOGGER.debug("No texture {}: {}", key, e.toString());
		}
		cache.put(key, loaded == null ? MISSING : loaded);
		return loaded;
	}

	/** Loads a texture shipped inside this mod (assets/packmixer/...). */
	public Preview modTexture(String path) {
		String key = "<mod>|" + path;
		Preview cached = cache.get(key);
		if (cached != null) return cached == MISSING ? null : cached;
		Preview loaded = null;
		try (InputStream in = PreviewCache.class.getResourceAsStream("/assets/testohd_styles/" + path)) {
			if (in != null) loaded = upload(in.readAllBytes(), path);
		} catch (Exception e) {
			PackMixerClient.LOGGER.debug("No mod texture {}: {}", path, e.toString());
		}
		cache.put(key, loaded == null ? MISSING : loaded);
		return loaded;
	}

	private static NativeImage readImage(SourcePack pack, String path) throws Exception {
		byte[] png = pack != null && pack.has(path) ? pack.read(path) : null;
		if (png == null) png = readVanilla(path);
		return png == null ? null : NativeImage.read(new ByteArrayInputStream(png));
	}

	/** Frames of the turning 3D end crystal preview. */
	public static final int CRYSTAL_FRAMES = 48;

	/**
	 * End crystal like when it is placed in the world: the two glass cubes and the core float up and down and turn
	 * once around themselves (software 3D, one strip with {@link #CRYSTAL_FRAMES} frames).
	 */
	public Preview crystal3d(SourcePack pack) {
		String key = name(pack) + "|crystal3d";
		Preview cached = cache.get(key);
		if (cached != null) return cached == MISSING ? null : cached;
		Preview loaded = null;
		try {
			NativeImage tex = readImage(pack, "textures/entity/end_crystal/end_crystal.png");
			NativeImage anim = pack != null && pack.has("textures/entity/end_crystal/" + CrystalPacks.STRIP_NAME)
					? readImage(pack, "textures/entity/end_crystal/" + CrystalPacks.STRIP_NAME) : null;
			if (tex != null) {
				NativeImage strip = Crystal3D.render(tex, anim, CRYSTAL_FRAMES, 96);
				tex.close();
				if (anim != null) anim.close();
				loaded = register(strip, "crystal3d");
			}
		} catch (Exception e) {
			PackMixerClient.LOGGER.debug("No crystal preview for {}: {}", key, e.toString());
		}
		cache.put(key, loaded == null ? MISSING : loaded);
		return loaded;
	}

	/** Enchant glint preview: a diamond sword with the pack's glint running over it (16 frame strip). */
	private Preview glint(SourcePack pack) throws Exception {
		NativeImage glint = readImage(pack, "textures/misc/enchanted_glint_item.png");
		NativeImage sword = readImage(null, "textures/item/diamond_sword.png");
		if (glint == null || sword == null) {
			if (glint != null) glint.close();
			if (sword != null) sword.close();
			return null;
		}
		int w = sword.getWidth(), frames = 16;
		NativeImage strip = new NativeImage(w, w * frames, true);
		int gw = glint.getWidth(), gh = glint.getHeight();
		for (int f = 0; f < frames; f++)
			for (int y = 0; y < w; y++)
				for (int x = 0; x < w; x++) {
					int p = sword.getPixel(x, y);
					if ((p >>> 24) < 10) {
						strip.setPixel(x, f * w + y, 0);
						continue;
					}
					// the glint scrolls diagonally, like on a real item
					int gx = Math.floorMod((x * gw / w) / 2 + f * gw / frames, gw);
					int gy = Math.floorMod((y * gh / w) / 2 + f * gh / (frames * 2), gh);
					int q = glint.getPixel(gx, gy);
					int r = Math.min(255, ((p >> 16) & 0xFF) + (int) (((q >> 16) & 0xFF) * 0.9));
					int g = Math.min(255, ((p >> 8) & 0xFF) + (int) (((q >> 8) & 0xFF) * 0.9));
					int b = Math.min(255, (p & 0xFF) + (int) ((q & 0xFF) * 0.9));
					strip.setPixel(x, f * w + y, (p & 0xFF000000) | (r << 16) | (g << 8) | b);
				}
		glint.close();
		sword.close();
		return register(strip, "glint");
	}

	/** Potion preview: bottle + coloured liquid (in game the liquid is tinted, the file itself is grey). */
	private Preview potion(SourcePack pack) throws Exception {
		NativeImage bottle = readImage(pack, "textures/item/splash_potion.png");
		if (bottle == null) bottle = readImage(pack, "textures/item/potion.png");
		NativeImage overlay = readImage(pack, "textures/item/potion_overlay.png");
		if (bottle == null || overlay == null) {
			if (bottle != null) bottle.close();
			if (overlay != null) overlay.close();
			return null;
		}
		int w = bottle.getWidth(), h = bottle.getHeight();
		NativeImage out = new NativeImage(w, h, true);
		int tint = 0xF82423; // healing red
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int ox = x * overlay.getWidth() / w, oy = y * overlay.getHeight() / h;
				int o = overlay.getPixel(ox, oy);
				int c = bottle.getPixel(x, y);
				if ((o >>> 24) > 10) {
					int r = ((o >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255, g = ((o >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255,
							b = (o & 0xFF) * (tint & 0xFF) / 255;
					int under = (o & 0xFF000000) | (r << 16) | (g << 8) | b;
					c = (c >>> 24) > 10 ? over(under, c) : under;
				}
				out.setPixel(x, y, c);
			}
		bottle.close();
		overlay.close();
		return register(out, "potion");
	}

	private static int over(int dst, int src) {
		int sa = src >>> 24;
		if (sa >= 250) return src;
		double a = sa / 255.0;
		int r = (int) (((src >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
		int g = (int) (((src >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
		int b = (int) ((src & 0xFF) * a + (dst & 0xFF) * (1 - a));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private Preview register(NativeImage image, String path) {
		Identifier id = Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "preview/" + (counter++));
		DynamicTexture texture = new DynamicTexture(() -> "packmixer preview " + id, image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		registered.add(id);
		return new Preview(id, image.getWidth(), image.getHeight(), path);
	}

	private static int badness(String p) {
		int b = 0;
		if (p.contains("_left") || p.contains("_right")) b += 2;
		if (p.contains("shield") && !p.contains("nopattern")) b += p.contains("/shield/") && !p.endsWith("base.png") ? 4 : 2;
		if (p.contains("pulling") || p.contains("_overlay") || p.contains("_open")) b += 1;
		return b;
	}

	private static String name(SourcePack pack) {
		return pack == null ? "<vanilla>" : pack.name;
	}

	private static byte[] readVanilla(String path) throws Exception {
		var vanilla = Minecraft.getInstance().getVanillaPackResources();
		IoSupplier<InputStream> res = vanilla.getResource(PackType.CLIENT_RESOURCES,
				Identifier.fromNamespaceAndPath("minecraft", path));
		if (res == null) return null;
		try (InputStream in = res.get()) {
			return in.readAllBytes();
		}
	}

	private Preview upload(byte[] png, String path) throws Exception {
		NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
		int w = image.getWidth();
		int h = image.getHeight();
		Identifier id = Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "preview/" + (counter++));
		DynamicTexture texture = new DynamicTexture(() -> "packmixer preview " + id, image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		registered.add(id);
		return new Preview(id, w, h, path);
	}

	public void clear() {
		var tm = Minecraft.getInstance().getTextureManager();
		for (Identifier id : registered) tm.release(id);
		registered.clear();
		cache.clear();
	}
}
