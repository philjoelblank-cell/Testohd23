package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/** Settings (layout as in the reference video): list on the left, details on the right. */
public class PackMixerSettingsScreen extends Screen {
	/** A row in the list. toggle == null -> no switch; value == null -> no value text. */
	private record Entry(String id, String section, ItemStack icon, BooleanSupplier toggle, Consumer<Boolean> setToggle,
			Supplier<String> value) {
	}

	static String lastSelected = "item_position";
	private static boolean lastSoundsTab = false;

	private final Screen parent;
	private final List<Entry> settingsEntries = new ArrayList<>();
	private final List<Entry> soundEntries = new ArrayList<>();
	private boolean soundsTab = lastSoundsTab;
	private String selected = lastSelected;
	private int listOffset, maxListOffset;
	private KeyMapping listening;
	private Ui.GlassButton listeningButton;
	private final long openedAt = System.currentTimeMillis();
	private long savedAt;

	private int x0, y0, x1, y1, listX1, detailX0, boxY, boxY1, backW;
	private final List<int[]> sectionRows = new ArrayList<>();
	private final List<String> sectionTitles = new ArrayList<>();
	private final List<Object[]> rowDecor = new ArrayList<>(); // {Entry, y}

	public PackMixerSettingsScreen(Screen parent) {
		super(Component.translatable("packmixer.settings.title"));
		this.parent = parent;
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		var o = net.minecraft.client.Minecraft.getInstance().options;
		settingsEntries.add(new Entry("item_position", "items", new ItemStack(Items.ARMOR_STAND), null, null,
				() -> tr(s.itemPositionEnabled ? "packmixer.changed" : "packmixer.default")));
		settingsEntries.add(new Entry("glint", "items", new ItemStack(Items.ENCHANTED_BOOK), null, null,
				() -> Math.round(o.glintStrength().get() * 100) + "%"));
		settingsEntries.add(new Entry("zoom", "screen", new ItemStack(Items.SPYGLASS), () -> s.zoomEnabled, v -> s.zoomEnabled = v, null));
		settingsEntries.add(new Entry("fullbright", "screen", new ItemStack(Items.TORCH), () -> s.fullbright, v -> s.fullbright = v, null));
		settingsEntries.add(new Entry("weather", "screen", new ItemStack(Items.CLOCK), () -> s.clearWeather, v -> s.clearWeather = v, null));
		settingsEntries.add(new Entry("particles", "screen", new ItemStack(Items.BLAZE_POWDER), null, null,
				() -> o.particles().get().caption().getString()));
		settingsEntries.add(new Entry("low_fire", "screen", new ItemStack(Items.FIRE_CHARGE), () -> s.lowFire, v -> s.lowFire = v, null));
		settingsEntries.add(new Entry("ancient_debris", "screen", new ItemStack(Items.ANCIENT_DEBRIS), () -> s.ancientDebris, v -> {
			s.ancientDebris = v;
			PackMixerConfig.saveSettings();
			PackBuilder.rebuildAndApply();
		}, null));
		settingsEntries.add(new Entry("tt", "screen", new ItemStack(Items.TOTEM_OF_UNDYING), () -> s.totemTracker, v -> s.totemTracker = v, null));
		settingsEntries.add(new Entry("language", "menu", new ItemStack(Items.BOOK), null, null,
				() -> net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected()));
		settingsEntries.add(new Entry("controls", "menu", new ItemStack(Items.COMPASS), null, null,
				() -> PackMixerClient.openKey().getTranslatedKeyMessage().getString()));
		settingsEntries.add(new Entry("packmixer", "menu", new ItemStack(Items.PAINTING), null, null, () -> ""));
		settingsEntries.add(new Entry("profiles", "menu", new ItemStack(Items.CHEST), null, null, () -> {
			int n = 0;
			for (int i = 1; i <= Profiles.COUNT; i++) if (Profiles.exists(i)) n++;
			return n + " / " + Profiles.COUNT;
		}));
		for (SoundSource src : SoundSource.values()) {
			soundEntries.add(new Entry("sound." + src.getName(), "sounds", new ItemStack(Items.NOTE_BLOCK), null, null,
					() -> Math.round(o.getSoundSourceOptionInstance(src).get() * 100) + "%"));
		}
		if (entries().stream().noneMatch(e -> e.id.equals(selected))) selected = entries().get(0).id;
	}

	private List<Entry> entries() {
		return soundsTab ? soundEntries : settingsEntries;
	}

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private Component title(Entry e) {
		if (e.id.startsWith("sound.")) return Component.translatable("soundCategory." + e.id.substring(6));
		return Component.translatable("packmixer.feature." + e.id);
	}

	@Override
	protected void init() {
		GuiFit.fit(this);
		int w = Math.min(this.width - 16, Math.max(380, (int) (this.width * 0.66)));
		int h = Math.min(this.height - 26, Math.max(220, (int) (this.height * 0.7)));
		x0 = (this.width - w) / 2;
		x1 = x0 + w;
		y0 = Math.max(18, (this.height - h) / 2);
		y1 = y0 + h;
		listX1 = x0 + Math.max(130, w * 2 / 5);
		detailX0 = listX1 + 8;
		sectionRows.clear();
		sectionTitles.clear();
		rowDecor.clear();

		// header: < Back
		backW = this.font.width(Component.literal("< ").append(Component.translatable("packmixer.settings.back"))) + 10;
		addRenderableWidget(new Ui.TextButton(x0 + 10, y0 + 4, backW, 12, Component.literal("< ").append(Component.translatable("packmixer.settings.back")),
				false, Ui.TEXT, this::onClose));

		// tabs
		int tabY = y0 + 24;
		int tabW = (listX1 - x0 - 14) / 2;
		addRenderableWidget(tab(x0 + 8, tabY, tabW, tr("packmixer.tab.settings"), settingsEntries.size(), !soundsTab, () -> switchTab(false)));
		addRenderableWidget(tab(x0 + 10 + tabW, tabY, tabW, tr("packmixer.tab.sounds"), soundEntries.size(), soundsTab, () -> switchTab(true)));

		// list
		int rowH = 15;
		int listY0 = tabY + 17;
		int listY1 = y1 - 18;
		List<Object> rows = new ArrayList<>();
		String section = null;
		for (Entry e : entries()) {
			if (!soundsTab && !e.section.equals(section)) {
				section = e.section;
				rows.add(section);
			}
			rows.add(e);
		}
		int visible = Math.max(1, (listY1 - listY0) / rowH);
		maxListOffset = Math.max(0, rows.size() - visible);
		listOffset = Math.max(0, Math.min(listOffset, maxListOffset));
		for (int i = 0; i < visible && listOffset + i < rows.size(); i++) {
			Object row = rows.get(listOffset + i);
			int y = listY0 + i * rowH;
			if (row instanceof String sec) {
				long count = entries().stream().filter(en -> en.section.equals(sec)).count();
				sectionRows.add(new int[]{y, (int) count});
				sectionTitles.add(tr("packmixer.section." + sec));
				continue;
			}
			Entry e = (Entry) row;
			addRenderableWidget(new Ui.TextButton(x0 + 8, y, listX1 - x0 - 12, rowH - 1, Component.literal("     ").append(title(e)),
					e.id.equals(selected), Ui.TEXT, () -> {
				selected = e.id;
				lastSelected = e.id;
				listening = null;
				rebuildWidgets();
			}));
			rowDecor.add(new Object[]{e, y});
		}

		// details (right)
		int x = detailX0 + 8;
		int cw = Math.min(250, x1 - 12 - x);
		boxY = y0 + 52;
		int y = boxY + 16;
		Entry e = entries().stream().filter(en -> en.id.equals(selected)).findFirst().orElse(entries().get(0));
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		var options = this.minecraft.options;

		// "Reset" in the box header
		addRenderableWidget(new Ui.TextButton(x1 - 12 - 30, boxY + 2, 28, 11, Component.translatable("packmixer.reset_short"), false, Ui.FAINT,
				() -> { resetEntry(e); saved(); rebuildWidgets(); }));

		if (e.toggle != null) {
			addRenderableWidget(toggleButton(x, y, cw, e));
			y += 18;
		}
		if (e.id.startsWith("sound.")) {
			SoundSource src = SoundSource.valueOf(SoundSource.values()[soundEntries.indexOf(e)].name());
			OptionInstance<Double> opt = options.getSoundSourceOptionInstance(src);
			addRenderableWidget(slider(x, y, cw, title(e).getString(), opt.get(), 0, 1, opt::set, v -> Math.round(v * 100) + "%"));
			y += 18;
		}
		switch (e.id) {
			case "item_position" -> {
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.enabled", () -> s.itemPositionEnabled, v -> s.itemPositionEnabled = v)); y += 18;
				addRenderableWidget(slider(x, y, cw, "X", s.itemX, -1, 1, v -> s.itemX = v, v -> String.format("%.2f", v))); y += 18;
				addRenderableWidget(slider(x, y, cw, "Y", s.itemY, -1, 1, v -> s.itemY = v, v -> String.format("%.2f", v))); y += 18;
				addRenderableWidget(slider(x, y, cw, "Z", s.itemZ, -1, 1, v -> s.itemZ = v, v -> String.format("%.2f", v))); y += 18;
				addRenderableWidget(slider(x, y, cw, tr("packmixer.size"), s.itemScale, 0.3, 1.5, v -> s.itemScale = v, v -> Math.round(v * 100) + "%")); y += 18;
			}
			case "glint" -> {
				addRenderableWidget(slider(x, y, cw, tr("packmixer.glint_strength"), options.glintStrength().get(), 0, 1,
						v -> options.glintStrength().set(v), v -> Math.round(v * 100) + "%")); y += 18;
				addRenderableWidget(slider(x, y, cw, tr("packmixer.glint_speed"), options.glintSpeed().get(), 0, 1,
						v -> options.glintSpeed().set(v), v -> Math.round(v * 100) + "%")); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.glint_gear", () -> s.glintOnlyGear, v -> s.glintOnlyGear = v)); y += 18;
			}
			case "zoom" -> {
				addRenderableWidget(slider(x, y, cw, tr("packmixer.zoom_strength"), s.zoomFactor, 1.5, 10, v -> s.zoomFactor = v,
						v -> String.format("%.1fx", v))); y += 18;
				addRenderableWidget(keyButton(x, y, cw, Features.zoomKey())); y += 18;
			}
			case "particles" -> {
				addRenderableWidget(Ui.glass(particlesLabel(), b -> {
					net.minecraft.server.level.ParticleStatus[] all = net.minecraft.server.level.ParticleStatus.values();
					options.particles().set(all[(options.particles().get().ordinal() + 1) % all.length]);
					saved();
					b.setMessage(particlesLabel());
				}).bounds(x, y, cw, 14).build()); y += 18;
			}
			case "low_fire" -> {
				addRenderableWidget(slider(x, y, cw, tr("packmixer.fire_height"), s.fireOffset, 0, 0.5, v -> s.fireOffset = v,
						v -> Math.round(v / 0.5 * 100) + "%")); y += 18;
			}
			case "tt" -> {
				addRenderableWidget(Ui.glass(Component.translatable("packmixer.tt.reset"), b -> { TotemTracker.reset(); saved(); })
						.bounds(x, y, cw, 14).build()); y += 18;
			}
			case "language" -> {
				addRenderableWidget(Ui.glass(Component.translatable("packmixer.language.open"), b -> this.minecraft.gui.setScreen(
						new net.minecraft.client.gui.screens.options.LanguageSelectScreen(this, options, this.minecraft.getLanguageManager())))
						.bounds(x, y, cw, 14).build()); y += 18;
			}
			case "controls" -> {
				addRenderableWidget(keyButton(x, y, cw, PackMixerClient.openKey())); y += 18;
				addRenderableWidget(keyButton(x, y, cw, Features.zoomKey())); y += 18;
			}
			case "packmixer" -> {
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.resourcepacks", () -> s.includeResourcePacksFolder,
						v -> s.includeResourcePacksFolder = v)); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.chat", () -> s.chatMessageOnApply, v -> s.chatMessageOnApply = v)); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.snow", () -> s.menuSnow, v -> s.menuSnow = v)); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.crystal_silent", () -> s.crystalSilent, v -> s.crystalSilent = v)); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.autoupdate", () -> s.autoUpdate, v -> s.autoUpdate = v)); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.share_cosmetics", () -> s.shareCosmetics, v -> s.shareCosmetics = v)); y += 18;
				addRenderableWidget(simpleToggle(x, y, cw, "packmixer.settings.show_cosmetics", () -> s.showOthersCosmetics, v -> s.showOthersCosmetics = v)); y += 18;
				addRenderableWidget(Ui.glass(Component.translatable("packmixer.builtin.regenerate"), b -> {
					b.active = false;
					s.builtinPacksVersion = 0;
					BuiltinPacks.generateIfNeeded(() -> { b.active = true; saved(); PackBuilder.rebuildAndApply(); });
				}).bounds(x, y, cw, 14).build()); y += 18;
			}
			case "profiles" -> {
				int bw = (cw - 4) / 2;
				for (int slot = 1; slot <= Profiles.COUNT; slot++) {
					final int sl = slot;
					addRenderableWidget(Ui.glass(Component.translatable("packmixer.profile.save", sl), b -> { Profiles.save(sl); saved(); rebuildWidgets(); })
							.bounds(x, y, bw, 14).build());
					Ui.GlassButton load = Ui.glass(Component.translatable("packmixer.profile.load", sl), b -> { Profiles.load(sl); saved(); })
							.bounds(x + bw + 4, y, bw, 14).build();
					load.active = Profiles.exists(sl);
					addRenderableWidget(load);
					y += 18;
				}
			}
			default -> {
			}
		}
		boxY1 = y + 2;

		// footer: Save
		int saveW = 58;
		addRenderableWidget(Ui.glass(Component.translatable("packmixer.save"), b -> onClose()).bounds(x1 - 12 - saveW, y1 - 17, saveW, 13).light().build());
	}

	private AbstractWidget tab(int x, int y, int w, String label, int count, boolean on, Runnable action) {
		return new Ui.FlatButton(x, y, w, 13, Component.literal(label), action) {
			@Override
			protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
				if (on) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x55FFFFFF);
				else if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
				int tw = font.width(label) + 4 + Ui.smallWidth(String.valueOf(count)) + 4;
				int tx = getX() + (getWidth() - tw) / 2;
				g.text(font, label, tx, getY() + 3, on ? Ui.TEXT : Ui.DIM, false);
				int bx = tx + font.width(label) + 4;
				g.fill(bx, getY() + 3, bx + Ui.smallWidth(String.valueOf(count)) + 4, getY() + 10, 0x40FFFFFF);
				Ui.small(g, String.valueOf(count), bx + 2, getY() + 4, Ui.DIM);
			}
		};
	}

	private void switchTab(boolean sounds) {
		soundsTab = sounds;
		lastSoundsTab = sounds;
		listOffset = 0;
		selected = entries().get(0).id;
		lastSelected = selected;
		rebuildWidgets();
	}

	private void resetEntry(Entry e) {
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		var o = this.minecraft.options;
		switch (e.id) {
			case "item_position" -> { s.itemPositionEnabled = false; s.itemX = 0; s.itemY = 0; s.itemZ = 0; s.itemScale = 1; }
			case "glint" -> { o.glintStrength().set(0.75); o.glintSpeed().set(0.5); s.glintOnlyGear = true; }
			case "zoom" -> { s.zoomEnabled = true; s.zoomFactor = 4; }
			case "fullbright" -> s.fullbright = false;
			case "weather" -> s.clearWeather = false;
			case "particles" -> o.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
			case "low_fire" -> { s.lowFire = false; s.fireOffset = 0.3; }
			case "ancient_debris" -> { s.ancientDebris = false; PackBuilder.rebuildAndApply(); }
			case "tt" -> { s.totemTracker = true; TotemTracker.reset(); }
			case "controls" -> {
				PackMixerClient.openKey().setKey(PackMixerClient.openKey().getDefaultKey());
				Features.zoomKey().setKey(Features.zoomKey().getDefaultKey());
				KeyMapping.resetMapping();
			}
			case "packmixer" -> { s.includeResourcePacksFolder = true; s.chatMessageOnApply = true; s.menuSnow = false; s.autoUpdate = true; s.shareCosmetics = true; s.showOthersCosmetics = true; s.crystalSilent = false; }
			default -> {
				if (e.id.startsWith("sound.")) {
					SoundSource src = SoundSource.values()[soundEntries.indexOf(e)];
					o.getSoundSourceOptionInstance(src).set(1.0);
				}
			}
		}
		o.save();
	}

	private void saved() {
		PackMixerConfig.saveSettings();
		savedAt = System.currentTimeMillis();
	}

	private Ui.GlassButton toggleButton(int x, int y, int w, Entry e) {
		return simpleToggle(x, y, w, "packmixer.enabled", e.toggle, e.setToggle);
	}

	private Ui.GlassButton simpleToggle(int x, int y, int w, String key, BooleanSupplier get, Consumer<Boolean> set) {
		return Ui.glass(onOff(Component.translatable(key), get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			saved();
			rebuildWidgets();
		}).bounds(x, y, w, 14).build();
	}

	private static Component onOff(Component label, boolean on) {
		return label.copy().append(": ").append(on
				? Component.translatable("packmixer.on").withStyle(ChatFormatting.GREEN)
				: Component.translatable("packmixer.off").withStyle(ChatFormatting.RED));
	}

	private Ui.GlassButton keyButton(int x, int y, int w, KeyMapping key) {
		Ui.GlassButton[] ref = new Ui.GlassButton[1];
		ref[0] = Ui.glass(keyLabel(key), b -> {
			listening = key;
			listeningButton = ref[0];
			b.setMessage(keyLabel(key));
		}).bounds(x, y, w, 14).build();
		return ref[0];
	}

	private Component particlesLabel() {
		return Component.translatable("packmixer.feature.particles").append(": ").append(this.minecraft.options.particles().get().caption());
	}

	private Component keyLabel(KeyMapping key) {
		Component name = listening == key
				? Component.literal("> ").append(Component.translatable("packmixer.settings.key.press")).append(" <").withStyle(ChatFormatting.YELLOW)
				: key.getTranslatedKeyMessage();
		return Component.translatable(key.getName()).append(": ").append(name);
	}

	/** Slider in the reference style: light filled bar with a handle and the text on top. */
	private AbstractWidget slider(int x, int y, int w, String label, double value, double min, double max,
			DoubleConsumer setter, DoubleFunction<String> format) {
		double norm = (value - min) / (max - min);
		return new AbstractSliderButton(x, y, w, 14, Component.empty(), Math.max(0, Math.min(1, norm))) {
			{
				updateMessage();
			}

			private double real() {
				return min + this.value * (max - min);
			}

			@Override
			public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
				int x = getX(), y = getY(), w = getWidth(), h = getHeight();
				g.fill(x, y, x + w, y + h, 0x30000000);
				int fill = (int) (w * this.value);
				g.fillGradient(x, y, x + fill, y + h, 0xD0E6E4F0, 0xB0C4C1D6);
				g.fill(x + fill - 1, y - 1, x + fill + 2, y + h + 1, 0xFFFFFFFF);
				g.outline(x, y, w, h, isHoveredOrFocused() ? 0x80FFFFFF : 0x40FFFFFF);
				int tw = font.width(getMessage());
				int tx = x + Math.max(4, Math.min(w - tw - 4, fill + 6));
				g.text(font, getMessage(), tx, y + 3, Ui.TEXT, true);
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + format.apply(real())));
			}

			@Override
			protected void applyValue() {
				setter.accept(real());
				saved();
			}
		};
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			if (!event.isEscape()) {
				listening.setKey(InputConstants.getKey(event));
				KeyMapping.resetMapping();
				this.minecraft.options.save();
				saved();
			}
			KeyMapping k = listening;
			listening = null;
			if (listeningButton != null) listeningButton.setMessage(keyLabel(k));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < listX1 && scrollY != 0) {
			listOffset += scrollY > 0 ? -1 : 1;
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
		renderContent(g, mouseX, mouseY, delta);
		g.pose().popMatrix();
	}

	private void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Ui.header(g, this.width, null);
		Ui.window(g, x0, y0, x1, y1);
		Ui.headerBar(g, x0, y0, x1);
		Ui.brand(g, x0 + 18 + backW, y0 + 6, tr("packmixer.settings"));
		if (System.currentTimeMillis() - savedAt < 2500) {
			String t = "✓ " + tr("packmixer.saved");
			Ui.small(g, t, x1 - 10 - Ui.smallWidth(t), y0 + 7, Ui.DIM);
		}
		// player in the background of the detail area (as in the reference)
		if (this.minecraft.player != null) {
			int cx = (detailX0 + x1) / 2;
			int top = Math.max(boxY1 + 6, y0 + (y1 - y0) / 2 - 10);
			int bottom = y1 - 20;
			if (bottom - top > 40) {
				InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - 30, top, cx + 30, bottom,
						Math.max(20, Math.min(45, (bottom - top) / 2 - 4)), 0.0625f, mouseX, mouseY, this.minecraft.player);
			}
		}
		// list separator
		g.fill(listX1 + 2, y0 + 24, listX1 + 3, y1 - 20, Ui.LINE);

		// detail header + box (behind the widgets)
		Entry e = entries().stream().filter(en -> en.id.equals(selected)).findFirst().orElse(entries().get(0));
		int x = detailX0 + 8;
		Ui.item(g, e.icon, x, y0 + 25, 10);
		g.text(this.font, title(e), x + 14, y0 + 24, Ui.TEXT, false);
		String desc = e.id.startsWith("sound.") ? tr("packmixer.sound.desc") : tr("packmixer.feature." + e.id + ".desc");
		Ui.small(g, desc, x + 14, y0 + 34, Ui.FAINT);
		g.fill(x - 4, boxY, x1 - 10, boxY1, 0x38000000);
		Ui.section(g, title(e).getString(), null, x, boxY + 4);
		g.fill(x, boxY + 14, x1 - 14, boxY + 15, Ui.LINE);

		super.extractRenderState(g, mouseX, mouseY, delta);

		// list decorations: icons, values, switches
		for (int i = 0; i < sectionRows.size(); i++) {
			int[] sr = sectionRows.get(i);
			g.fill(x0 + 9, sr[0] + 3, x0 + 10, sr[0] + 10, Ui.TEXT);
			Ui.small(g, sectionTitles.get(i).toUpperCase(), x0 + 13, sr[0] + 4, Ui.TEXT);
			Ui.small(g, String.valueOf(sr[1]), x0 + 15 + Ui.smallWidth(sectionTitles.get(i).toUpperCase()), sr[0] + 4, Ui.FAINT);
		}
		for (Object[] rd : rowDecor) {
			Entry en = (Entry) rd[0];
			int y = (int) rd[1];
			Ui.item(g, en.icon, x0 + 13, y + 2, 10);
			if (en.toggle != null) {
				Ui.drawSwitch(g, listX1 - 22, y + 3, en.toggle.getAsBoolean());
			} else if (en.value != null) {
				String v = en.value.get();
				if (!v.isEmpty()) Ui.small(g, v, listX1 - 8 - Ui.smallWidth(v), y + 4, Ui.FAINT);
			}
		}
		Ui.small(g, tr("packmixer.settings.footer"), x0 + 10, y1 - 13, Ui.FAINT);
	}

	@Override
	public void onClose() {
		PackMixerConfig.saveSettings();
		this.minecraft.options.save();
		this.minecraft.gui.setScreen(parent);
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
