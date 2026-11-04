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
		CrystalPacks.selectionChanged(selection);
		CrystalAnim.markDirty();
		for (Category category : categories) {
			String packName = selection.get(category.id);
			// nothing chosen for explosions: they come with the animated crystal pack
			if (packName == null && category.id.equals("explosion") && CrystalPacks.isCrystalPack(selection.get("end_crystal")))
				packName = selection.get("end_crystal");
			if (packName == null || category.id.equals(DefaultCategories.ENCHANTED_APPLE)) continue; // OP gaps: see below
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

		copied += enchantedApple(assets, selection, library);
		copied += crystalSound(out, selection, library);

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

	/**
	 * Explosion sounds of the pack chosen for end crystals go to our own sound event, so only crystals
	 * sound like that pack (TNT keeps its sound). Also finds sounds in renamed folders like "random1".
	 */
	private static int crystalSound(Path out, Map<String, String> selection, PackLibrary library) throws IOException {
		CrystalSound.packHasSound = false;
		CrystalSound.markChecked();
		// sound: explosion pack, then crystal pack, then the last pack that had one
		SourcePack pack = null;
		List<String> found = new ArrayList<>();
		PackMixerConfig.Settings settings = PackMixerConfig.settings();
		for (String name : new String[]{selection.get("explosion"), selection.get("end_crystal"), settings.crystalSoundPack}) {
			SourcePack p = name == null || name.isEmpty() ? null : library.get(name);
			if (p == null) continue;
			found = explodeSounds(p);
			if (!found.isEmpty()) {
				pack = p;
				break;
			}
		}
		if (pack == null) return 0;
		if (!pack.name.equals(settings.crystalSoundPack)) {
			settings.crystalSoundPack = pack.name;
			PackMixerConfig.saveSettings();
		}
		found.sort(Comparator.naturalOrder());
		Path dir = out.resolve("assets").resolve(PackMixerClient.MOD_ID).resolve("sounds/crystal");
		Files.createDirectories(dir);
		StringBuilder list = new StringBuilder();
		for (int i = 0; i < found.size(); i++) {
			Files.write(dir.resolve("explode" + (i + 1) + ".ogg"), pack.read(found.get(i)));
			if (i > 0) list.append(", ");
			list.append("\"").append(PackMixerClient.MOD_ID).append(":crystal/explode").append(i + 1).append("\"");
		}
		Files.writeString(out.resolve("assets").resolve(PackMixerClient.MOD_ID).resolve("sounds.json"),
				"{\n  \"crystal_explode\": {\"sounds\": [" + list + "]}\n}\n", StandardCharsets.UTF_8);
		CrystalSound.packHasSound = true;
		return found.size();
	}

	private static List<String> explodeSounds(SourcePack pack) {
		List<String> found = new ArrayList<>();
		for (String p : pack.allPaths()) {
			String l = p.toLowerCase(java.util.Locale.ROOT);
			if (l.startsWith("sounds/") && l.contains("explode") && l.endsWith(".ogg")) found.add(p);
		}
		return found;
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
		SkyboxRenderer.markDirty();
		mc.options.updateResourcePacks(repo); // saves options.txt and reloads if the list changed
		if (before.equals(mc.options.resourcePacks)) {
			mc.reloadResourcePacks(); // list unchanged, but the files inside changed
		}
	}

	private static final String OP_TEXTURE = "textures/item/enchanted_golden_apple.png";
	private static final Pattern LAYER0 = Pattern.compile("\"layer0\"\\s*:\\s*\"(?:minecraft:)?([a-z0-9_./-]+)\"");

	/**
	 * In vanilla the OP gap uses the normal golden apple texture. So both can come from different packs, the OP gap
	 * gets its own texture ("item/enchanted_golden_apple") and model: from the pack chosen for OP gaps, or the
	 * vanilla apple when only normal gaps were changed.
	 */
	private static int enchantedApple(Path assets, Map<String, String> selection, PackLibrary library) throws IOException {
		String opName = selection.get(DefaultCategories.ENCHANTED_APPLE);
		SourcePack op = opName == null ? null : library.get(opName);
		if (op == null && selection.get("golden_apple") == null) return 0; // both vanilla: nothing to do
		Map<String, byte[]> files = new LinkedHashMap<>();
		byte[] png = null, meta = null;
		if (op != null) {
			// the pack has its own OP gap model with its own texture (not the normal apple): take it as it is
			if (op.has("models/item/enchanted_golden_apple.json")) {
				String model = new String(op.read("models/item/enchanted_golden_apple.json"), StandardCharsets.UTF_8);
				Matcher m = LAYER0.matcher(model);
				String tex = m.find() ? m.group(1) : null;
				if (tex != null && !tex.equals("item/golden_apple")) {
					List<String> own = new ArrayList<>(List.of("models/item/enchanted_golden_apple.json"));
					if (op.has("items/enchanted_golden_apple.json")) own.add("items/enchanted_golden_apple.json");
					Map<String, byte[]> deps = withDependencies(op, own);
					if (!deps.containsKey("textures/item/golden_apple.png")) {
						files.putAll(deps);
						return write(assets, files);
					}
				}
			}
			String src = op.has(OP_TEXTURE) ? OP_TEXTURE : op.has("textures/item/golden_apple.png") ? "textures/item/golden_apple.png" : null;
			if (src != null) {
				png = op.read(src);
				if (op.has(src + ".mcmeta")) meta = op.read(src + ".mcmeta");
			}
		}
		if (png == null) {
			try {
				png = PreviewCache.readVanilla("textures/item/golden_apple.png");
			} catch (Exception e) {
				PackMixerClient.LOGGER.warn("No vanilla golden apple texture", e);
			}
			if (png == null) return 0;
		}
		files.put(OP_TEXTURE, png);
		if (meta != null) files.put(OP_TEXTURE + ".mcmeta", meta);
		files.put("models/item/enchanted_golden_apple.json", """
				{"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/enchanted_golden_apple"}}
				""".getBytes(StandardCharsets.UTF_8));
		files.put("items/enchanted_golden_apple.json", """
				{"model": {"type": "minecraft:model", "model": "minecraft:item/enchanted_golden_apple"}}
				""".getBytes(StandardCharsets.UTF_8));
		return write(assets, files);
	}

	private static int write(Path assets, Map<String, byte[]> files) throws IOException {
		int n = 0;
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			Path target = assets.resolve(e.getKey()).normalize();
			if (!target.startsWith(assets)) continue;
			Files.createDirectories(target.getParent());
			Files.write(target, e.getValue());
			n++;
		}
		return n;
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
				if (e.getKey().endsWith(".properties")) { // OptiFine sky: source=./sky1.png etc.
					String key = e.getKey();
					String folder = key.substring(0, key.lastIndexOf('/') + 1);
					for (String line : new String(e.getValue(), StandardCharsets.UTF_8).split("\\R")) {
						line = line.trim();
						if (!line.startsWith("source")) continue;
						int eq = line.indexOf('=');
						if (eq < 0) continue;
						String src = line.substring(eq + 1).trim();
						String ref = src.startsWith("./") ? folder + src.substring(2)
								: src.startsWith("~/") ? "optifine/" + src.substring(2)
								: src.startsWith("assets/minecraft/") ? src.substring(17)
								: src.contains(":") ? src.substring(src.indexOf(':') + 1) : src;
						if (pack.has(ref) && seen.add(ref)) todo.add(ref);
					}
					continue;
				}
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
