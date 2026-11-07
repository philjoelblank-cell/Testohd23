package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackMixerClient implements ClientModInitializer {
	public static final String MOD_ID = "testohd_styles";
	public static final Logger LOGGER = LoggerFactory.getLogger("Testohd Styles");
	public static final String CREDIT = "Made by Testohd23";

	private static KeyMapping openKey;

	public static KeyMapping openKey() {
		return openKey;
	}

	@Override
	public void onInitializeClient() {
		PackMixerConfig.ensureDirs();
		PackMixerConfig.loadCategories(); // writes categories.json on first start

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.packmixer.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));
		Features.zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.packmixer.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, category));

		Cosmetics.init();

		// create the built-in "Made by Testohd23" packs once the game has loaded its textures
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Updater.checkAsync());
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> ChangelogScreen.maybeShow(screen));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> BuiltinPacks.generateIfNeeded(() -> {
			// new pack versions were written: re-apply your saved choices so nothing you picked gets lost
			java.util.Map<String, String> sel = PackMixerConfig.loadSelection();
			String full = sel.get(DefaultCategories.FULL_PACK);
			if (V1Packs.isV1(full)) { // a whole Testohd pack was chosen: categories it didn't have yet (new crystal, anchor ...) get it too
				boolean changed = false;
				for (Category c : PackMixerConfig.loadCategories()) if (!sel.containsKey(c.id)) {
					sel.put(c.id, full);
					changed = true;
				}
				if (changed) PackMixerConfig.saveSelection(sel);
			}
			if (!sel.isEmpty() || PackMixerConfig.settings().ancientDebris) PackBuilder.rebuildAndApply();
		}));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Features.tick(client);
			CosmeticEffects.tick(client);
			Updater.tick(client);
			CosmeticSync.tick(client);
			AutoTest.tick(client);
			CrystalSound.tick(client);
			CrystalAnim.tick(client);
			SkyboxRenderer.tick(client);
			while (openKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new PackMixerScreen(null));
				}
			}
		});
	}
}
