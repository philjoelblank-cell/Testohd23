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
			new Emote("dab", "➚", 2f), new Emote("shrug", "?", 2.6f), new Emote("clap", "✦", 3f),
			new Emote("facepalm", "☹", 2.4f), new Emote("flex", "⚡", 3f),
			new Emote("football", "⚽", 6f), new Emote("basketball", "●", 3.6f), new Emote("default_dance", "♪", 6f),
			new Emote("take_l", "L", 5f), new Emote("floss", "≈", 5f), new Emote("robot", "⚙", 5f), new Emote("headbang", "♬", 4f),
			new Emote("bow", "♛", 2.4f),
			new Emote("sit", "▄", 6f), new Emote("faint", "✝", 4f), new Emote("superman", "✈", 4f), new Emote("ascend", "↑", 4f),
			new Emote("spin", "✿", 3f), new Emote("yellow_card", "▮", 2.6f), new Emote("red_card", "▮", 2.6f),
			new Emote("worship", "☼", 4f), new Emote("six_seven", "67", 3f), new Emote("vibe", "♩", 5f));

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
	/** Render state ids at or below this are previews in the emote wheel: -(PREVIEW + index of the emote). */
	public static final int PREVIEW = 100000;

	/** Emote shown by a preview doll (its render state id), or null. */
	public static Emote preview(int stateId) {
		int i = -stateId - PREVIEW;
		return stateId <= -PREVIEW && i < ALL.size() ? ALL.get(i) : null;
	}

	/** Seconds into the looping preview of an emote. */
	static float previewTime(Emote e) {
		return (System.nanoTime() / 1e9f) % e.seconds;
	}

	public static void pose(PlayerModel model, AvatarRenderState state) {
		if (poseInner(model, state)) { // only while an emote plays (bows, crossbows ... stay vanilla)
			guard(model.rightArm, 1f);
			guard(model.leftArm, -1f);
		}
	}

	private static boolean poseInner(PlayerModel model, AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		Emote em;
		float t;
		Emote pv = preview(state.id);
		if (pv != null) {
			em = pv;
			t = previewTime(pv);
		} else {
			if (mc.level == null || active.isEmpty()) return false;
			Entity e = mc.level.getEntity(state.id);
			if (e == null) return false;
			Active a = active.get(e.getUUID());
			if (a == null) return false;
			em = a.emote;
			t = time(e.getUUID(), a);
		}
		float dur = em.seconds;
		// blend in / out over 0.25 s (previews loop without)
		float k = pv != null ? 1f : Math.min(1f, Math.min(t, dur - t) / 0.25f);
		if (k <= 0) return false;
		float s = t * 20f; // ticks
		ModelPart ra = model.rightArm, la = model.leftArm, rl = model.rightLeg, ll = model.leftLeg, head = model.head, body = model.body;
		ModelPart root = model.root();
		switch (em.id) {
			case "wave" -> {
				set(ra, -3.0f, 0, 0.55f + 0.4f * (float) Math.sin(s * 0.55f), k);
			}
			case "dance" -> {
				float b = (float) Math.sin(s * 0.45f);
				set(ra, -1.6f - 1.0f * b, 0, 0.9f, k);
				set(la, -1.6f + 1.0f * b, 0, -0.9f, k);
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
			case "shrug" -> { // "no idea": arms out, palms up, head tilted
				float b = (float) Math.abs(Math.sin(Math.min(1f, t / 0.8f) * Math.PI / 2));
				set(ra, -0.55f * b, 0.35f, 0.55f * b, k);
				set(la, -0.55f * b, -0.35f, -0.55f * b, k);
				set(head, 0, 0, 0.25f * b, k);
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
			case "football" -> { // keepy-uppy: the right foot comes up to meet the ball and kicks it up again
				float u = (s / 14f) % 1f;
				float d = Math.min(u, 1 - u); // distance to the kick
				float kick = Math.max(0, 1 - d / 0.24f);
				kick = kick * kick * (3 - 2 * kick);
				set(rl, -0.85f * kick, 0, 0, k);
				set(ll, 0.08f * kick, 0, 0, k);
				set(ra, 0.15f * kick, 0, 0.45f, k);
				set(la, -0.15f * kick, 0, -0.45f, k);
				set(head, 0.5f, 0, 0, k);
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
				set(la, -2.95f, 0.5f, -0.75f, k);
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
			case "sit" -> { // cross-legged on the ground
				set(rl, -1.45f, 0.35f, 0, k);
				set(ll, -1.45f, -0.35f, 0, k);
				set(ra, -0.65f, 0.1f, 0, k);
				set(la, -0.65f, -0.1f, 0, k);
				root.y += 10f * k;
				set(head, 0.08f * (float) Math.sin(s * 0.08f), 0.2f * (float) Math.sin(s * 0.05f), 0, k);
			}
			case "faint" -> { // falls over backwards and lies there
				float a = (float) (Math.min(1f, t / 0.7f) * Math.PI / 2) * k;
				lie(root, -a);
				set(ra, 0, 0, 0.6f, k);
				set(la, 0, 0, -0.6f, k);
			}
			case "superman" -> { // flying, arms forward
				float a = (float) (Math.PI / 2) * k;
				lie(root, a);
				root.y -= (6f + 2f * (float) Math.sin(s * 0.2f)) * k;
				set(ra, -3.05f, 0, 0.1f, k);
				set(la, -3.05f, 0, -0.1f, k);
				set(head, -1.2f, 0, 0, k);
			}
			case "ascend" -> { // rises up slowly, arms open
				root.y -= 14f * Math.min(1f, t / 3f) * k;
				set(ra, 0, 0, 1.2f, k);
				set(la, 0, 0, -1.2f, k);
				set(head, -0.45f, 0, 0, k);
				set(rl, 0.1f, 0, 0.05f, k);
				set(ll, 0.1f, 0, -0.05f, k);
			}
			case "spin" -> { // ballet spin
				root.yRot += (float) (t / dur * Math.PI * 4) * k;
				set(ra, -3.0f, 0, -0.3f, k);
				set(la, -3.0f, 0, 0.3f, k);
				set(ll, -0.5f, 0, -0.35f, k);
			}
			case "yellow_card", "red_card" -> { // referee: card up high
				float up = Math.min(1f, t / 0.4f);
				set(ra, -0.25f * up, 0, 2.95f * up, k); // raised over the side, never forward
				set(head, -0.15f, 0, 0, k);
				set(la, 0, 0, -0.2f, k);
			}
			case "worship" -> { // kneeling and bowing again and again
				float b = (float) Math.abs(Math.sin(s * 0.18f));
				set(rl, 1.4f, 0, 0, k);
				set(ll, 1.4f, 0, 0, k);
				root.y += 9f * k;
				set(body, 0.7f * b, 0, 0, k);
				set(head, 0.7f * b, 0, 0, k);
				set(ra, -2.6f + 1.2f * b, 0, 0.2f, k);
				set(la, -2.6f + 1.2f * b, 0, -0.2f, k);
			}
			case "six_seven" -> { // both hands up, palms up, going up and down in turns
				float b = (float) Math.sin(s * 0.55f);
				set(ra, -1.25f + 0.45f * b, 0, 0, k);
				set(la, -1.25f - 0.45f * b, 0, 0, k);
				set(head, 0, 0.25f * b, 0, k);
			}
			case "vibe" -> { // stepping from side to side
				float b = (float) Math.sin(s * 0.3f);
				root.x += 2.2f * b * k;
				set(body, 0, 0, 0.1f * b, k);
				set(head, 0.12f * (float) Math.abs(Math.sin(s * 0.6f)), 0, -0.15f * b, k);
				set(ra, -0.4f - 0.3f * b, 0, 0.35f, k);
				set(la, -0.4f + 0.3f * b, 0, -0.35f, k);
				set(rl, 0, 0, 0.2f * Math.max(0, b), k);
				set(ll, 0, 0, -0.2f * Math.max(0, -b), k);
			}
			default -> {
			}
		}
		return true;
	}

	/**
	 * Safety: a straight arm pointing forward and up (with the arm not out to the side) looks like a forbidden
	 * salute, also for a moment while an emote blends in. Such arms are always moved out to the side.
	 */
	private static void guard(ModelPart arm, float side) {
		if (arm.xRot > -2.8f && arm.xRot < -1.55f && Math.abs(arm.yRot) < 0.6f && side * arm.zRot < 0.8f) arm.zRot = side * 0.8f;
	}

	/** Tilts the whole player by {@code angle} around the feet (lying on the back / front), feet stay on the ground. */
	private static void lie(ModelPart root, float angle) {
		root.xRot += angle;
		root.y += 24f * (1 - (float) Math.cos(angle));
		root.z -= 24f * (float) Math.sin(angle);
	}

	/** Emotes that move or tilt the whole body (the vanilla cape can't follow those). */
	private static final java.util.Set<String> WHOLE_BODY = java.util.Set.of("sit", "faint", "superman", "ascend", "spin", "worship", "vibe", "bow");

	/** During an emote the cape hangs straight (no flapping); when the whole body moves, the cape is hidden. */
	public static void fixCape(AvatarRenderState state, UUID uuid) {
		Emote e = preview(state.id);
		if (e == null) {
			Active a = uuid == null ? null : active.get(uuid);
			e = a == null ? null : a.emote;
		}
		if (e == null) return;
		state.capeFlap = 0;
		state.capeLean = 0;
		state.capeLean2 = 0;
		if (WHOLE_BODY.contains(e.id)) state.showCape = false;
	}

	/** Prop held in the right hand: "card_yellow" / "card_red", or null. */
	public static String prop(int stateId, UUID uuid) {
		Emote e = preview(stateId);
		if (e == null) {
			Active a = uuid == null ? null : active.get(uuid);
			e = a == null ? null : a.emote;
		}
		if (e == null) return null;
		return e.id.equals("yellow_card") ? "card_yellow" : e.id.equals("red_card") ? "card_red" : null;
	}



	/**
	 * Ball of a football / basketball emote in model space of this player (x, y down, z forward = negative),
	 * {type 0 = football, 1 = basketball}, or null.
	 */
	public static float[] ball(int stateId, UUID uuid) {
		Emote e = preview(stateId);
		float t;
		if (e != null) t = previewTime(e);
		else {
			Active a = uuid == null ? null : active.get(uuid);
			if (a == null) return null;
			e = a.emote;
			t = time(uuid, a);
		}
		float s = t * 20f;
		if (e.id.equals("football")) { // touches the foot at the kick (u = 0), flies up and comes back down
			float u = (s / 14f) % 1f;
			float h = 4 * u * (1 - u);
			return new float[]{-0.12f, 1.0f - 0.95f * h, -0.56f, 0};
		}
		if (e.id.equals("basketball")) {
			if (t < 1.6f) {
				float d = (float) Math.abs(Math.sin(Math.PI * s / 8));
				return new float[]{-0.45f, 1.25f - 0.55f * d, -0.42f, 1};
			}
			float tt = t - 1.6f;
			if (tt < 0.35f) return new float[]{0, 0.5f - 1.25f * (tt / 0.35f), -0.4f, 1};
			float f = tt - 0.35f;
			if (f > 1.6f) return null;
			return new float[]{0, -0.75f - 3.2f * f + 2.4f * f * f, -0.4f - 3.0f * f, 1};
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
