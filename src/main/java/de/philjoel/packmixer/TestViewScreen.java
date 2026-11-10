package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Auto test only: your player from fixed angles (to check wings / capes / emotes). */
class TestViewScreen extends Screen {
	private final float[] yaws;

	TestViewScreen(float... yaws) {
		super(Component.literal("test view"));
		this.yaws = yaws;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, this.width, this.height, 0xFF5A7FA8);
		Minecraft mc = this.minecraft;
		if (mc.player == null) return;
		int n = yaws.length, cw = this.width / n;
		for (int i = 0; i < n; i++) {
			int x0 = i * cw, x1 = x0 + cw, y0 = 10, y1 = this.height - 10;
			try {
				EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1.0f);
				state.lightCoords = 15728880;
				state.outlineColor = 0;
				if (state instanceof LivingEntityRenderState ls) {
					ls.bodyRot = 180f;
					ls.yRot = 0f;
					ls.xRot = 0f;
				}
				int size = (int) Math.min(cw * 0.42f, (y1 - y0) * 0.36f);
				Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-6))
						.rotateY((float) Math.toRadians(yaws[i]));
				Vector3f translate = new Vector3f(0, state.boundingBoxHeight / 2.0F + 0.0625F, 0);
				for (java.lang.reflect.Method m : g.getClass().getMethods()) {
					Class<?>[] p = m.getParameterTypes();
					if (p.length == 9 && p[0].isAssignableFrom(state.getClass()) && p[2] == Vector3f.class && p[3] == Quaternionf.class) {
						m.invoke(g, state, (float) size, translate, rot, null, x0, y0, x1, y1);
						break;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		// badge check: your name with the "T" in front
		g.pose().pushMatrix();
		g.pose().translate(10, 10);
		g.pose().scale(3f, 3f);
		g.text(this.font, Badge.prefix(mc.player.getName()), 0, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
