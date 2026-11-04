package de.philjoel.packmixer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All files PackMixer keeps in {@code .minecraft/packmixer/}. */
public final class PackMixerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final String OUTPUT_PACK_NAME = "Testohd Styles Output";
	public static final String OUTPUT_PACK_ID = "file/" + OUTPUT_PACK_NAME;

	public static Path rootDir() {
		return FabricLoader.getInstance().getGameDir().resolve("testohd-styles");
	}

	/** Folder where the user drops texture packs (zip or folder). */
	public static Path packsDir() {
		return rootDir().resolve("packs");
	}

	public static Path categoriesFile() {
		return rootDir().resolve("categories.json");
	}

	public static Path selectionFile() {
		return rootDir().resolve("selection.json");
	}

	public static void ensureDirs() {
		try {
			Files.createDirectories(packsDir());
			Path readme = packsDir().resolve("README_TEXTURE_PACKS.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, """
						EN: Put your texture packs here (.zip or unzipped folder).
						    Then press K in game, pick a category and click the pack you want for it.
						    Packs from the normal 'resourcepacks' folder are found as well.
						    You can add your own categories in ../categories.json.

						DE: Lege hier deine Texture Packs rein (.zip oder entpackter Ordner).
						    Danach im Spiel K druecken, Kategorie waehlen und auf das Pack klicken.
						    Packs aus dem normalen 'resourcepacks'-Ordner werden ebenfalls erkannt.
						    Eigene Kategorien kannst du in ../categories.json hinzufuegen.
						""", StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			PackMixerClient.LOGGER.error("Could not create PackMixer folders", e);
		}
	}

	// ---------------------------------------------------------------- categories

	public static List<Category> loadCategories() {
		ensureDirs();
		Path file = categoriesFile();
		if (Files.exists(file)) {
			List<Category> result = null;
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				List<Category> list = GSON.fromJson(r, new TypeToken<List<Category>>() {}.getType());
				if (list != null && !list.isEmpty()) {
					list.removeIf(c -> c == null || c.id == null || c.files == null || c.files.isEmpty());
					for (Category c : list) {
						if (c.name == null) c.name = c.id;
						if (c.icons == null) c.icons = new ArrayList<>();
					}
					// built-in categories always come from the mod (so fixes arrive), your own ones are kept
					List<Category> defaults = DefaultCategories.create();
					java.util.Set<String> ids = new java.util.HashSet<>();
					for (Category d : defaults) ids.add(d.id);
					List<Category> merged = new ArrayList<>(defaults);
					for (Category c : list) if (!ids.contains(c.id)) merged.add(c);
					result = merged;
				}
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("categories.json is broken, using defaults", e);
				return DefaultCategories.create();
			}
			if (result != null) {
				write(file, result); // after the file was closed (Windows can't replace an open file)
				return result;
			}
		}
		List<Category> defaults = DefaultCategories.create();
		write(file, defaults);
		return defaults;
	}

	// ---------------------------------------------------------------- selection

	/** category id -> pack name. Missing entry = vanilla / untouched. */
	public static Map<String, String> loadSelection() {
		Path file = restoreIfNeeded(selectionFile());
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, String> map = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
				if (map != null) {
					// 1.8.4: OP gaps got their own category – keep what you had (same pack as the normal gaps)
					Settings st = settings();
					if (!st.opGapSplit) {
						st.opGapSplit = true;
						saveSettings();
						if (map.containsKey("golden_apple") && !map.containsKey(DefaultCategories.ENCHANTED_APPLE)) {
							map.put(DefaultCategories.ENCHANTED_APPLE, map.get("golden_apple"));
							r.close();
							saveSelection(map);
						}
					}
					return map;
				}
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("selection.json is broken, starting empty", e);
			}
		}
		return new LinkedHashMap<>();
	}

	public static void saveSelection(Map<String, String> selection) {
		write(selectionFile(), selection);
	}

	// ---------------------------------------------------------------- settings

	public static final class Settings {
		/** Also list packs from the normal resourcepacks folder. */
		public boolean includeResourcePacksFolder = true;
		/** Show a message in chat after the textures were applied. */
		public boolean chatMessageOnApply = true;

		// Item position (first person)
		public boolean itemPositionEnabled = false;
		public double itemX = 0, itemY = 0, itemZ = 0; // -1 .. 1
		public double itemScale = 1.0;                 // 0.3 .. 1.5

		// Zoom
		public boolean zoomEnabled = true;
		public double zoomFactor = 4.0;                // 1.5 .. 10

		// Fullbright
		public boolean fullbright = false;

		// Weather
		public boolean clearWeather = false;

		// Low fire
		public boolean lowFire = false;
		public double fireOffset = 0.3;                // 0 .. 0.5

		// Cosmetics
		public String hat = "";                        // "" = none
		public String face = "";
		public String back = "";
		public boolean cosmeticsEnabled = true;
		public String effect = "";                     // particle aura
		public boolean effectsFirstPerson = false;     // also show auras in first person

		// TT (Totem-Tracker) + ancient debris highlight
		public boolean totemTracker = true;
		public boolean ancientDebris = false;

		// Favourite packs (by name), toggled with right-click
		public java.util.List<String> favorites = new java.util.ArrayList<>();

		// Built-in packs
		public int builtinPacksVersion = 0;

		// Design
		public boolean menuSnow = false;

		// Cosmetics of other players
		public boolean shareCosmetics = true;      // let other mod users see yours
		public boolean showOthersCosmetics = true; // see theirs

		// Enchantment glint only on tools / weapons / armor
		public boolean glintOnlyGear = true;

		// End crystal explosions without sound
		public boolean crystalSilent = false;
		/** Last pack that brought crystal explosion sounds (kept when an animated crystal pack is chosen). */
		public String crystalSoundPack = "";

		// Updates from GitHub
		public boolean autoUpdate = true;
		/** Version whose changelog was shown last. */
		public String lastChangelog = "";
		/** Old selections were moved to separate OP gaps already. */
		public boolean opGapSplit = false;
		/** Picture behind the menus (see Ui.BACKGROUNDS). */
		public String menuBackground = "purple";
		/** Size of all particles, 0.2 .. 1 (small particles for PvP). */
		public double particleSize = 1.0;
	}

	private static Settings settings;

	public static Settings settings() {
		if (settings == null) {
			Path file = restoreIfNeeded(rootDir().resolve("settings.json"));
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					settings = GSON.fromJson(r, Settings.class);
				} catch (Exception e) {
					PackMixerClient.LOGGER.error("settings.json is broken, using defaults", e);
				}
			}
			if (settings == null) settings = new Settings();
		}
		return settings;
	}

	public static void saveSettings() {
		write(rootDir().resolve("settings.json"), settings());
	}

	private static void write(Path file, Object value) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(value, w);
			}
		} catch (IOException e) {
			PackMixerClient.LOGGER.error("Could not write {}", file, e);
		}
	}

	/** If a file is missing or empty/broken but a backup exists, the backup is put back. */
	private static Path restoreIfNeeded(Path file) {
		try {
			Path backup = rootDir().resolve("backup").resolve(file.getFileName().toString());
			boolean bad = !Files.exists(file) || Files.size(file) < 2;
			if (!bad) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					bad = GSON.fromJson(r, Object.class) == null;
				} catch (Exception e) {
					bad = true;
				}
			}
			if (bad && Files.exists(backup) && Files.size(backup) > 1) {
				Files.createDirectories(file.getParent());
				Files.copy(backup, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				PackMixerClient.LOGGER.info("Restored {} from backup", file.getFileName());
			}
		} catch (IOException e) {
			PackMixerClient.LOGGER.warn("Backup check failed for {}", file, e);
		}
		return file;
	}

	private PackMixerConfig() {
	}
}
