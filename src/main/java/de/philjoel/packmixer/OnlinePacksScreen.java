package de.philjoel.packmixer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Search texture packs on Modrinth and download them with one click. */
public class OnlinePacksScreen extends Screen {
	private record Preset(String key, String query, String sort) {
	}

	private static final List<Preset> PRESETS = List.of(
			new Preset("packmixer.online.popular", "", "downloads"),
			new Preset("packmixer.online.pvp", "pvp", "downloads"),
			new Preset("packmixer.online.christmas", "christmas", "downloads"),
			new Preset("packmixer.online.winter", "winter snow", "downloads"),
			new Preset("packmixer.online.dark", "dark", "downloads"),
			new Preset("packmixer.online.new", "", "newest"));

	private static String lastQuery = "christmas";
	private static String lastSort = "downloads";
	private static List<Modrinth.Hit> lastResults = new ArrayList<>();

	private final Screen parent;
	private EditBox search;
	private List<Modrinth.Hit> results = lastResults;
	private final Map<String, String> state = new HashMap<>(); // id -> "loading" / "done" / "error"
	private String status = "";
	private boolean loading;
	private int offset;
	private int maxOffset;
	private int px0, py0, px1, py1, listY0, rowH = 34, backW = 44;

	public OnlinePacksScreen(Screen parent) {
		super(Component.translatable("packmixer.online"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		GuiFit.fit(this);
		int maxW = Math.min(this.width - 12, 620);
		px0 = (this.width - maxW) / 2;
		px1 = px0 + maxW;
		py0 = 18;
		py1 = this.height - 6;

		backW = this.font.width(Component.literal("< ").append(Component.translatable("packmixer.settings.back"))) + 10;
		addRenderableWidget(new Ui.TextButton(px0 + 10, py0 + 4, backW, 12,
				Component.literal("< ").append(Component.translatable("packmixer.settings.back")), false, Ui.TEXT, this::onClose));

		// search row
		int y = py0 + 26;
		int searchW = Math.min(260, maxW - 140);
		search = new EditBox(this.font, px0 + 8, y, searchW, 18, Component.translatable("packmixer.online.search"));
		search.setMaxLength(64);
		search.setValue(lastQuery);
		search.setHint(Component.translatable("packmixer.online.search").withStyle(ChatFormatting.GRAY));
		search.setResponder(v -> lastQuery = v);
		addRenderableWidget(search);
		addRenderableWidget(Ui.glass(Component.translatable("packmixer.online.search_button"), b -> runSearch(search.getValue(), "relevance"))
				.bounds(px0 + 12 + searchW, y - 1, 70, 20).accent(Ui.ACCENT).build());

		// preset chips
		int cx = px0 + 8;
		int cy = y + 24;
		for (Preset p : PRESETS) {
			Component label = Component.translatable(p.key);
			int w = this.font.width(label) + 14;
			if (cx + w > px1 - 8) break;
			boolean on = p.query.equals(lastQuery) && p.sort.equals(lastSort);
			addRenderableWidget(new Ui.TextButton(cx, cy, w, 16, label, on, Ui.TEXT, () -> {
				lastQuery = p.query;
				runSearch(p.query, p.sort);
			}));
			cx += w + 4;
		}

		// result rows
		listY0 = cy + 24;
		int listY1 = py1 - 18;
		int visible = Math.max(1, (listY1 - listY0) / rowH);
		maxOffset = Math.max(0, results.size() - visible);
		offset = Math.max(0, Math.min(offset, maxOffset));
		for (int i = 0; i < visible && offset + i < results.size(); i++) {
			Modrinth.Hit hit = results.get(offset + i);
			int ry = listY0 + i * rowH;
			String st = state.get(hit.id());
			Component label = "done".equals(st) ? Component.literal("✔ ").append(Component.translatable("packmixer.online.installed"))
					: "loading".equals(st) ? Component.translatable("packmixer.online.loading")
					: "error".equals(st) ? Component.translatable("packmixer.online.retry")
					: Component.literal("⬇ ").append(Component.translatable("packmixer.online.download"));
			Ui.GlassButton b = Ui.glass(label, btn -> download(hit)).bounds(px1 - 8 - 90, ry + 7, 90, 20)
					.tooltip(Tooltip.create(Component.literal(hit.description())))
					.build();
			b.active = !"loading".equals(st) && !"done".equals(st);
			addRenderableWidget(b);
		}

		if (results.isEmpty() && !loading && status.isEmpty()) pendingSearch = true;
	}

	private boolean pendingSearch;

	@Override
	public void tick() {
		if (pendingSearch) {
			pendingSearch = false;
			runSearch(lastQuery, lastSort);
		}
	}

	private void runSearch(String query, String sort) {
		lastSort = sort;
		loading = true;
		status = Component.translatable("packmixer.online.searching").getString();
		rebuildWidgets();
		Modrinth.search(query, sort, 30).whenComplete((hits, err) -> this.minecraft.execute(() -> {
			loading = false;
			if (err != null) {
				status = Component.translatable("packmixer.online.error").getString();
				PackMixerClient.LOGGER.warn("Modrinth search failed", err);
			} else {
				results = hits;
				lastResults = hits;
				offset = 0;
				status = Component.translatable("packmixer.online.found", hits.size()).getString();
			}
			rebuildWidgets();
		}));
	}

	private void download(Modrinth.Hit hit) {
		state.put(hit.id(), "loading");
		rebuildWidgets();
		Modrinth.download(hit).whenComplete((path, err) -> this.minecraft.execute(() -> {
			if (err != null) {
				state.put(hit.id(), "error");
				status = Component.translatable("packmixer.online.download_error", hit.title()).getString();
				PackMixerClient.LOGGER.warn("Download failed", err);
			} else {
				state.put(hit.id(), "done");
				status = Component.translatable("packmixer.online.download_done", hit.title()).getString();
			}
			rebuildWidgets();
		}));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 && search != null && search.isFocused()) { // Enter
			runSearch(search.getValue(), "relevance");
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && mouseY >= listY0) {
			offset += scrollY > 0 ? -1 : 1;
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private final long openedAt = System.currentTimeMillis();

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float in = Ui.intro(openedAt);
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - in) * 6);
		renderContent(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	private void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Ui.window(g, px0, py0, px1, py1);
		Ui.headerBar(g, px0, py0, px1);
		int visible = Math.max(1, (py1 - 18 - listY0) / rowH);
		for (int i = 0; i < visible && offset + i < results.size(); i++) {
			int ry = listY0 + i * rowH;
			g.fill(px0 + 8, ry, px1 - 8, ry + rowH - 4, Ui.CARD);
		}

		super.extractRenderState(g, mouseX, mouseY, delta);

		Ui.header(g, this.width, Component.translatable("packmixer.online").getString());
		Ui.brand(g, px0 + 18 + backW, py0 + 6, Component.translatable("packmixer.online").getString());
		String src = "modrinth.com";
		Ui.small(g, src, px1 - 10 - Ui.smallWidth(src), py0 + 7, Ui.FAINT);

		int textW = px1 - 8 - 96 - (px0 + 14);
		for (int i = 0; i < visible && offset + i < results.size(); i++) {
			Modrinth.Hit hit = results.get(offset + i);
			int ry = listY0 + i * rowH;
			g.text(this.font, Component.literal(this.font.plainSubstrByWidth(hit.title(), textW)).withStyle(ChatFormatting.BOLD),
					px0 + 14, ry + 4, Ui.TEXT);
			String meta = Component.translatable("packmixer.online.meta", hit.author(), formatCount(hit.downloads()), hit.lastVersion()).getString();
			g.text(this.font, this.font.plainSubstrByWidth(meta, textW), px0 + 14, ry + 16, Ui.DIM);
		}
		if (maxOffset > 0) {
			int h = py1 - 18 - listY0;
			int thumb = Math.max(12, h / (maxOffset + 1));
			int ty = listY0 + (h - thumb) * offset / maxOffset;
			g.fill(px1 - 5, listY0, px1 - 3, listY0 + h, 0x30FFFFFF);
			g.fill(px1 - 5, ty, px1 - 3, ty + thumb, 0xC0FFFFFF);
		}
		g.text(this.font, status, px0 + 8, py1 - 13, loading ? Ui.DIM : Ui.GOOD);
		String note = Component.translatable("packmixer.online.note").getString();
		g.text(this.font, note, px1 - 8 - this.font.width(note), py1 - 13, 0x80FFFFFF);
	}

	private static String formatCount(long n) {
		if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
		if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
		return Long.toString(n);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		GuiFit.restore();
	}
}
