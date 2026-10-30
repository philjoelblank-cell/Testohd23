package de.philjoel.packmixer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A texture pack the user dropped in (zip or folder). Builds an index
 * "normalized path (relative to assets/minecraft/)" -> "real entry inside the pack".
 * Old 1.8-style names (textures/items/, apple_golden, ...) are translated to 1.21 names.
 */
public final class SourcePack {
	private static final Map<String, String> LEGACY_NAMES = new HashMap<>();

	static {
		String[][] pairs = {
				{"apple_golden", "golden_apple"},
				{"wood_sword", "wooden_sword"}, {"gold_sword", "golden_sword"},
				{"wood_pickaxe", "wooden_pickaxe"}, {"gold_pickaxe", "golden_pickaxe"},
				{"wood_axe", "wooden_axe"}, {"gold_axe", "golden_axe"},
				{"wood_shovel", "wooden_shovel"}, {"gold_shovel", "golden_shovel"},
				{"wood_hoe", "wooden_hoe"}, {"gold_hoe", "golden_hoe"},
				{"bow_standby", "bow"},
				{"potion_bottle_drinkable", "potion"}, {"potion_bottle_splash", "splash_potion"},
				{"potion_bottle_empty", "glass_bottle"},
				{"gold_helmet", "golden_helmet"}, {"gold_chestplate", "golden_chestplate"},
				{"gold_leggings", "golden_leggings"}, {"gold_boots", "golden_boots"},
				{"fishing_rod_uncast", "fishing_rod"},
				{"web", "cobweb"},
				{"fire_layer_0", "fire_0"}, {"fire_layer_1", "fire_1"},
				{"grass_top", "grass_block_top"}, {"grass_side", "grass_block_side"},
				{"grass_side_overlay", "grass_block_side_overlay"}, {"grass_side_snowed", "grass_block_snow"},
				{"snowball", "snowball"},
		};
		for (String[] p : pairs) LEGACY_NAMES.put(p[0], p[1]);
	}

	public final String name;
	public final Path path;
	private final boolean zip;
	/** normalized path -> original entry/relative path */
	private final Map<String, String> index = new LinkedHashMap<>();

	private SourcePack(String name, Path path, boolean zip) {
		this.name = name;
		this.path = path;
		this.zip = zip;
	}

	/** Returns null if the path is not a usable pack. */
	public static SourcePack open(Path path) {
		String fileName = path.getFileName().toString();
		try {
			if (Files.isDirectory(path)) {
				SourcePack pack = new SourcePack(fileName, path, false);
				try (Stream<Path> files = Files.walk(path)) {
					files.filter(Files::isRegularFile).forEach(f ->
							pack.add(path.relativize(f).toString().replace('\\', '/')));
				}
				return pack.index.isEmpty() ? null : pack;
			}
			if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
				SourcePack pack = new SourcePack(fileName.substring(0, fileName.length() - 4), path, true);
				try (ZipFile zf = new ZipFile(path.toFile())) {
					zf.stream().filter(e -> !e.isDirectory()).forEach(e -> pack.add(e.getName()));
				}
				return pack.index.isEmpty() ? null : pack;
			}
		} catch (Exception e) {
			PackMixerClient.LOGGER.warn("Could not read pack {}: {}", path, e.toString());
		}
		return null;
	}

	private void add(String entry) {
		String normalized = normalize(entry);
		if (normalized != null) {
			// an exact 1.21 path wins over a translated legacy path
			if (!index.containsKey(normalized) || normalized.equals(stripPrefix(entry))) {
				index.put(normalized, entry);
			}
		}
	}

	private static String stripPrefix(String entry) {
		String e = entry.replace('\\', '/');
		int i = e.indexOf("assets/minecraft/");
		return i < 0 ? null : e.substring(i + "assets/minecraft/".length());
	}

	/** "Pack/assets/minecraft/textures/items/apple_golden.png" -> "textures/item/golden_apple.png" */
	static String normalize(String entry) {
		String p = stripPrefix(entry);
		if (p == null || p.isEmpty()) return null;
		if (p.startsWith("textures/items/")) p = "textures/item/" + p.substring("textures/items/".length());
		else if (p.startsWith("textures/blocks/")) p = "textures/block/" + p.substring("textures/blocks/".length());
		else if (p.startsWith("textures/misc/enchanted_item_glint.png")) // 1.8 – 1.20 name
			p = "textures/misc/enchanted_glint_item.png" + p.substring("textures/misc/enchanted_item_glint.png".length());

		if (p.startsWith("textures/item/") || p.startsWith("textures/block/")) {
			int slash = p.lastIndexOf('/');
			String dir = p.substring(0, slash + 1);
			String file = p.substring(slash + 1);
			int dot = file.indexOf('.');
			String base = dot < 0 ? file : file.substring(0, dot);
			String ext = dot < 0 ? "" : file.substring(dot);
			String renamed = LEGACY_NAMES.get(base);
			if (renamed != null) p = dir + renamed + ext;
		}
		return p;
	}

	public boolean has(String normalizedPath) {
		return index.containsKey(normalizedPath);
	}

	public List<String> matching(Category category) {
		List<String> out = new ArrayList<>();
		for (String p : index.keySet()) {
			if (category.matches(p)) out.add(p);
		}
		return out;
	}

	public boolean contains(Category category) {
		for (String p : index.keySet()) {
			if (category.matches(p)) return true;
		}
		return false;
	}

	/** Reads a file by its normalized path. */
	public byte[] read(String normalizedPath) throws IOException {
		String entry = index.get(normalizedPath);
		if (entry == null) throw new IOException("Not in pack: " + normalizedPath);
		if (zip) {
			try (ZipFile zf = new ZipFile(path.toFile())) {
				ZipEntry ze = zf.getEntry(entry);
				if (ze == null) throw new IOException("Missing zip entry " + entry);
				try (InputStream in = zf.getInputStream(ze)) {
					return in.readAllBytes();
				}
			}
		}
		return Files.readAllBytes(path.resolve(entry));
	}

	/** Reads many files at once (opens a zip only once). */
	public Map<String, byte[]> readAll(List<String> normalizedPaths) throws IOException {
		Map<String, byte[]> out = new LinkedHashMap<>();
		if (zip) {
			try (ZipFile zf = new ZipFile(path.toFile())) {
				for (String p : normalizedPaths) {
					ZipEntry ze = zf.getEntry(index.get(p));
					if (ze == null) continue;
					try (InputStream in = zf.getInputStream(ze)) {
						out.put(p, in.readAllBytes());
					}
				}
			}
		} else {
			for (String p : normalizedPaths) {
				out.put(p, Files.readAllBytes(path.resolve(index.get(p))));
			}
		}
		return out;
	}
}
