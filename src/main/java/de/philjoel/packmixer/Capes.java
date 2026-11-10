package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.HashSet;
import java.util.Set;

/** Animated Testohd capes (also used as elytra texture). */
public final class Capes {
	private static final Set<String> loaded = new HashSet<>();

	public static void apply(Entity e, AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		String id = Cosmetics.of(e.getUUID()).cape();
		if (id == null || id.isEmpty() || !loaded.contains(id)) return;
		Identifier tex = Cosmetics.capeTexture(id, mc.level.getGameTime());
		ClientAsset.ResourceTexture asset = new ClientAsset.ResourceTexture(tex, tex);
		PlayerSkin s = state.skin;
		state.skin = new PlayerSkin(s.body(), asset, asset, s.model(), s.secure());
		state.showCape = true;
	}

	/** Loads the frames of every cape that is being worn (outside of rendering). */
	static void tick(Minecraft mc) {
		if (mc.level == null) return;
		Set<String> wanted = new HashSet<>();
		for (var p : mc.level.players()) {
			String id = Cosmetics.of(p.getUUID()).cape();
			if (id != null && !id.isEmpty()) wanted.add(id);
		}
		for (String id : wanted) {
			if (loaded.contains(id)) continue;
			try {
				for (int f = 0; f < Cosmetics.CAPE_FRAMES; f++) mc.getTextureManager().getTexture(Cosmetics.capeTexture(id, f));
				loaded.add(id);
			} catch (Exception ex) {
				PackMixerClient.LOGGER.warn("Cape {} could not be loaded", id, ex);
				loaded.add(id);
			}
		}
	}

	private Capes() {
	}
}
