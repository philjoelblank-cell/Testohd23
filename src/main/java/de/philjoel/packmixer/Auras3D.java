package de.philjoel.packmixer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D auras: glowing ribbons (comet trails) swirling around you, and little models flying around you with a trail
 * behind them. Positions are in the player's model space (y down, front = -z, 1 = one block).
 */
final class Auras3D {
	private static final int BRIGHT = 15728880;
	private static final Map<String, ItemStackRenderState> CACHE = new HashMap<>();

	static boolean handles(String id) {
		return id.startsWith("orb_") || id.startsWith("trail_");
	}

	private static ItemStackRenderState model(Minecraft mc, String name) {
		ItemStack st = Ui.stack(Items.PAPER);
		if (st.isEmpty()) return null;
		st.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, name));
		ItemStackRenderState item = new ItemStackRenderState();
		mc.getItemModelResolver().updateForTopItem(item, st, ItemDisplayContext.FIXED, mc.level, null, 0);
		return item.isEmpty() ? null : item;
	}

	/** Path of a ribbon: where its head is at "phase" (radians along the path). */
	private interface Path {
		double[] at(double phase);
	}

	/** One ribbon: n glowing pieces getting smaller and darker towards the tail. */
	private static void ribbon(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel model, Minecraft mc,
			Path path, double head, String[] colors, int n, double step, float size) {
		for (int j = n - 1; j >= 0; j--) {
			double[] p = path.at(head - j * step);
			float f = 1f - j / (float) n;
			String c = colors[Math.min(colors.length - 1, (int) ((1 - f) * colors.length))];
			ItemStackRenderState it = model(mc, "seg_" + c);
			if (it == null) return;
			pose.pushPose();
			model.root().translateAndRotate(pose);
			pose.translate((float) p[0], (float) p[1], (float) p[2]);
			pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(head - j * step) * 2));
			float s = size * (0.25f + 0.75f * (float) Math.pow(f, 0.8));
			pose.scale(s, s, s);
			it.submit(pose, collector, BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
			pose.popPose();
		}
	}

	static void render(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel model, Minecraft mc, String id, float t) {
		switch (id) {
			case "trail_plasma" -> helix(pose, collector, state, model, mc, t, new String[]{"white", "pink", "magenta", "purple", "deep"}, 0.08);
			case "trail_inferno" -> helix(pose, collector, state, model, mc, t, new String[]{"fire1", "fire2", "fire3", "red", "deep"}, 0.1);
			case "trail_frost" -> helix(pose, collector, state, model, mc, t, new String[]{"white", "ice1", "ice2", "ice3", "blue"}, 0.07);
			case "trail_void" -> helix(pose, collector, state, model, mc, t, new String[]{"white", "purple", "deep", "black", "black"}, 0.06);
			case "trail_yinyang" -> { // a white and a black ribbon chasing each other around your waist
				for (int k = 0; k < 2; k++) {
					int kk = k;
					Path p = ph -> new double[]{Math.cos(ph) * 0.95, 0.72 + Math.sin(ph * 2) * 0.12, Math.sin(ph) * 0.95};
					ribbon(pose, collector, state, model, mc, p, t * 0.16 + kk * Math.PI,
							kk == 0 ? new String[]{"white", "white", "ice1", "ice1"} : new String[]{"black", "black", "deep", "deep"}, 26, 0.09, 0.42f);
				}
			}
			case "trail_rainbow" -> { // rainbow ring turning over your head + one around your feet
				String[] rb = {"red", "fire2", "gold", "green", "ice2", "blue", "purple", "pink"};
				for (int k = 0; k < 2; k++) {
					double y = k == 0 ? -0.55 : 1.42, r = k == 0 ? 0.42 : 0.85;
					Path p = ph -> new double[]{Math.cos(ph) * r, y + Math.sin(ph * 3) * 0.04, Math.sin(ph) * r};
					double head = t * (k == 0 ? 0.2 : -0.14);
					int n = 32;
					for (int j = 0; j < n; j++) {
						double[] q = p.at(head - j * 2 * Math.PI / n);
						ItemStackRenderState it = model(mc, "seg_" + rb[j * rb.length / n]);
						if (it == null) return;
						pose.pushPose();
						model.root().translateAndRotate(pose);
						pose.translate((float) q[0], (float) q[1], (float) q[2]);
						float s = k == 0 ? 0.26f : 0.34f;
						pose.scale(s, s, s);
						it.submit(pose, collector, BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
						pose.popPose();
					}
				}
			}
			default -> orbit(pose, collector, state, model, mc, id, t);
		}
	}

	/** Two ribbons spiralling up and down around you (opposite each other). */
	private static void helix(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel model, Minecraft mc, float t,
			String[] colors, double speed) {
		for (int k = 0; k < 2; k++) {
			int kk = k;
			Path p = ph -> {
				double r = 0.78 + 0.08 * Math.sin(ph * 3);
				return new double[]{Math.cos(ph) * r, 0.75 + Math.sin(ph * 0.45 + kk * Math.PI) * 0.85, Math.sin(ph) * r};
			};
			ribbon(pose, collector, state, model, mc, p, t * speed * 2.2 + kk * Math.PI, colors, 30, 0.085, 0.4f);
		}
	}

	/** Little models (ghosts, crystals, wisps, hearts) flying around you, each with a glowing trail; rings for orb_rings. */
	private static void orbit(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel model, Minecraft mc, String id, float t) {
		ItemStackRenderState item = model(mc, switch (id) {
			case "orb_ghosts" -> "orb_ghost";
			case "orb_crystals" -> "orb_crystal";
			case "orb_rings" -> "orb_ring";
			case "orb_wisps" -> "orb_wisp";
			default -> "orb_heart";
		});
		if (item == null) return;
		if (id.equals("orb_rings")) {
			for (int i = 0; i < 3; i++) {
				pose.pushPose();
				model.root().translateAndRotate(pose);
				pose.translate(0.0F, 0.75F + (float) Math.sin(t * 0.05F + i * 2.1) * 0.65F, 0.0F);
				pose.mulPose(Axis.YP.rotationDegrees(t * (i % 2 == 0 ? 3F : -2.4F)));
				pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.04 + i) * 8F));
				float sc = 1.55F + 0.15F * i;
				pose.scale(sc, sc, sc);
				item.submit(pose, collector, BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
				pose.popPose();
			}
			return;
		}
		String[] trail = switch (id) {
			case "orb_ghosts" -> new String[]{"white", "ice1", "ice2", "ice3"};
			case "orb_crystals" -> new String[]{"pink", "magenta", "purple", "deep"};
			case "orb_wisps" -> new String[]{"ice1", "ice2", "blue", "ice3"};
			default -> new String[]{"pink", "red", "magenta", "deep"};
		};
		int n = id.equals("orb_hearts") ? 5 : 4;
		for (int i = 0; i < n; i++) {
			int ii = i;
			double speed = 0.06;
			Path p = ph -> new double[]{Math.cos(ph) * 0.95, 0.6 + Math.sin(ph * 2 + ii) * 0.35, Math.sin(ph) * 0.95};
			double head = t * speed + i * Math.PI * 2 / n;
			ribbon(pose, collector, state, model, mc, p, head - 0.12, trail, 14, 0.07, 0.22f);
			double[] q = p.at(head);
			pose.pushPose();
			model.root().translateAndRotate(pose);
			pose.translate((float) q[0], (float) q[1], (float) q[2]);
			float yaw = id.equals("orb_ghosts") ? (float) Math.toDegrees(-head) : t * 5F + i * 60F;
			pose.mulPose(Axis.YP.rotationDegrees(yaw));
			if (id.equals("orb_crystals")) pose.mulPose(Axis.XP.rotationDegrees(45F + t * 4F));
			pose.mulPose(Axis.XP.rotationDegrees(180F)); // models are upright in item space, the model space has y down
			float sc = id.equals("orb_ghosts") ? 0.8F : 0.65F;
			pose.scale(sc, sc, sc);
			item.submit(pose, collector, BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
			pose.popPose();
		}
	}

	private Auras3D() {
	}
}
