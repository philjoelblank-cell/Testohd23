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
	private int counter;

	/** Flat icon of a category. @param pack null = vanilla */
	public Preview icon(Category category, SourcePack pack) {
		String key = name(pack) + "|icon|" + category.id;
		Preview cached = cache.get(key);
		if (cached != null) return cached == MISSING ? null : cached;
		Preview loaded = null;
		try {
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

	private static int badness(String p) {
		int b = 0;
		if (p.contains("_left") || p.contains("_right")) b += 2;
		if (p.contains("/shield/") && !p.endsWith("base.png") && !p.contains("nopattern")) b += 3;
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
