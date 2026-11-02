package de.philjoel.packmixer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A texture category (e.g. "Totems"). Every pattern is a path relative to
 * {@code assets/minecraft/}. Supported wildcards: {@code *} (anything except '/')
 * and {@code **} (anything, including '/').
 */
public final class Category {
	public String id;
	public String name;
	/** Candidate textures used for the preview icon; the first one that exists wins. */
	public List<String> icons = new ArrayList<>();
	public List<String> files = new ArrayList<>();
	/** Optional 3D block preview: [top texture, side texture]. */
	public List<String> cube;
	/** Optional colour for the cube top, e.g. "#7CBD6B" for the grey grass top. */
	public String topTint;

	private transient List<Pattern> compiled;

	public Category cube(String top, String side, String tint) {
		this.cube = List.of(top, side);
		this.topTint = tint;
		return this;
	}

	/** Translated name ("packmixer.category.<id>") – falls back to the name from categories.json. */
	public String displayName() {
		String key = "packmixer.category." + id;
		return net.minecraft.locale.Language.getInstance().has(key) ? net.minecraft.client.resources.language.I18n.get(key) : name;
	}

	public boolean isCube() {
		return cube != null && cube.size() == 2;
	}

	public int topTintArgb() {
		if (topTint == null) return 0xFFFFFFFF;
		try {
			return 0xFF000000 | Integer.parseInt(topTint.replace("#", ""), 16);
		} catch (NumberFormatException e) {
			return 0xFFFFFFFF;
		}
	}

	public Category() {
	}

	public Category(String id, String name, List<String> icons, List<String> files) {
		this.id = id;
		this.name = name;
		this.icons = new ArrayList<>(icons);
		this.files = new ArrayList<>(files);
	}

	public boolean matches(String path) {
		if (compiled == null) {
			List<Pattern> list = new ArrayList<>();
			for (String glob : files) {
				list.add(globToRegex(glob));
			}
			compiled = list;
		}
		for (Pattern p : compiled) {
			if (p.matcher(path).matches()) {
				return true;
			}
		}
		return false;
	}

	static Pattern globToRegex(String glob) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*') {
				if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
					sb.append(".*");
					i++;
				} else {
					sb.append("[^/]*");
				}
			} else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) {
				sb.append('\\').append(c);
			} else {
				sb.append(c);
			}
		}
		// also accept animation / metadata files that belong to a matched texture
		sb.append("(\\.mcmeta)?");
		return Pattern.compile(sb.toString());
	}
}
