package de.philjoel.packmixer;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emotes: wave, dance, cheer ... Your own start with the emote wheel (key B) and are sent to everybody with the
 * mod (same channel as the cosmetics), so they see them right away.
 */
public final class Emotes {
	public record Emote(String id, String glyph, float seconds) {
	}

	public static final List<Emote> ALL = List.of(
			new Emote("wave", "✋", 2.6f), new Emote("dance", "♫", 6f), new Emote("cheer", "★", 3f),
			new Emote("dab", "➚", 2f), new Emote("salute", "⚑", 2.6f), new Emote("clap", "✦", 3f),
			new Emote("facepalm", "☹", 2.4f), new Emote("flex", "⚡", 3f),
			new Emote("football", "⚽", 6f), new Emote("basketball", "●", 3.6f), new Emote("default_dance", "♪", 6f),
			new Emote("take_l", "L", 5f), new Emote("floss", "≈", 5f), new Emote("robot", "⚙", 5f), new Emote("headbang", "♬", 4f),
			new Emote("bow", "♛", 2.4f));

	public static KeyMapping key;

	private record Active(Emote emote, long startNanos) {
	}

	private static final Map<UUID, Active> active = new ConcurrentHashMap<>();
	private static CameraType cameraBefore;
	private static double startX, startZ;

	static Emote byId(String id) {
		for (Emote e : ALL) if (e.id.equals(id)) return e;
		return null;
	}

	/** You start an emote. */
	public static void play(String id) {
		Minecraft mc = Minecraft.getInstance();
		Emote e = byId(id);
		if (e == null || mc.player == null) return;
		active.put(mc.player.getUUID(), new Active(e, System.nanoTime()));
		startX = mc.player.getX();
		startZ = mc.player.getZ();
		if (PackMixerConfig.settings().emoteCamera && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			cameraBefore = CameraType.FIRST_PERSON;
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
		if (PackMixerConfig.settings().shareCosmetics) CosmeticSync.send("te1;" + mc.player.getUUID() + ";" + id);
	}

	/** Somebody else started an emote. */
	static void received(String uuid, String id) {
		Emote e = byId(id);
		if (e == null) return;
		try {
			UUID u = UUID.fromString(uuid);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && mc.player.getUUID().equals(u)) return; // our own echo
			active.put(u, new Active(e, System.nanoTime()));
		} catch (IllegalArgumentException ignored) {
		}
	}

	static void tick(Minecraft mc) {
		long now = System.nanoTime();
		active.entrySet().removeIf(en -> (now - en.getValue().startNanos) / 1e9 > en.getValue().emote.seconds);
		if (mc.player != null) {
			Active own = active.get(mc.player.getUUID());
			// walking away stops your emote
			if (own != null && (now - own.startNanos) / 1e9 > 0.4
					&& Math.hypot(mc.player.getX() - startX, mc.player.getZ() - startZ) > 0.6) active.remove(mc.player.getUUID());
			if (active.get(mc.player.getUUID()) == null && cameraBefore != null) {
				if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(cameraBefore);
				cameraBefore = null;
			}
		}
	}

	/** Seconds into the current emote of this player, or -1. */
	private static float time(UUID uuid, Active a) {
		return a == null ? -1 : (System.nanoTime() - a.startNanos) / 1e9f;
	}

	/** Called after the vanilla pose was set up: bends arms / legs / head for the running emote. */
	public static void pose(PlayerModel model, AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || active.isEmpty()) return;
		Entity e = mc.level.getEntity(state.id);
		if (e == null) return;
		Active a = active.get(e.getUUID());
		if (a == null) return;
		float t = time(e.getUUID(), a), dur = a.emote.seconds;
		// blend in / out over 0.25 s
		float k = Math.min(1f, Math.min(t, dur - t) / 0.25f);
		if (k <= 0) return;
		float s = t * 20f; // ticks
		ModelPart ra = model.rightArm, la = model.leftArm, rl = model.rightLeg, ll = model.leftLeg, head = model.head, body = model.body;
		switch (a.emote.id) {
			case "wave" -> {
				set(ra, -3.0f, 0, 0.55f + 0.4f * (float) Math.sin(s * 0.55f), k);
			}
			case "dance" -> {
				float b = (float) Math.sin(s * 0.45f);
				set(ra, -1.6f - 1.0f * b, 0, 0.5f, k);
				set(la, -1.6f + 1.0f * b, 0, -0.5f, k);
				set(rl, 0.45f * b, 0, 0.08f, k);
				set(ll, -0.45f * b, 0, -0.08f, k);
				set(body, 0, 0.35f * (float) Math.sin(s * 0.225f), 0, k);
				set(head, 0, 0, 0.25f * b, k);
			}
			case "cheer" -> {
				float b = (float) Math.abs(Math.sin(s * 0.4f));
				set(ra, -3.0f + 0.2f * b, 0, 0.7f, k);
				set(la, -3.0f + 0.2f * b, 0, -0.7f, k);
				set(head, -0.35f, 0, 0, k);
			}
			case "dab" -> {
				set(ra, -0.2f, 0, 2.3f, k);           // stretched up and out to the side
				set(la, -1.75f, 0.95f, 0, k);         // across in front of the face
				set(head, 0.55f, 0.4f, 0, k);
			}
			case "salute" -> {
				set(ra, -2.25f, -0.45f, -0.75f, k);   // hand at the forehead
				set(head, -0.1f, 0, 0, k);
			}
			case "clap" -> {
				float c = (float) Math.abs(Math.sin(s * 0.6f));
				set(ra, -1.35f, -0.15f - 0.45f * c, 0, k);
				set(la, -1.35f, 0.15f + 0.45f * c, 0, k);
			}
			case "facepalm" -> {
				set(ra, -2.1f, -0.75f, 0, k);
				set(head, 0.45f, 0, 0, k);
			}
			case "flex" -> {
				float p = 0.1f * (float) Math.sin(s * 0.5f);
				set(ra, 0, 0, 2.2f + p, k);
				set(la, 0, 0, -2.2f - p, k);
			}
			case "football" -> { // keepy-uppy: the right foot kicks the ball up again and again
				float u = (s / 12f) % 1f;
				float kick = Math.max(0, 1 - u * 4);
				set(rl, -0.75f * kick, 0, 0, k);
				set(ra, 0, 0, 0.45f, k);
				set(la, 0, 0, -0.45f, k);
				set(head, 0.45f, 0, 0, k);
			}
			case "basketball" -> {
				if (t < 1.6f) { // dribbling
					float d = (float) Math.abs(Math.sin(Math.PI * s / 8));
					set(ra, -0.75f + 0.35f * d, 0, 0.1f, k);
					set(head, 0.3f, 0, 0, k);
				} else { // jump shot: both arms up, ball flies away
					float up = Math.min(1f, (t - 1.6f) / 0.35f);
					set(ra, -1.3f - 1.6f * up, 0, 0.15f, k);
					set(la, -1.3f - 1.6f * up, 0, -0.15f, k);
					set(head, -0.35f * up, 0, 0, k);
				}
			}
			case "default_dance" -> {
				float p = s * 0.35f, sn = (float) Math.sin(p), cs = (float) Math.cos(p);
				set(ra, -1.1f + 0.9f * sn, 0, -0.55f * sn + 0.2f, k);
				set(la, -1.1f - 0.9f * sn, 0, -0.55f * sn - 0.2f, k);
				set(rl, 0.45f * Math.max(0, cs), 0, 0.05f, k);
				set(ll, 0.45f * Math.max(0, -cs), 0, -0.05f, k);
				set(body, 0, 0.25f * sn, 0, k);
				set(head, 0, -0.2f * sn, 0.12f * cs, k);
			}
			case "take_l" -> { // "L" on the forehead, legs kicking out to the side
				float b = (float) Math.sin(s * 0.5f);
				set(la, -2.5f, 0.2f, -0.35f, k);
				set(ra, -0.3f, 0, 0.35f + 0.2f * b, k);
				set(rl, 0, 0, 0.55f * Math.max(0, b), k);
				set(ll, 0, 0, -0.55f * Math.max(0, -b), k);
				set(head, -0.15f, 0.3f * b, 0, k);
			}
			case "floss" -> { // arms swing side to side, hips the other way
				float p = (float) Math.sin(s * 0.55f), sw = Math.signum((float) Math.sin(s * 1.1f));
				set(ra, 0.35f * sw, 0, 0.5f * p, k);
				set(la, -0.35f * sw, 0, 0.5f * p, k);
				set(body, 0, -0.3f * p, 0, k);
				set(rl, 0, 0, -0.15f * p, k);
				set(ll, 0, 0, -0.15f * p, k);
			}
			case "robot" -> { // stiff moves, one pose after the other
				int step = (int) (s / 8) % 4;
				float[][] poses = {{-1.57f, 0, -0.3f, 0}, {-1.57f, -1.57f, 0, 0.5f}, {0, -1.57f, 0.5f, 0}, {-1.57f, -1.57f, -0.3f, -0.5f}};
				float[] q = poses[step];
				set(ra, q[0], 0, 0, k);
				set(la, q[1], 0, 0, k);
				set(head, 0, q[2], 0, k);
				set(body, 0, q[3] * 0.3f, 0, k);
			}
			case "headbang" -> { // head goes hard, air guitar
				float h = (float) Math.sin(s * 0.9f);
				set(head, 0.25f + 0.45f * h, 0, 0, k);
				set(ra, -0.9f + 0.3f * h, -0.45f, 0, k);
				set(la, -0.75f, 0.55f, 0, k);
			}
			case "bow" -> {
				float bw = (float) Math.sin(Math.min(1f, t / dur) * Math.PI);
				set(body, 0.5f * bw, 0, 0, k);
				set(head, 0.6f * bw, 0, 0, k);
				set(ra, -0.4f * bw, 0, 0.1f, k);
				set(la, 0.3f * bw, 0, -0.1f, k);
			}
			default -> {
			}
		}
	}

	/**
	 * Ball of a football / basketball emote in model space of this player (x, y down, z forward = negative),
	 * {type 0 = football, 1 = basketball}, or null.
	 */
	public static float[] ball(UUID uuid) {
		Active a = active.get(uuid);
		if (a == null) return null;
		float t = time(uuid, a), s = t * 20f;
		if (a.emote.id.equals("football")) {
			float u = (s / 12f) % 1f;
			float h = 4 * u * (1 - u); // up and down between two kicks
			return new float[]{-0.12f, 1.32f - 1.05f * h, -0.32f, 0};
		}
		if (a.emote.id.equals("basketball")) {
			if (t < 1.6f) {
				float d = (float) Math.abs(Math.sin(Math.PI * s / 8));
				return new float[]{-0.36f, 1.38f - 0.6f * d, -0.3f, 1};
			}
			float tt = t - 1.6f;
			if (tt < 0.35f) return new float[]{0, 0.6f - 1.15f * (tt / 0.35f), -0.25f, 1};
			float f = tt - 0.35f;
			if (f > 1.6f) return null;
			return new float[]{0, -0.55f - 3.2f * f + 2.4f * f * f, -0.25f - 3.0f * f, 1};
		}
		return null;
	}

	private static void set(ModelPart p, float x, float y, float z, float k) {
		p.xRot += (x - p.xRot) * k;
		p.yRot += (y - p.yRot) * k;
		p.zRot += (z - p.zRot) * k;
	}

	public static boolean isActive(UUID uuid) {
		return active.containsKey(uuid);
	}

	private Emotes() {
	}
}
