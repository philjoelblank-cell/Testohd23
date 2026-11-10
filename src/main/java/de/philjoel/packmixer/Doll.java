package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Your player as a small doll in a menu (emote wheel previews). */
final class Doll {
	private static boolean failedLogged;

	/**
	 * @param stateId render state id (Emotes preview ids make the doll play that emote), 0 = keep
	 * @return false if it could not be drawn
	 */
	static boolean draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int stateId, float yawDeg, float scale) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		try {
			EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1.0f);
			state.lightCoords = 15728880;
			state.outlineColor = 0;
			state.nameTag = null;
			if (stateId != 0 && state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState av) {
				av.id = stateId;
				Emotes.fixCape(av, null);
			}
			if (state instanceof LivingEntityRenderState ls) {
				ls.bodyRot = 180f;
				ls.yRot = 0f;
				ls.xRot = 0f;
			}
			int size = (int) (Math.min((x1 - x0) * 0.45f, (y1 - y0) * 0.36f) * scale);
			Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-6))
					.rotateY((float) Math.toRadians(yawDeg));
			Vector3f translate = new Vector3f(0, state.boundingBoxHeight / 2.0F + 0.0625F, 0);
			g.entity(state, (float) size, translate, rot, null, x0, y0, x1, y1);
			return true;
		} catch (Throwable e) {
			if (!failedLogged) {
				failedLogged = true;
				PackMixerClient.LOGGER.warn("Doll could not be drawn", e);
			}
			return false;
		}
	}

	private Doll() {
	}
}
