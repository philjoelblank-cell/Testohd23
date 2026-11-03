package de.philjoel.packmixer;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Finds all packs in packmixer/packs and in the normal resourcepacks folder. */
public final class PackLibrary {
	private final Map<String, SourcePack> packs = new LinkedHashMap<>();

	public static PackLibrary scan() {
		PackMixerConfig.ensureDirs();
		PackLibrary lib = new PackLibrary();
		lib.scanDir(PackMixerConfig.packsDir());
		if (PackMixerConfig.settings().includeResourcePacksFolder) {
			lib.scanDir(FabricLoader.getInstance().getGameDir().resolve("resourcepacks"));
		}
		return lib;
	}

	private void scanDir(Path dir) {
		if (!Files.isDirectory(dir)) return;
		List<Path> entries = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			s.forEach(entries::add);
		} catch (Exception e) {
			PackMixerClient.LOGGER.warn("Could not list {}", dir, e);
			return;
		}
		entries.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
		for (Path p : entries) {
			if (p.getFileName().toString().equals(PackMixerConfig.OUTPUT_PACK_NAME)) continue;
			SourcePack pack = SourcePack.open(p);
			if (pack != null && !packs.containsKey(pack.name)) {
				packs.put(pack.name, pack);
			}
		}
	}

	public SourcePack get(String name) {
		return packs.get(name);
	}

	public int size() {
		return packs.size();
	}

	/** All packs that contain at least one file of the category. */
	public List<SourcePack> packsFor(Category category) {
		List<SourcePack> list = new ArrayList<>();
		boolean full = category.id.equals(DefaultCategories.FULL_PACK);
		for (SourcePack p : packs.values()) {
			if (full ? V1Packs.isV1(p.name) : p.contains(category)) list.add(p);
		}
		return list;
	}
}
