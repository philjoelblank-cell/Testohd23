package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** The little Testohd "T" in front of the names of players who use the mod (name tag and tab list). */
public final class Badge {
	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "badge"));

	public static boolean has(UUID uuid) {
		if (uuid == null || !PackMixerConfig.settings().showBadges) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getUUID().equals(uuid)) return true;
		return CosmeticSync.isUser(uuid);
	}

	public static Component prefix(Component name) {
		if (name == null) return null;
		if (name.getString().startsWith("")) return name;
		MutableComponent b = Component.literal("").withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF));
		return Component.empty().append(b).append(Component.literal(" ")).append(name);
	}

	private Badge() {
	}
}
