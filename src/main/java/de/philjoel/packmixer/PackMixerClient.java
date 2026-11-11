package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
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
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.packmixer.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		Features.zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.packmixer.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
		Emotes.key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.packmixer.emotes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

		Cosmetics.init();

		// create the built-in "Made by Testohd23" packs once the game has loaded its textures
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Updater.checkAsync());
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> ChangelogScreen.maybeShow(screen));
		if (!AutoTest.ENABLED) ClientLifecycleEvents.CLIENT_STARTED.register(client -> BuiltinPacks.generateIfNeeded(written -> {
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
			// reload the game's textures only if a pack you actually use was written again
			boolean used = false;
			for (String name : sel.values()) if (written.contains(name)) used = true;
			if (used) PackBuilder.rebuildAndApply();
		}));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Features.tick(client);
			CosmeticEffects.tick(client);
			Capes.tick(client);
			Emotes.tick(client);
			Updater.tick(client);
			CosmeticSync.tick(client);
			AutoTest.tick(client);
			CrystalSound.tick(client);
			CrystalAnim.tick(client);
			while (Emotes.key.consumeClick()) {
				if (client.screen == null && client.player != null) client.setScreen(new EmoteWheelScreen());
			}
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new PackMixerScreen(null));
				}
			}
		});
	}
}
