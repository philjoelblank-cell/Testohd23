package de.philjoel.packmixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Minimal client for the public Modrinth API (search + download resource packs). */
public final class Modrinth {
	private static final String API = "https://api.modrinth.com/v2";
	private static final String USER_AGENT = "Testohd23/TestohdStyles/1.0.0 (Minecraft mod)";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public record Hit(String id, String slug, String title, String author, String description, long downloads,
			String license, List<String> versions) {
		public String lastVersion() {
			return versions.isEmpty() ? "?" : versions.get(versions.size() - 1);
		}
	}

	/** @param sort "downloads", "relevance", "follows" or "newest" */
	public static CompletableFuture<List<Hit>> search(String query, String sort, int limit) {
		String facets = "[[\"project_type:resourcepack\"]]";
		String url = API + "/search?query=" + enc(query) + "&index=" + enc(sort) + "&limit=" + limit
				+ "&facets=" + enc(facets);
		return get(url).thenApply(body -> {
			List<Hit> hits = new ArrayList<>();
			JsonArray arr = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("hits");
			for (JsonElement e : arr) {
				JsonObject o = e.getAsJsonObject();
				List<String> versions = new ArrayList<>();
				if (o.has("versions")) for (JsonElement v : o.getAsJsonArray("versions")) versions.add(v.getAsString());
				hits.add(new Hit(str(o, "project_id"), str(o, "slug"), str(o, "title"), str(o, "author"),
						str(o, "description"), o.has("downloads") ? o.get("downloads").getAsLong() : 0,
						str(o, "license"), versions));
			}
			return hits;
		});
	}

	/** Downloads the newest file (preferably for 1.21.11) into the PackMixer packs folder. */
	public static CompletableFuture<Path> download(Hit hit) {
		String filtered = API + "/project/" + hit.id() + "/version?game_versions=" + enc("[\"1.21.11\"]");
		String all = API + "/project/" + hit.id() + "/version";
		return get(filtered)
				.thenCompose(body -> JsonParser.parseString(body).getAsJsonArray().isEmpty() ? get(all) : CompletableFuture.completedFuture(body))
				.thenCompose(body -> {
					JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
					if (versions.isEmpty()) throw new IllegalStateException("Keine Dateien gefunden");
					JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
					JsonObject file = files.get(0).getAsJsonObject();
					for (JsonElement f : files) {
						if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
							file = f.getAsJsonObject();
						}
					}
					String name = str(file, "filename").replaceAll("[\\\\/:*?\"<>|]", "_");
					if (!name.toLowerCase().endsWith(".zip")) name = name + ".zip";
					Path target = PackMixerConfig.packsDir().resolve(name);
					HttpRequest req = HttpRequest.newBuilder(URI.create(str(file, "url")))
							.header("User-Agent", USER_AGENT).timeout(Duration.ofMinutes(3)).build();
					Path tmp = target.resolveSibling(name + ".part");
					return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofFile(tmp)).thenApply(resp -> {
						try {
							if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
							Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
							return target;
						} catch (Exception ex) {
							throw new RuntimeException(ex);
						}
					});
				});
	}

	private static CompletableFuture<String> get(String url) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(20)).build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
			if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
			return resp.body();
		});
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private Modrinth() {
	}
}
