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
 * <a href="https://ntfy.sh">ntfy.sh</a> topic; everybody stays subscribed to the topic, so a change shows up for the
 * others within a few seconds – no relog needed (if the live connection fails: poll every two minutes). Only the player
 * UUID and the names of the chosen cosmetics are sent. Both directions can be switched off in the settings.
 */
public final class CosmeticSync {
	private static final String TOPIC_URL = "https://ntfy.sh/testohd-styles-cosmetics-7f3k9q";
	private static final long POLL_MS = 120_000, REPUBLISH_MS = 3 * 3600_000L;

	private static final Map<UUID, Cosmetics.Loadout> remote = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static volatile boolean busy, streaming;
	private static Thread streamThread;
	private static long lastPoll, lastPublish, publishAt, lastCheck;
	private static String lastSent = "";

	public static Cosmetics.Loadout get(UUID uuid) {
		return remote.getOrDefault(uuid, Cosmetics.Loadout.NONE);
	}

	/** Your cosmetics changed: send them shortly (a few changes in a row = one message). */
	public static void publishSoon() {
		publishAt = System.currentTimeMillis() + 1500;
	}

	static void tick(Minecraft mc) {
		if (mc.player == null || busy) return;
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		long now = System.currentTimeMillis();
		// any change (also switching cosmetics on/off) is sent, not only changes made in the cosmetics menu
		if (now - lastCheck > 1000) {
			lastCheck = now;
			if (s.shareCosmetics && lastPublish != 0 && publishAt == 0 && !line(mc.player.getUUID(), Cosmetics.own()).equals(lastSent))
				publishSoon();
		}
		if (s.showOthersCosmetics) startStream();
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
		if (s.showOthersCosmetics && !streaming && now - lastPoll > POLL_MS) {
			lastPoll = now;
			run(CosmeticSync::poll);
		}
	}

	private static String line(UUID uuid, Cosmetics.Loadout l) {
		return "ts2;" + uuid + ";" + l.head() + ";" + l.face() + ";" + l.back() + ";" + l.effect() + ";" + l.cape();
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
				parse(o.get("message").getAsString(), age(o));
			} catch (Exception ignored) {
			}
		}
	}

	/** Live subscription: ntfy keeps the connection open and sends every new line right away. */
	private static synchronized void startStream() {
		if (streamThread != null) return;
		streamThread = new Thread(() -> {
			long wait = 5000;
			while (true) {
				try {
					if (!PackMixerConfig.settings().showOthersCosmetics) {
						Thread.sleep(10_000);
						continue;
					}
					HttpResponse<java.io.InputStream> res = HTTP.send(HttpRequest.newBuilder(URI.create(TOPIC_URL + "/json?since=12h"))
							.timeout(Duration.ofSeconds(20)).header("User-Agent", "testohd-styles").GET().build(),
							HttpResponse.BodyHandlers.ofInputStream());
					if (res.statusCode() == 200) {
						streaming = true;
						wait = 5000;
						try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
							String json;
							while ((json = r.readLine()) != null) {
								if (!PackMixerConfig.settings().showOthersCosmetics) break;
								if (json.isBlank()) continue;
								try {
									JsonObject o = JsonParser.parseString(json).getAsJsonObject();
									if (o.has("event") && o.get("event").getAsString().equals("message") && o.has("message"))
										parse(o.get("message").getAsString(), age(o));
								} catch (Exception ignored) {
								}
							}
						}
					} else res.body().close();
				} catch (InterruptedException e) {
					return;
				} catch (Exception e) {
					PackMixerClient.LOGGER.debug("Cosmetic stream: {}", e.toString());
				}
				streaming = false;
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					return;
				}
				wait = Math.min(wait * 2, 120_000);
			}
		}, "Testohd cosmetics live");
		streamThread.setDaemon(true);
		streamThread.start();
	}

	/** Strict check: only known cosmetic names are accepted. */
	static void parse(String msg) {
		parse(msg, 0);
	}

	/** @param ageSeconds how old the message is (from ntfy's time stamp) */
	static void parse(String msg, long ageSeconds) {
		String[] p = msg.trim().split(";", -1);
		if (p.length == 3 && p[0].equals("te1")) { // emote (only fresh ones, not from the history)
			if (ageSeconds <= 8) Emotes.received(p[1], p[2]);
			return;
		}
		boolean v1 = p.length == 6 && p[0].equals("ts1"), v2 = p.length == 7 && p[0].equals("ts2");
		if (!v1 && !v2) return;
		String cape = v2 ? p[6] : "";
		if (!Cosmetics.valid(Cosmetics.Slot.CAPE, cape)) return;
		UUID uuid;
		try {
			uuid = UUID.fromString(p[1]);
		} catch (IllegalArgumentException e) {
			return;
		}
		if (!Cosmetics.valid(Cosmetics.Slot.HEAD, p[2]) || !Cosmetics.valid(Cosmetics.Slot.FACE, p[3])
				|| !Cosmetics.valid(Cosmetics.Slot.BACK, p[4]) || !Cosmetics.valid(Cosmetics.Slot.EFFECT, p[5])) return;
		Cosmetics.Loadout l = new Cosmetics.Loadout(p[2], p[3], p[4], p[5], cape);
		if (l.isEmpty()) remote.remove(uuid);
		else remote.put(uuid, l);
	}

	private static long age(JsonObject o) {
		return o.has("time") ? System.currentTimeMillis() / 1000 - o.get("time").getAsLong() : 9999;
	}

	/** Sends one line right away (emotes). */
	static void send(String line) {
		Thread t = new Thread(() -> {
			try {
				HTTP.send(HttpRequest.newBuilder(URI.create(TOPIC_URL)).timeout(Duration.ofSeconds(10))
						.header("User-Agent", "testohd-styles").POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8)).build(),
						HttpResponse.BodyHandlers.discarding());
			} catch (Exception e) {
				PackMixerClient.LOGGER.debug("Emote send: {}", e.toString());
			}
		}, "Testohd emote");
		t.setDaemon(true);
		t.start();
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
