package de.philjoel.packmixer;


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

	/**
	 * Minecraft 26.x has no file dialog library any more: the packs folder is opened instead,
	 * packs can be copied there or dragged into the window.
	 */
	public static List<Path> chooseZipFiles(String title) {
		PackMixerConfig.ensureDirs();
		com.mojang.blaze3d.Blaze3D.openPath(PackMixerConfig.packsDir());
		return new ArrayList<>();
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
