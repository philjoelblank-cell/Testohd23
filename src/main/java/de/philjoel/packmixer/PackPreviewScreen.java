package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Preview before applying: your character turning around (with your wings, crown and effect) and the most
 * important items of the chosen mix, already in the new textures.
 */
public class PackPreviewScreen extends Screen {
	private static final String[] SHOW = {"swords", "end_crystal", "totem", "ender_pearl", "golden_apple", "enchanted_golden_apple", "mace",
			"pickaxes", "respawn_anchor", "spear", "shield", "potions"};

	private final Screen parent;
	private final Map<String, String> selection;
	private final PackLibrary library;
	private final PreviewCache cache;
	private final List<Category> shown = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private int px0, py0, px1, py1;

	PackPreviewScreen(Screen parent, Map<String, String> selection, PackLibrary library, PreviewCache cache) {
		super(Component.translatable("packmixer.preview"));
		this.parent = parent;
		this.selection = selection;
		this.library = library;
		this.cache = cache;
		List<Category> cats = PackMixerConfig.loadCategories();
		for (String id : SHOW)
			for (Category c : cats) if (c.id.equals(id)) shown.add(c);
	}

	@Override
	protected void init() {
		GuiFit.fit(this);
		int w = Math.min(this.width - 12, 460), h = Math.min(this.height - 12, 260);
		px0 = (this.width - w) / 2;
		px1 = px0 + w;
		py0 = (this.height - h) / 2;
		py1 = py0 + h;
		int backW = this.font.width(Component.literal("< ").append(Component.translatable("packmixer.settings.back"))) + 10;
		addRenderableWidget(new Ui.TextButton(px0 + 10, py0 + 4, backW, 12,
				Component.literal("< ").append(Component.translatable("packmixer.settings.back")), false, Ui.TEXT, this::onClose));
		Ui.preloadCosmetics();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float in = Ui.intro(openedAt);
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - in) * 6);
		Ui.window(g, px0, py0, px1, py1);
		Ui.headerBar(g, px0, py0, px1);
		int bw = this.font.width(Component.literal("< ").append(Component.translatable("packmixer.settings.back"))) + 16;
		Ui.brand(g, px0 + 10 + bw, py0 + 6, Component.translatable("packmixer.preview").getString());

		// ---- your character, turning around
		int dollW = Math.min(150, (px1 - px0) / 3);
		int dx0 = px0 + 8, dx1 = dx0 + dollW, dy0 = py0 + 26, dy1 = py1 - 26;
		g.fill(dx0, dy0, dx1, dy1, Ui.CARD);
		doll(g, dx0, dy0, dx1, dy1);
		Ui.small(g, Component.translatable("packmixer.preview.you").getString(), dx0 + 4, dy1 + 4, Ui.DIM);

		// ---- items of the chosen mix
		int gx0 = dx1 + 10, gx1 = px1 - 10, gy0 = py0 + 26, gy1 = py1 - 26;
		int cols = 4, rows = (shown.size() + cols - 1) / cols;
		int cw = (gx1 - gx0) / cols, ch = Math.max(30, (gy1 - gy0) / Math.max(1, rows));
		int size = Math.min(cw, ch) - 14;
		float t = Ui.time();
		for (int i = 0; i < shown.size(); i++) {
			Category c = shown.get(i);
			int cx = gx0 + (i % cols) * cw, cy = gy0 + (i / cols) * ch;
			g.fill(cx + 2, cy + 2, cx + cw - 2, cy + ch - 2, Ui.CARD);
			String packName = selection.get(c.id);
			SourcePack pack = packName == null ? null : library.get(packName);
			Ui.drawPreview(g, cache, c, pack, cx + (cw - size) / 2, cy + 3, size, i * 0.7f, false, true);
			String label = pack == null ? Component.translatable("packmixer.default").getString()
					: Ui.packDisplay(pack.name).replace("Testohd23 ", "").replace("Testohd ", "");
			int lw = Ui.smallWidth(label);
			if (lw > cw - 6) {
				while (label.length() > 1 && Ui.smallWidth(label + "..") > cw - 6) label = label.substring(0, label.length() - 1);
				label += "..";
				lw = Ui.smallWidth(label);
			}
			Ui.small(g, label, cx + (cw - lw) / 2, cy + ch - 10, pack == null ? Ui.DIM : Ui.TEXT);
		}
		Ui.small(g, Component.translatable("packmixer.preview.hint").getString(), gx0, py1 - 18, Ui.DIM);
		super.render(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	/** Your player model turning once around every few seconds (same picture-in-picture as the inventory). */
	private void doll(GuiGraphics g, int x0, int y0, int x1, int y1) {
		Minecraft mc = this.minecraft;
		if (mc.player == null) {
			Ui.small(g, Component.translatable("packmixer.preview.no_world").getString(), x0 + 6, (y0 + y1) / 2, Ui.DIM);
			return;
		}
		try {
			EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1.0f);
			state.lightCoords = 15728880;
			state.outlineColor = 0;
			if (state instanceof LivingEntityRenderState ls) {
				ls.bodyRot = 180f;
				ls.yRot = 0f;
				ls.xRot = 0f;
			}
			float angle = (float) (Ui.time() * 0.9 % (Math.PI * 2));
			int size = (int) Math.min((x1 - x0) * 0.45f, (y1 - y0) * 0.36f);
			Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-8)).rotateY(angle);
			Vector3f translate = new Vector3f(0, state.boundingBoxHeight / 2.0F + 0.0625F, 0);
			g.submitEntityRenderState(state, size, translate, rot, null, x0, y0, x1, y1);
		} catch (Throwable e) {
			Ui.small(g, Component.translatable("packmixer.preview.no_world").getString(), x0 + 6, (y0 + y1) / 2, Ui.DIM);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
