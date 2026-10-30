package de.philjoel.packmixer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 3 profile slots that store which pack is used for which category. */
public final class Profiles {
	public static final int COUNT = 3;
	private static final Gson GSON = new Gson();

	private static Path file(int slot) {
		return PackMixerConfig.rootDir().resolve("profiles").resolve("profile" + slot + ".json");
	}

	public static boolean exists(int slot) {
		return Files.exists(file(slot));
	}

	public static Map<String, String> read(int slot) {
		try (Reader r = Files.newBufferedReader(file(slot), StandardCharsets.UTF_8)) {
			Map<String, String> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
			return m == null ? new LinkedHashMap<>() : m;
		} catch (Exception e) {
			return new LinkedHashMap<>();
		}
	}

	public static void save(int slot) {
		try {
			Files.createDirectories(file(slot).getParent());
			try (Writer w = Files.newBufferedWriter(file(slot), StandardCharsets.UTF_8)) {
				GSON.toJson(PackMixerConfig.loadSelection(), w);
			}
		} catch (Exception e) {
			PackMixerClient.LOGGER.error("Could not save profile {}", slot, e);
		}
	}

	/** Loads the profile, rebuilds the output pack and reloads textures. */
	public static void load(int slot) {
		Map<String, String> sel = read(slot);
		PackMixerConfig.saveSelection(sel);
		try {
			PackBuilder.build(PackMixerConfig.loadCategories(), sel, PackLibrary.scan());
			PackBuilder.applyInGame(!sel.isEmpty());
		} catch (Exception e) {
			PackMixerClient.LOGGER.error("Could not load profile {}", slot, e);
		}
	}

	private Profiles() {
	}
}
