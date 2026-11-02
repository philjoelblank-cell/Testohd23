package de.philjoel.packmixer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets mod users see each other's cosmetics – without a server of our own.
 * <p>
 * Every player posts one short line ({@code ts1;<uuid>;<head>;<face>;<back>;<effect>}) to a public
 * <a href="https://ntfy.sh">ntfy.sh</a> topic; everybody reads the topic every two minutes. Only the player
 * UUID and the names of the chosen cosmetics are sent. Both directions can be switched off in the settings.
 */
public final class CosmeticSync {
	private static final String TOPIC_URL = "https://ntfy.sh/testohd-styles-cosmetics-7f3k9q";
	private static final long POLL_MS = 120_000, REPUBLISH_MS = 3 * 3600_000L;

	private static final Map<UUID, Cosmetics.Loadout> remote = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static volatile boolean busy;
	private static long lastPoll, lastPublish, publishAt;
	private static String lastSent = "";

	public static Cosmetics.Loadout get(UUID uuid) {
		return remote.getOrDefault(uuid, Cosmetics.Loadout.NONE);
	}

	/** Your cosmetics changed: send them shortly (a few changes in a row = one message). */
	public static void publishSoon() {
		publishAt = System.currentTimeMillis() + 4000;
	}

	static void tick(Minecraft mc) {
		if (mc.player == null || busy) return;
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		long now = System.currentTimeMillis();
		boolean periodic = lastPublish != 0 && now - lastPublish > REPUBLISH_MS;
		if (s.shareCosmetics && (lastPublish == 0 || (publishAt != 0 && now >= publishAt) || periodic)) {
			publishAt = 0;
			lastPublish = now;
			String line = line(mc.player.getUUID(), Cosmetics.own());
			if (periodic || !line.equals(lastSent)) {
				lastSent = line;
				run(() -> HTTP.send(HttpRequest.newBuilder(URI.create(TOPIC_URL)).timeout(Duration.ofSeconds(10))
						.header("User-Agent", "testohd-styles").POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8)).build(),
						HttpResponse.BodyHandlers.discarding()));
			}
			return;
		}
		if (s.showOthersCosmetics && now - lastPoll > POLL_MS) {
			lastPoll = now;
			run(CosmeticSync::poll);
		}
	}

	private static String line(UUID uuid, Cosmetics.Loadout l) {
		return "ts1;" + uuid + ";" + l.head() + ";" + l.face() + ";" + l.back() + ";" + l.effect();
	}

	private static void poll() throws Exception {
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(TOPIC_URL + "/json?poll=1&since=12h"))
				.timeout(Duration.ofSeconds(15)).header("User-Agent", "testohd-styles").GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) return;
		// messages come oldest first -> later lines overwrite earlier ones
		for (String json : res.body().split("\n")) {
			if (json.isBlank()) continue;
			try {
				JsonObject o = JsonParser.parseString(json).getAsJsonObject();
				if (!o.has("message")) continue;
				parse(o.get("message").getAsString());
			} catch (Exception ignored) {
			}
		}
	}

	/** Strict check: only known cosmetic names are accepted. */
	static void parse(String msg) {
		String[] p = msg.trim().split(";", -1);
		if (p.length != 6 || !p[0].equals("ts1")) return;
		UUID uuid;
		try {
			uuid = UUID.fromString(p[1]);
		} catch (IllegalArgumentException e) {
			return;
		}
		if (!Cosmetics.valid(Cosmetics.Slot.HEAD, p[2]) || !Cosmetics.valid(Cosmetics.Slot.FACE, p[3])
				|| !Cosmetics.valid(Cosmetics.Slot.BACK, p[4]) || !Cosmetics.valid(Cosmetics.Slot.EFFECT, p[5])) return;
		Cosmetics.Loadout l = new Cosmetics.Loadout(p[2], p[3], p[4], p[5]);
		if (l.isEmpty()) remote.remove(uuid);
		else remote.put(uuid, l);
	}

	private interface Job {
		void run() throws Exception;
	}

	private static void run(Job job) {
		busy = true;
		Thread t = new Thread(() -> {
			try {
				job.run();
			} catch (Exception e) {
				PackMixerClient.LOGGER.debug("Cosmetic sync: {}", e.toString());
			} finally {
				busy = false;
			}
		}, "Testohd cosmetics sync");
		t.setDaemon(true);
		t.start();
	}

	private CosmeticSync() {
	}
}
