package de.philjoel.packmixer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/** "Meine Packs": save the current mix under a name, use / delete saved ones, share them with a code. */
public class MyPacksScreen extends Screen {
	private final PackMixerScreen parent;
	private final Map<String, String> current;
	private final PackLibrary library;
	private final long openedAt = System.currentTimeMillis();
	private EditBox nameBox, codeBox;
	private String status = "";
	private int statusColor = Ui.DIM;
	private int px0, py0, px1, py1, listY, rowH = 22, offset;

	MyPacksScreen(PackMixerScreen parent, Map<String, String> current, PackLibrary library) {
		super(Component.translatable("packmixer.mypacks"));
		this.parent = parent;
		this.current = current;
		this.library = library;
	}

	private void status(Component text, int color) {
		status = text.getString();
		statusColor = color;
	}

	@Override
	protected void init() {
		GuiFit.fit(this);
		int w = Math.min(this.width - 12, 420);
		px0 = (this.width - w) / 2;
		px1 = px0 + w;
		py0 = Math.max(10, (this.height - 300) / 2);
		py1 = Math.min(this.height - 6, py0 + 300);
		int backW = this.font.width(Component.literal("< ").append(Component.translatable("packmixer.settings.back"))) + 10;
		addRenderableWidget(new Ui.TextButton(px0 + 10, py0 + 4, backW, 12,
				Component.literal("< ").append(Component.translatable("packmixer.settings.back")), false, Ui.TEXT, this::onClose));

		// ---- save the current mix
		int y = py0 + 40;
		String oldName = nameBox == null ? "" : nameBox.getValue();
		nameBox = new EditBox(this.font, px0 + 12, y, px1 - px0 - 24 - 104, 16, Component.translatable("packmixer.mypacks.name"));
		nameBox.setMaxLength(32);
		nameBox.setHint(Component.translatable("packmixer.mypacks.name").withStyle(ChatFormatting.DARK_GRAY));
		nameBox.setValue(oldName);
		addRenderableWidget(nameBox);
		addRenderableWidget(Ui.glass(Component.translatable("packmixer.mypacks.save"), b -> saveCurrent())
				.bounds(px1 - 12 - 100, y - 1, 100, 18).light().build());

		// ---- import a code
		y += 40;
		String oldCode = codeBox == null ? "" : codeBox.getValue();
		codeBox = new EditBox(this.font, px0 + 12, y, px1 - px0 - 24 - 104, 16, Component.translatable("packmixer.mypacks.code"));
		codeBox.setMaxLength(4000);
		codeBox.setHint(Component.translatable("packmixer.mypacks.code").withStyle(ChatFormatting.DARK_GRAY));
		codeBox.setValue(oldCode);
		addRenderableWidget(codeBox);
		addRenderableWidget(Ui.glass(Component.translatable("packmixer.mypacks.import"), b -> importCode())
				.bounds(px1 - 12 - 100, y - 1, 100, 18).build());

		// ---- saved mixes
		listY = y + 40;
		List<Mixes.Mix> all = Mixes.all();
		int visible = Math.max(1, (py1 - 22 - listY) / rowH);
		offset = Math.max(0, Math.min(offset, all.size() - visible));
		for (int i = 0; i < visible && offset + i < all.size(); i++) {
			Mixes.Mix m = all.get(offset + i);
			int ry = listY + i * rowH;
			int bx = px1 - 12;
			bx -= 22;
			addRenderableWidget(Ui.glass(Component.literal("✕"), b -> {
				Mixes.remove(m);
				status(Component.translatable("packmixer.mypacks.deleted", m.name), Ui.DIM);
				rebuildWidgets();
			}).bounds(bx, ry + 2, 20, 16).build());
			bx -= 64;
			addRenderableWidget(Ui.glass(Component.translatable("packmixer.mypacks.copy"), b -> {
				this.minecraft.keyboardHandler.setClipboard(Mixes.code(m));
				status(Component.translatable("packmixer.mypacks.copied", m.name), 0xFF7CFFB2);
			}).bounds(bx, ry + 2, 62, 16).build());
			bx -= 64;
			addRenderableWidget(Ui.glass(Component.translatable("packmixer.mypacks.use"), b -> use(m))
					.bounds(bx, ry + 2, 62, 16).light().build());
		}
	}

	private void saveCurrent() {
		String name = nameBox.getValue().trim();
		if (name.isEmpty()) {
			status(Component.translatable("packmixer.mypacks.need_name"), 0xFFFF7A7A);
			return;
		}
		if (current.isEmpty()) {
			status(Component.translatable("packmixer.mypacks.empty"), 0xFFFF7A7A);
			return;
		}
		Mixes.add(new Mixes.Mix(name, current));
		nameBox.setValue("");
		offset = 0;
		status(Component.translatable("packmixer.mypacks.saved", name), 0xFF7CFFB2);
		rebuildWidgets();
	}

	private void importCode() {
		String text = codeBox.getValue().isBlank() ? this.minecraft.keyboardHandler.getClipboard() : codeBox.getValue();
		Mixes.Mix m = Mixes.decode(text);
		if (m == null) {
			status(Component.translatable("packmixer.mypacks.bad_code"), 0xFFFF7A7A);
			return;
		}
		Mixes.add(m);
		codeBox.setValue("");
		offset = 0;
		List<String> miss = Mixes.missing(m, library);
		if (miss.isEmpty()) status(Component.translatable("packmixer.mypacks.imported", m.name), 0xFF7CFFB2);
		else status(Component.translatable("packmixer.mypacks.missing", m.name, String.join(", ", miss)), 0xFFFFC23A);
		rebuildWidgets();
	}

	private void use(Mixes.Mix m) {
		parent.useSelection(m.selection);
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= listY) {
			offset = Math.max(0, offset - (int) Math.signum(scrollY));
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float in = Ui.intro(openedAt);
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - in) * 6);
		Ui.window(g, px0, py0, px1, py1);
		Ui.headerBar(g, px0, py0, px1);
		int bw = this.font.width(Component.literal("< ").append(Component.translatable("packmixer.settings.back"))) + 16;
		Ui.brand(g, px0 + 10 + bw, py0 + 6, Component.translatable("packmixer.mypacks").getString());
		Ui.section(g, Component.translatable("packmixer.mypacks.save_title").getString(), "", px0 + 12, py0 + 28);
		Ui.section(g, Component.translatable("packmixer.mypacks.import_title").getString(), "", px0 + 12, py0 + 68);
		List<Mixes.Mix> all = Mixes.all();
		Ui.section(g, Component.translatable("packmixer.mypacks.saved_title").getString(), String.valueOf(all.size()), px0 + 12, listY - 12);
		int visible = Math.max(1, (py1 - 22 - listY) / rowH);
		if (all.isEmpty()) Ui.small(g, Component.translatable("packmixer.mypacks.none").getString(), px0 + 14, listY + 6, Ui.DIM);
		for (int i = 0; i < visible && offset + i < all.size(); i++) {
			Mixes.Mix m = all.get(offset + i);
			int ry = listY + i * rowH;
			g.fill(px0 + 10, ry, px1 - 10, ry + rowH - 2, Ui.CARD);
			int textW = px1 - px0 - 24 - 3 * 64 - 22;
			g.text(this.font, this.font.plainSubstrByWidth(m.name, textW), px0 + 16, ry + 3, Ui.TEXT, false);
			long miss = Mixes.missing(m, library).size();
			String info = Component.translatable("packmixer.mypacks.count", m.selection.size()).getString()
					+ (miss > 0 ? " · " + Component.translatable("packmixer.mypacks.missing_short", miss).getString() : "");
			Ui.small(g, info, px0 + 16, ry + 13, miss > 0 ? 0xFFFFC23A : Ui.DIM);
		}
		if (!status.isEmpty()) Ui.small(g, status, px0 + 12, py1 - 14, statusColor);
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
