package de.philjoel.packmixer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** "Meine Packs": named mixes (which pack for which category) that can be shared with a short code. */
public final class Mixes {
	public static final class Mix {
		public String name;
		public Map<String, String> selection = new LinkedHashMap<>();

		public Mix() {
		}

		public Mix(String name, Map<String, String> selection) {
			this.name = name;
			this.selection = new LinkedHashMap<>(selection);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String PREFIX = "TS1-";
	private static List<Mix> mixes;

	private static Path file() {
		return PackMixerConfig.rootDir().resolve("mixes.json");
	}

	public static List<Mix> all() {
		if (mixes == null) {
			mixes = new ArrayList<>();
			if (Files.exists(file())) {
				try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
					List<Mix> l = GSON.fromJson(r, new TypeToken<List<Mix>>() {}.getType());
					if (l != null) for (Mix m : l) if (m != null && m.name != null && m.selection != null) mixes.add(m);
				} catch (Exception e) {
					PackMixerClient.LOGGER.error("mixes.json is broken", e);
				}
			}
		}
		return mixes;
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			Path tmp = file().resolveSibling("mixes.json.tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(all(), w);
			}
			Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			PackMixerClient.LOGGER.error("Could not save mixes", e);
		}
	}

	/** Adds a mix (a mix with the same name is replaced). */
	public static void add(Mix mix) {
		all().removeIf(m -> m.name.equalsIgnoreCase(mix.name));
		all().add(0, mix);
		save();
	}

	public static void remove(Mix mix) {
		all().remove(mix);
		save();
	}

	// ======================================================================= share codes

	/** Short text code of a mix, e.g. "TS1-eJx...". */
	public static String code(Mix mix) {
		byte[] json = new Gson().toJson(mix).getBytes(StandardCharsets.UTF_8);
		Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
		d.setInput(json);
		d.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (!d.finished()) out.write(buf, 0, d.deflate(buf));
		d.end();
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
	}

	/** Reads a code; null if it isn't a valid Testohd code. */
	public static Mix decode(String code) {
		if (code == null) return null;
		code = code.trim().replaceAll("\\s", "");
		int i = code.indexOf(PREFIX);
		if (i < 0) return null;
		try {
			byte[] data = Base64.getUrlDecoder().decode(code.substring(i + PREFIX.length()));
			Inflater inf = new Inflater();
			inf.setInput(data);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			while (!inf.finished()) {
				int n = inf.inflate(buf);
				if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
				out.write(buf, 0, n);
				if (out.size() > 200_000) break;
			}
			inf.end();
			Mix m = new Gson().fromJson(out.toString(StandardCharsets.UTF_8), Mix.class);
			if (m == null || m.selection == null) return null;
			if (m.name == null || m.name.isBlank()) m.name = "Pack";
			m.name = m.name.length() > 32 ? m.name.substring(0, 32) : m.name;
			return m;
		} catch (Exception e) {
			return null;
		}
	}

	/** Packs of a mix this player doesn't have. */
	public static List<String> missing(Mix mix, PackLibrary library) {
		List<String> l = new ArrayList<>();
		for (String p : new java.util.LinkedHashSet<>(mix.selection.values())) if (library.get(p) == null) l.add(p);
		return l;
	}

	private Mixes() {
	}
}
