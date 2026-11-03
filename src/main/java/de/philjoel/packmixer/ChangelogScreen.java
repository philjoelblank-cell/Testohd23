package de.philjoel.packmixer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Shown once after an (auto-)update: what is new in this version ("packmixer.changelog.<version>" in the lang files). */
public final class ChangelogScreen extends Screen {
	private final Screen parent;
	private final String version;
	private final List<String> lines = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private int px0, py0, px1, py1;

	ChangelogScreen(Screen parent, String version) {
		super(Component.translatable("packmixer.changelog.title", version));
		this.parent = parent;
		this.version = version;
		String key = "packmixer.changelog." + version;
		if (I18n.exists(key)) for (String l : I18n.get(key).split("\n")) lines.add(l);
	}

	static String modVersion() {
		return FabricLoader.getInstance().getModContainer(PackMixerClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	private static boolean checked;

	/** Called when the title screen opens: shows the changelog once per new version. */
	static void maybeShow(Screen screen) {
		if (checked || !(screen instanceof TitleScreen) || AutoTest.ENABLED) return;
		checked = true;
		String v = modVersion();
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		if (v.isEmpty() || v.equals(s.lastChangelog)) return;
		s.lastChangelog = v;
		PackMixerConfig.saveSettings();
		if (!I18n.exists("packmixer.changelog." + v)) return;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ChangelogScreen(screen, v)));
	}

	@Override
	protected void init() {
		int w = Math.min(320, this.width - 20);
		int h = Math.min(40 + lines.size() * 12 + 34, this.height - 20);
		px0 = (this.width - w) / 2;
		px1 = px0 + w;
		py0 = (this.height - h) / 2;
		py1 = py0 + h;
		addRenderableWidget(Ui.glass(Component.translatable("packmixer.changelog.ok"), b -> onClose())
				.bounds((this.width - 80) / 2, py1 - 26, 80, 18).light().build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float in = Ui.intro(openedAt);
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - in) * 6);
		Ui.window(g, px0, py0, px1, py1);
		Ui.headerBar(g, px0, py0, px1);
		Ui.brand(g, px0 + 10, py0 + 6, Component.translatable("packmixer.changelog.title", version).getString());
		int y = py0 + 28;
		for (String l : lines) {
			if (y > py1 - 34) break;
			boolean head = !l.startsWith("•");
			g.text(this.font, l, px0 + 14, y, head ? 0xFFFFD86B : 0xFFE6E6F0);
			y += 12;
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
