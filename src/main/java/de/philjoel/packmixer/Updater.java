package de.philjoel.packmixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * Auto-update from GitHub releases.
 * <p>
 * On start the mod asks GitHub for the newest release. If it is newer, the jar is downloaded next to the
 * running one as {@code *.jar.update} (Fabric ignores that ending, so nothing can load twice). When the game
 * closes, the old jar is replaced by the new one – the next start runs the new version.
 */
public final class Updater {
	/** GitHub "owner/repo" the releases come from. */
	public static final String REPO = "philjoelblank-cell/Testohd23";

	private static volatile Component pendingMessage;
	private static volatile String status = "";

	public static String status() {
		return status;
	}

	public static void checkAsync() {
		if (REPO.startsWith("OWNER/") || !PackMixerConfig.settings().autoUpdate) return;
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;
		retryPendingInstall();
		Thread t = new Thread(Updater::check, "Testohd Styles updater");
		t.setDaemon(true);
		t.start();
	}

	/** Shows the "update downloaded" message once the player is in a world. */
	public static void tick(Minecraft mc) {
		if (pendingMessage != null && mc.player != null) {
			mc.gui.getChat().addMessage(pendingMessage);
			pendingMessage = null;
		}
	}

	private static void check() {
		try {
			ModContainer mod = FabricLoader.getInstance().getModContainer(PackMixerClient.MOD_ID).orElse(null);
			if (mod == null) return;
			String current = mod.getMetadata().getVersion().getFriendlyString();
			Path ownJar = mod.getOrigin().getPaths().stream().filter(p -> p.toString().endsWith(".jar")).findFirst().orElse(null);
			if (ownJar == null || !Files.isRegularFile(ownJar)) return; // not running from a jar

			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "testohd-styles-updater")
					.timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) {
				status = "GitHub: " + res.statusCode();
				return;
			}
			JsonObject release = JsonParser.parseString(res.body()).getAsJsonObject();
			String latest = release.get("tag_name").getAsString().replaceFirst("^[vV]", "");
			if (compare(latest, current) <= 0) {
				status = "up to date (" + current + ")";
				return;
			}
			String url = null, fileName = null;
			JsonArray assets = release.getAsJsonArray("assets");
			for (JsonElement e : assets) {
				JsonObject a = e.getAsJsonObject();
				String name = a.get("name").getAsString();
				if (name.endsWith(".jar") && !name.contains("sources")) {
					url = a.get("browser_download_url").getAsString();
					fileName = name;
					break;
				}
			}
			if (url == null) return;

			Path modsDir = ownJar.getParent();
			Path newJar = modsDir.resolve(fileName);
			Path update = modsDir.resolve(fileName + ".update");
			Path tmp = modsDir.resolve(fileName + ".download");
			HttpResponse<InputStream> dl = http.send(HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "testohd-styles-updater").timeout(Duration.ofMinutes(2)).GET().build(),
					HttpResponse.BodyHandlers.ofInputStream());
			if (dl.statusCode() != 200) return;
			try (InputStream in = dl.body()) {
				Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.size(tmp) < 10_000) { // not a real jar
				Files.deleteIfExists(tmp);
				return;
			}
			Files.move(tmp, update, StandardCopyOption.REPLACE_EXISTING);
			installOnExit(ownJar, update, newJar);
			status = "update " + latest + " ready";
			PackMixerClient.LOGGER.info("Testohd Styles {} downloaded, will be installed when the game closes", latest);
			boolean de = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("de");
			pendingMessage = Component.literal("[Testohd Styles] ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal(de
							? "Update " + latest + " wurde geladen – starte Minecraft neu, dann ist es aktiv."
							: "Update " + latest + " downloaded – restart Minecraft to use it.").withStyle(ChatFormatting.WHITE));
		} catch (Exception e) {
			status = "check failed";
			PackMixerClient.LOGGER.warn("Update check failed: {}", e.toString());
		}
	}

	/** An update was downloaded earlier but could not be installed yet -> try again when the game closes. */
	private static void retryPendingInstall() {
		try {
			ModContainer mod = FabricLoader.getInstance().getModContainer(PackMixerClient.MOD_ID).orElse(null);
			if (mod == null) return;
			Path ownJar = mod.getOrigin().getPaths().stream().filter(p -> p.toString().endsWith(".jar")).findFirst().orElse(null);
			if (ownJar == null) return;
			try (var files = Files.list(ownJar.getParent())) {
				for (Path u : files.filter(p -> p.getFileName().toString().startsWith("testohd-styles-")
						&& p.getFileName().toString().endsWith(".jar.update")).toList()) {
					String name = u.getFileName().toString();
					installOnExit(ownJar, u, u.resolveSibling(name.substring(0, name.length() - ".update".length())));
				}
			}
		} catch (Exception ignored) {
		}
	}

	/** Swaps the jars after the game process has ended (on Windows a running jar is locked). */
	private static void installOnExit(Path oldJar, Path update, Path newJar) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
					Path bat = Files.createTempFile("testohd-update", ".bat");
					String script = "@echo off\r\n"
							+ "for /l %%i in (1,1,60) do (\r\n"
							+ "  ping -n 2 127.0.0.1 >nul\r\n"
							+ "  del /f /q \"" + oldJar + "\" 2>nul\r\n"
							+ "  if not exist \"" + oldJar + "\" goto swap\r\n"
							+ ")\r\n"
							+ "exit /b\r\n"
							+ ":swap\r\n"
							+ "move /y \"" + update + "\" \"" + newJar + "\" >nul\r\n"
							+ "del \"%~f0\"\r\n";
					Files.writeString(bat, script, StandardCharsets.ISO_8859_1);
					new ProcessBuilder("cmd", "/c", bat.toString()).start(); // keeps running after Java has exited
				} else {
					Files.deleteIfExists(oldJar);
					Files.move(update, newJar, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (Exception e) {
				// keep the old version; the update is tried again next time
			}
		}, "Testohd Styles update installer"));
	}

	/** Compares versions like 1.2.10 numerically. */
	static int compare(String a, String b) {
		String[] x = a.split("[.+-]"), y = b.split("[.+-]");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
			if (p != q) return Integer.compare(p, q);
		}
		return 0;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private Updater() {
	}
}
