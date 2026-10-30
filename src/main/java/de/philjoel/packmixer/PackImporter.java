package de.philjoel.packmixer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Copies texture packs into packmixer/packs (from the file dialog or drag & drop). */
public final class PackImporter {
	public record Result(int added, int skipped) {
	}

	/** Opens the system file dialog (zip files, multi-select). Returns an empty list if cancelled. */
	public static List<Path> chooseZipFiles(String title) {
		List<Path> out = new ArrayList<>();
		Path start = Path.of(System.getProperty("user.home"), "Downloads");
		String defaultPath = (Files.isDirectory(start) ? start : Path.of(System.getProperty("user.home")))
				.toAbsolutePath() + java.io.File.separator;
		String result;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8("*.zip"));
			filters.flip();
			result = TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, filters, "Texture Packs (*.zip)", true);
		} catch (Throwable t) {
			PackMixerClient.LOGGER.error("File dialog failed", t);
			return out;
		}
		if (result == null || result.isBlank()) return out;
		for (String s : result.split("\\|")) {
			if (!s.isBlank()) out.add(Path.of(s));
		}
		return out;
	}

	/** Copies all valid packs (zip or folder) into the PackMixer packs folder. */
	public static Result importPacks(List<Path> paths) {
		PackMixerConfig.ensureDirs();
		Path dest = PackMixerConfig.packsDir();
		int added = 0;
		int skipped = 0;
		for (Path src : paths) {
			try {
				if (src == null || !Files.exists(src) || SourcePack.open(src) == null) {
					skipped++;
					continue;
				}
				Path target = dest.resolve(src.getFileName().toString());
				if (target.toAbsolutePath().normalize().equals(src.toAbsolutePath().normalize())) {
					skipped++; // already in the folder
					continue;
				}
				if (Files.isDirectory(src)) {
					copyDirectory(src, target);
				} else {
					Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
				}
				added++;
			} catch (Exception e) {
				PackMixerClient.LOGGER.warn("Could not import {}", src, e);
				skipped++;
			}
		}
		return new Result(added, skipped);
	}

	private static void copyDirectory(Path src, Path target) throws IOException {
		try (Stream<Path> walk = Files.walk(src)) {
			for (Path p : (Iterable<Path>) walk::iterator) {
				Path t = target.resolve(src.relativize(p).toString());
				if (Files.isDirectory(p)) {
					Files.createDirectories(t);
				} else {
					Files.createDirectories(t.getParent());
					Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private PackImporter() {
	}
}
