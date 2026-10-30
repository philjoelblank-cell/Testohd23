package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes the chosen textures into resourcepacks/PackMixer Output/ and puts that pack
 * on top of the resource pack list, then reloads resources.
 */
public final class PackBuilder {
	/** Resource pack format of Minecraft 1.21.11. */
	private static final int PACK_FORMAT = 75;

	public static int build(List<Category> categories, Map<String, String> selection, PackLibrary library) throws IOException {
		Path out = Minecraft.getInstance().getResourcePackDirectory().resolve(PackMixerConfig.OUTPUT_PACK_NAME);
		deleteRecursively(out);
		Path assets = out.resolve("assets").resolve("minecraft");
		Files.createDirectories(assets);

		int copied = 0;
		// Later categories overwrite earlier ones if patterns overlap.
		for (Category category : categories) {
			String packName = selection.get(category.id);
			if (packName == null) continue;
			SourcePack pack = library.get(packName);
			if (pack == null) {
				PackMixerClient.LOGGER.warn("Pack '{}' for category '{}' not found anymore", packName, category.id);
				continue;
			}
			List<String> files = pack.matching(category);
			for (Map.Entry<String, byte[]> e : withDependencies(pack, files).entrySet()) {
				Path target = assets.resolve(e.getKey()).normalize();
				if (!target.startsWith(assets)) continue; // never write outside the pack
				Files.createDirectories(target.getParent());
				Files.write(target, e.getValue());
				copied++;
			}
		}

		if (PackMixerConfig.settings().ancientDebris) {
			copied += highlightAncientDebris(assets.resolve("textures/block"));
		}

		Files.writeString(out.resolve("pack.mcmeta"), """
				{
				  "pack": {
				    "description": {"translate": "packmixer.pack.desc.output", "fallback": "Testohd Styles"},
				    "min_format": %d,
				    "max_format": %d
				  }
				}
				""".formatted(PACK_FORMAT, PACK_FORMAT), StandardCharsets.UTF_8);
		return copied;
	}

	/** Enables the output pack on top of all other packs and reloads the textures. */
	/** Ancient debris with a bright orange frame so it is easy to spot (texture only, no x-ray). */
	private static int highlightAncientDebris(Path blockDir) throws IOException {
		int n = 0;
		for (String name : new String[]{"ancient_debris_side", "ancient_debris_top"}) {
			Path target = blockDir.resolve(name + ".png");
			com.mojang.blaze3d.platform.NativeImage img = null;
			if (Files.exists(target)) { // a selected pack already provides it -> highlight that one
				try (java.io.InputStream in = Files.newInputStream(target)) {
					img = com.mojang.blaze3d.platform.NativeImage.read(in);
				}
			}
			if (img == null) img = BuiltinPacks.vanilla("block/" + name + ".png");
			if (img == null) continue;
			int w = img.getWidth(), h = img.getHeight();
			int t = Math.max(1, w / 16);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					boolean edge = x < t || y < t || x >= w - t || y >= h - t;
					if (edge) {
						img.setPixel(x, y, 0xFFFF9A1F);
					} else {
						int p = img.getPixel(x, y);
						int r = Math.min(255, (int) (((p >> 16) & 0xFF) * 1.35) + 20);
						int g = Math.min(255, (int) (((p >> 8) & 0xFF) * 1.15) + 8);
						int b = (int) ((p & 0xFF) * 0.9);
						img.setPixel(x, y, (p & 0xFF000000) | (r << 16) | (g << 8) | b);
					}
				}
			}
			Files.createDirectories(blockDir);
			img.writeToFile(target);
			img.close();
			n++;
		}
		return n;
	}

	/** Rebuilds the output pack from the saved selection and reloads (used by settings toggles). */
	public static void rebuildAndApply() {
		java.util.Map<String, String> sel = PackMixerConfig.loadSelection();
		try {
			build(PackMixerConfig.loadCategories(), sel, PackLibrary.scan());
		} catch (Exception e) {
			PackMixerClient.LOGGER.error("Rebuilding the pack failed", e);
		}
		applyInGame(!sel.isEmpty() || PackMixerConfig.settings().ancientDebris);
	}

	public static void applyInGame(boolean enabled) {
		enabled = enabled || PackMixerConfig.settings().ancientDebris;
		Minecraft mc = Minecraft.getInstance();
		PackRepository repo = mc.getResourcePackRepository();
		repo.reload();

		List<String> before = new ArrayList<>(mc.options.resourcePacks);
		String id = PackMixerConfig.OUTPUT_PACK_ID;
		repo.removePack(id);
		if (enabled && repo.isAvailable(id)) {
			repo.addPack(id); // added last = highest priority
		}
		mc.options.updateResourcePacks(repo); // saves options.txt and reloads if the list changed
		if (before.equals(mc.options.resourcePacks)) {
			mc.reloadResourcePacks(); // list unchanged, but the files inside changed
		}
	}

	private static final Pattern REFERENCE = Pattern.compile("\"(?:minecraft:)?((?:item|block|entity|custom|models?|textures?)/[a-z0-9_./-]+)\"");

	/**
	 * Reads the files and follows references inside model JSONs (e.g. a 3D sword model that
	 * uses extra textures or a parent model), so custom models arrive complete.
	 */
	static Map<String, byte[]> withDependencies(SourcePack pack, List<String> files) throws IOException {
		Map<String, byte[]> result = new LinkedHashMap<>();
		Deque<String> todo = new ArrayDeque<>(files);
		Set<String> seen = new HashSet<>(files);
		while (!todo.isEmpty()) {
			List<String> batch = new ArrayList<>(todo);
			todo.clear();
			for (Map.Entry<String, byte[]> e : pack.readAll(batch).entrySet()) {
				result.put(e.getKey(), e.getValue());
				if (!e.getKey().endsWith(".json")) continue;
				Matcher m = REFERENCE.matcher(new String(e.getValue(), StandardCharsets.UTF_8));
				while (m.find()) {
					String ref = m.group(1);
					for (String candidate : List.of("models/" + ref + ".json", "textures/" + ref + ".png",
							"textures/" + ref + ".png.mcmeta", ref, ref + ".png", ref + ".json")) {
						if (pack.has(candidate) && seen.add(candidate)) todo.add(candidate);
					}
				}
			}
		}
		return result;
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
			for (Path p : all) Files.deleteIfExists(p);
		}
	}

	private PackBuilder() {
	}
}
