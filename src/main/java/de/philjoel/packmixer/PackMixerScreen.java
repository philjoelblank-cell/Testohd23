package de.philjoel.packmixer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Texture styles menu (layout as in the reference video). */
public class PackMixerScreen extends Screen {
	private static final int ICON = 18;
	private static final int GAP = 4;

	private static int lastCategory = 0;

	private final Screen parent;
	private List<Category> categories;
	private PackLibrary library;
	private final Map<String, String> selection;
	private final PreviewCache previews = new PreviewCache();
	private final long openedAt = System.currentTimeMillis();
	private boolean dirty;

	private int category = lastCategory;
	private int barOffset;
	private int rowOffset;
	private int maxRowOffset;
	private boolean favoritesOnly;
	private boolean menuOpen;
	private String query = "";
	private boolean searchFocused;
	private EditBox search;

	// layout
	private int x0, y0, x1, y1, barY, sectionY, gridY, gridY1, footerY;
	private int optionCount;
	private boolean rescanOnReturn;
	private String status;
	private long statusUntil;
	private final List<Ui.Card> cards = new ArrayList<>();
	private final List<MenuItem> menu = new ArrayList<>();

	private record MenuItem(Component label, Runnable action) {
	}

	public PackMixerScreen(Screen parent) {
		super(Component.translatable("packmixer.title"));
		this.parent = parent;
		this.categories = PackMixerConfig.loadCategories();
		this.library = PackLibrary.scan();
		this.selection = new LinkedHashMap<>(PackMixerConfig.loadSelection());
		this.selection.keySet().removeIf(id -> library.get(selection.get(id)) == null);
		if (category >= categories.size()) category = 0;
	}

	/** A saved mix was chosen in "Meine Packs": take it over (applied with "Done" like any other choice). */
	void useSelection(Map<String, String> mix) {
		selection.clear();
		for (Map.Entry<String, String> e : mix.entrySet()) if (library.get(e.getValue()) != null) selection.put(e.getKey(), e.getValue());
		dirty = true;
	}

	@Override
	protected void init() {
		GuiFit.fit(this);
		if (rescanOnReturn) {
			rescanOnReturn = false;
			rescan();
		}
		cards.clear();
		int w = Math.min(this.width - 16, Math.max(380, (int) (this.width * 0.8)));
		int h = Math.min(this.height - 26, Math.max(220, (int) (this.height * 0.74)));
		x0 = (this.width - w) / 2;
		x1 = x0 + w;
		y0 = Math.max(18, (this.height - h) / 2);
		y1 = y0 + h;

		// ---------------------------------------------------------------- header bar
		addRenderableWidget(new Ui.TextButton(x0 + 10, y0 + 4, 13, 12, Component.literal(" +"), menuOpen, Ui.TEXT, () -> {
			menuOpen = !menuOpen;
			rebuildWidgets();
		}));
		int searchW = 76;
		if (search == null) {
			search = new EditBox(this.font, 0, 0, searchW, 12, Component.translatable("packmixer.search"));
			search.setMaxLength(40);
			search.setBordered(false);
			search.setHint(Component.literal("⌕ ").append(Component.translatable("packmixer.search")).withStyle(ChatFormatting.DARK_GRAY));
			search.setResponder(v -> {
				query = v.toLowerCase(Locale.ROOT);
				searchFocused = true;
				rowOffset = 0;
				rebuildWidgets();
			});
		}
		search.setX(x1 - 8 - searchW);
		search.setY(y0 + 6);
		addRenderableWidget(search);
		if (searchFocused) {
			this.setFocused(search);
			search.setFocused(true);
		}
		int favW = this.font.width("★ " + Component.translatable("packmixer.favorites").getString()) + 10;
		int favX = x1 - 8 - searchW - 6 - favW;
		addRenderableWidget(new Ui.TextButton(favX, y0 + 4, favW, 12,
				Component.literal("★ ").withStyle(ChatFormatting.GOLD).append(Component.translatable("packmixer.favorites").withStyle(ChatFormatting.WHITE)),
				favoritesOnly, Ui.TEXT, () -> {
					favoritesOnly = !favoritesOnly;
					rowOffset = 0;
					rebuildWidgets();
				}));
		int cosW = this.font.width("♟ " + Component.translatable("packmixer.cosmetics").getString()) + 10;
		addRenderableWidget(new Ui.TextButton(favX - 4 - cosW, y0 + 4, cosW, 12,
				Component.literal("♟ ").append(Component.translatable("packmixer.cosmetics")), false, Ui.TEXT,
				() -> this.minecraft.gui.setScreen(new CosmeticsScreen(this))));
		int prevW = this.font.width("◉ " + Component.translatable("packmixer.preview").getString()) + 10;
		int prevX = favX - 4 - cosW - 4 - prevW;
		addRenderableWidget(new Ui.TextButton(prevX, y0 + 4, prevW, 12,
				Component.literal("◉ ").append(Component.translatable("packmixer.preview")), false, Ui.TEXT,
				() -> this.minecraft.gui.setScreen(new PackPreviewScreen(this, selection, library, previews))));
		int myW = this.font.width("☰ " + Component.translatable("packmixer.mypacks").getString()) + 10;
		addRenderableWidget(new Ui.TextButton(prevX - 4 - myW, y0 + 4, myW, 12,
				Component.literal("☰ ").append(Component.translatable("packmixer.mypacks")), false, Ui.TEXT,
				() -> this.minecraft.gui.setScreen(new MyPacksScreen(this, selection, library))));

		// ---------------------------------------------------------------- icon bar + Settings button
		barY = y0 + 25;
		int settingsW = this.font.width(Component.translatable("packmixer.settings")) + 26;
		addRenderableWidget(new Ui.SettingsButton(x1 - 8 - settingsW, barY - 1, settingsW, ICON + 2,
				Component.translatable("packmixer.settings"), () -> {
					rescanOnReturn = true;
					this.minecraft.gui.setScreen(new PackMixerSettingsScreen(this));
				}));
		int barX0 = x0 + 10;
		int barX1 = x1 - 8 - settingsW - 6;
		int visibleIcons = Math.max(1, (barX1 - barX0) / (ICON + 3));
		int maxBar = Math.max(0, categories.size() - visibleIcons);
		if (category < barOffset) barOffset = category;
		if (category >= barOffset + visibleIcons) barOffset = category - visibleIcons + 1;
		barOffset = Math.max(0, Math.min(barOffset, maxBar));
		for (int i = 0; i < visibleIcons && barOffset + i < categories.size(); i++) {
			int index = barOffset + i;
			Category c = categories.get(index);
			int count = library.packsFor(c).size();
			String chosen = selection.get(c.id);
			Ui.IconButton b = new Ui.IconButton(barX0 + i * (ICON + 3), barY, ICON, previews, c, index == category, chosen != null,
					() -> selectCategory(index));
			b.setTooltip(Tooltip.create(Component.literal(c.displayName() + " (" + count + ")  -  ")
					.append(chosen == null ? Component.translatable("packmixer.default") : Component.literal(Ui.packDisplay(chosen)))));
			addRenderableWidget(b);
		}

		// ---------------------------------------------------------------- section row (<  >)
		sectionY = barY + ICON + 9;
		addRenderableWidget(new Ui.TextButton(x1 - 30, sectionY - 2, 10, 11, Component.literal("<"), false, Ui.TEXT,
				() -> selectCategory(Math.floorMod(category - 1, Math.max(1, categories.size())))));
		addRenderableWidget(new Ui.TextButton(x1 - 18, sectionY - 2, 10, 11, Component.literal(">"), false, Ui.TEXT,
				() -> selectCategory(Math.floorMod(category + 1, Math.max(1, categories.size())))));

		// ---------------------------------------------------------------- cards
		gridY = sectionY + 13;
		footerY = y1 - 18;
		gridY1 = footerY - 4;
		int gx0 = x0 + 10;
		int gw = x1 - 14 - gx0;
		int cols = Math.max(3, Math.min(9, (gw + GAP) / (46 + GAP)));
		int cardW = (gw - (cols - 1) * GAP) / cols;
		int cardH = cardW + 8;
		int rows = Math.max(1, (gridY1 - gridY + GAP) / (cardH + GAP));
		// use the free space: cards grow a little instead of leaving an empty band at the bottom
		int fill = (gridY1 - gridY - (rows - 1) * GAP) / rows;
		if (fill > cardH) cardH = Math.min(fill, cardW + 22);

		Category cat = categories.isEmpty() ? null : categories.get(category);
		List<SourcePack> options = new ArrayList<>();
		if (!favoritesOnly && query.isEmpty()) options.add(null); // "Default"
		if (cat != null) {
			for (SourcePack p : library.packsFor(cat)) {
				if (favoritesOnly && !isFavorite(p)) continue;
				if (!query.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(query)) continue;
				options.add(p);
			}
		}
		optionCount = options.size();
		int totalRows = (options.size() + cols - 1) / cols;
		maxRowOffset = Math.max(0, totalRows - rows);
		rowOffset = Math.max(0, Math.min(rowOffset, maxRowOffset));

		if (cat != null && !menuOpen) {
			String current = selection.get(cat.id);
			for (int i = 0; i < rows * cols; i++) {
				int idx = rowOffset * cols + i;
				if (idx >= options.size()) break;
				SourcePack pack = options.get(idx);
				int x = gx0 + (i % cols) * (cardW + GAP);
				int y = gridY + (i / cols) * (cardH + GAP);
				boolean sel = pack == null ? current == null : pack.name.equals(current);
				String label = pack == null ? Component.translatable("packmixer.default").getString() : pack.name;
				Ui.Card card = new Ui.Card(x, y, cardW, cardH, previews, cat, pack, label, sel, pack != null && isFavorite(pack), () -> {
					if (pack == null) selection.remove(cat.id);
					else selection.put(cat.id, pack.name);
					if (pack != null && cat.id.equals(DefaultCategories.FULL_PACK)) { // whole pack: every category
						// (the Testohd packs get every category, even if the menu was opened while they were still being created)
						for (Category c : categories) if (V1Packs.isV1(pack.name) || pack.contains(c)) selection.put(c.id, pack.name);
					}
					dirty = true;
					rebuildWidgets();
				});
				int files = pack == null ? 0 : pack.matching(cat).size();
				card.setTooltip(Tooltip.create(pack == null
						? Component.translatable("packmixer.default.tooltip")
						: Component.literal(Ui.packDisplay(pack.name) + "\n")
						.append(Component.translatable("packmixer.files", files).withStyle(ChatFormatting.GRAY))
						.append(Component.literal("\n")).append(Component.translatable("packmixer.fav_hint").withStyle(ChatFormatting.DARK_GRAY))));
				addRenderableWidget(card);
				cards.add(card);
			}
		}

		// ---------------------------------------------------------------- footer: Done
		int doneW = 58;
		addRenderableWidget(Ui.glass(Component.translatable("packmixer.apply"), b -> onClose())
				.bounds(x1 - 12 - doneW, footerY - 1, doneW, 13).light().build());

		// ---------------------------------------------------------------- "+" menu (drawn on top in render)
		menu.clear();
		if (menuOpen) {
			menu.add(new MenuItem(Component.translatable("packmixer.menu.add"), () -> {
				List<Path> chosen = PackImporter.chooseZipFiles(Component.translatable("packmixer.add.dialog").getString());
				if (!chosen.isEmpty()) importAndRefresh(chosen);
			}));
			menu.add(new MenuItem(Component.translatable("packmixer.menu.online"), () -> {
				rescanOnReturn = true;
				this.minecraft.gui.setScreen(new OnlinePacksScreen(this));
			}));
			menu.add(new MenuItem(Component.translatable("packmixer.menu.folder"), () -> com.mojang.blaze3d.Blaze3D.openPath(PackMixerConfig.packsDir())));
			menu.add(new MenuItem(Component.translatable("packmixer.menu.reload"), () -> {
				rescan();
				setStatus(Component.translatable("packmixer.pack_count", library.size()).getString());
			}));
			menu.add(new MenuItem(Component.translatable("packmixer.menu.reset"), () -> {
				if (!selection.isEmpty()) {
					selection.clear();
					dirty = true;
				}
			}));
		}
	}

	/** Used by the automatic screenshot test. */
	void testSelect(int index) {
		selectCategory(index);
	}

	int categoryCount() {
		return categories.size();
	}

	private void selectCategory(int index) {
		category = index;
		lastCategory = index;
		rowOffset = 0;
		rebuildWidgets();
	}

	private boolean isFavorite(SourcePack p) {
		return PackMixerConfig.settings().favorites.contains(p.name);
	}

	private void rescan() {
		previews.clear();
		categories = PackMixerConfig.loadCategories();
		library = PackLibrary.scan();
		if (category >= categories.size()) category = 0;
	}

	private void setStatus(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 5000;
	}

	private void importAndRefresh(List<Path> paths) {
		PackImporter.Result r = PackImporter.importPacks(paths);
		rescan();
		String msg = Component.translatable("packmixer.add.done", r.added()).getString();
		if (r.skipped() > 0) msg += " " + Component.translatable("packmixer.add.skipped", r.skipped()).getString();
		setStatus(msg);
		rebuildWidgets();
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		importAndRefresh(paths);
	}

	private int menuX() { return x0 + 10; }
	private int menuY() { return y0 + 18; }
	private int menuW() {
		int w = 0;
		for (MenuItem m : menu) w = Math.max(w, this.font.width(m.label()));
		return w + 14;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (menuOpen && !menu.isEmpty()) {
			int mx = menuX(), my = menuY(), mw = menuW();
			for (int i = 0; i < menu.size(); i++) {
				int iy = my + 3 + i * 12;
				if (event.x() >= mx && event.x() < mx + mw && event.y() >= iy && event.y() < iy + 12) {
					menuOpen = false;
					menu.get(i).action().run();
					if (this.minecraft.gui.screen() == this) rebuildWidgets();
					return true;
				}
			}
			boolean onPlus = event.x() >= x0 + 10 && event.x() < x0 + 23 && event.y() >= y0 + 4 && event.y() < y0 + 16;
			if (!onPlus) {
				menuOpen = false;
				rebuildWidgets();
				return true;
			}
		}
		if (event.button() == 1) { // right-click a card = favourite
			for (Ui.Card c : cards) {
				if (c.pack() != null && event.x() >= c.getX() && event.x() < c.getX() + c.getWidth()
						&& event.y() >= c.getY() && event.y() < c.getY() + c.getHeight()) {
					List<String> favs = PackMixerConfig.settings().favorites;
					if (!favs.remove(c.pack().name)) favs.add(c.pack().name);
					PackMixerConfig.saveSettings();
					rebuildWidgets();
					return true;
				}
			}
		}
		searchFocused = false;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int dir = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
		if (dir == 0) return false;
		if (mouseY >= barY && mouseY < barY + ICON) barOffset += dir;
		else rowOffset += dir;
		rebuildWidgets();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float in = Ui.intro(openedAt);
		g.pose().pushMatrix();
		g.pose().translate(0, (1 - in) * 6);
		renderContent(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	private boolean waitedForPacks;

	private void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// the Testohd packs are still being created (first start after an update): show it, reload them when done
		if (BuiltinPacks.generating) {
			waitedForPacks = true;
			setStatus(Component.translatable("packmixer.generating").getString());
		} else if (waitedForPacks) {
			waitedForPacks = false;
			rescan();
			setStatus(Component.translatable("packmixer.generated").getString());
			rebuildWidgets();
		}
		Ui.header(g, this.width, null);
		Ui.window(g, x0, y0, x1, y1);
		Ui.headerBar(g, x0, y0, x1);
		Ui.brand(g, x0 + 28, y0 + 6, Component.translatable("packmixer.subtitle.textures").getString());
		g.fill(x1 - 8 - 76, y0 + 15, x1 - 8, y0 + 16, Ui.LINE); // search underline

		super.extractRenderState(g, mouseX, mouseY, delta);

		Category cat = categories.isEmpty() ? null : categories.get(category);
		if (cat != null) {
			int styles = optionCount - (favoritesOnly || !query.isEmpty() ? 0 : 1);
			Ui.section(g, cat.displayName(), Component.translatable("packmixer.styles", styles).getString(), x0 + 10, sectionY);
			String current = selection.get(cat.id);
			String active = Component.translatable("packmixer.active").getString() + " "
					+ (current == null ? Component.translatable("packmixer.default").getString() : Ui.packDisplay(current));
			Ui.small(g, active, x1 - 36 - Ui.smallWidth(active), sectionY + 1, Ui.FAINT);
			if (styles == 0 && !menuOpen) {
				g.text(this.font, Component.translatable(favoritesOnly ? "packmixer.no_favorites" : "packmixer.empty_hint"),
						x0 + 10, cards.isEmpty() ? gridY + 4 : gridY1 - 12, Ui.DIM, false);
			}
		}

		if (maxRowOffset > 0) {
			int x = x1 - 7;
			int h = gridY1 - gridY;
			int thumb = Math.max(14, h / (maxRowOffset + 1));
			int ty = gridY + (h - thumb) * rowOffset / maxRowOffset;
			g.fill(x, gridY, x + 1, gridY1, 0x30FFFFFF);
			g.fill(x - 1, ty, x + 2, ty + thumb, 0xC0FFFFFF);
		}

		boolean showStatus = status != null && System.currentTimeMillis() < statusUntil;
		String foot = showStatus ? status : Component.translatable("packmixer.footer").getString();
		Ui.small(g, foot, x0 + 10, footerY + 3, showStatus ? Ui.GOOD : Ui.FAINT);

		if (menuOpen && !menu.isEmpty()) {
			int mx = menuX(), my = menuY(), mw = menuW(), mh = menu.size() * 12 + 6;
			g.fill(mx, my, mx + mw, my + mh, 0xF0141020);
			g.outline(mx, my, mw, mh, 0x60FFFFFF);
			for (int i = 0; i < menu.size(); i++) {
				int iy = my + 3 + i * 12;
				boolean hover = mouseX >= mx && mouseX < mx + mw && mouseY >= iy && mouseY < iy + 12;
				if (hover) g.fill(mx + 1, iy, mx + mw - 1, iy + 12, 0x30FFFFFF);
				g.text(this.font, menu.get(i).label(), mx + 7, iy + 2, hover ? Ui.TEXT : Ui.DIM, false);
			}
		}
	}

	@Override
	public void onClose() {
		if (dirty) {
			PackMixerConfig.saveSelection(selection);
			try {
				int files = PackBuilder.build(categories, selection, library);
				PackMixerClient.LOGGER.info("Testohd Styles: {} files written", files);
			} catch (Exception e) {
				PackMixerClient.LOGGER.error("Testohd Styles: building the pack failed", e);
			}
			this.minecraft.gui.setScreen(parent);
			PackBuilder.applyInGame(!selection.isEmpty());
			dirty = false;
			if (PackMixerConfig.settings().chatMessageOnApply && this.minecraft.player != null) {
				this.minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("[Testohd Styles] ").withStyle(ChatFormatting.LIGHT_PURPLE)
						.append(Component.translatable("packmixer.applied", selection.size()).withStyle(ChatFormatting.WHITE)));
			}
		} else {
			this.minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public void removed() {
		GuiFit.restore();
		previews.clear();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
